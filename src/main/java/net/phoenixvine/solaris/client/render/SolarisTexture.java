package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.CaveColorSampler;
import net.phoenixvine.solaris.client.color.ChunkColorCache;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A square viewport texture built from {@link ChunkColorCache}, centered on a chunk and
 * spanning {@code radiusChunks} chunks in every direction ({@code (2r+1)*16} pixels wide).
 * Rebuilt only when the center chunk actually changes ({@link #maybeRebuild}) or an overlay
 * calls {@link #invalidateAll()} — never touches world/chunk data itself, purely reads
 * whatever's already cached, then composites registered {@link SolarisOverlay}s on top.
 *
 * Chunks not yet in the cache render as a flat "fog" color rather than being skipped, so
 * unexplored area is visually distinct from black.
 */
public class SolarisTexture implements AutoCloseable {

    private static final List<SolarisTexture> ACTIVE = new CopyOnWriteArrayList<>();

    /** How many chunks around the player get live cave-sliced instead of surface-cached, while underground. */
    private static final int CAVE_REVEAL_RADIUS = 3;
    /** Re-rebuild while underground if the player's Y moved at least this much, even in the same chunk. */
    private static final int CAVE_Y_REBUILD_THRESHOLD = 3;

    private final ResourceLocation textureId;
    private final int radiusChunks;
    private final int sizePixels;
    private final NativeImage image;
    private final DynamicTexture texture;

    private ChunkKey lastCenter;
    private int lastPlayerY = Integer.MIN_VALUE;
    private boolean lastUnderground = false;

    public SolarisTexture(String name, int radiusChunks) {
        this.radiusChunks = radiusChunks;
        int spanChunks = radiusChunks * 2 + 1;
        this.sizePixels = spanChunks * 16;

        this.textureId = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/" + name);
        this.image = new NativeImage(sizePixels, sizePixels, false);
        this.texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(textureId, texture);

        ACTIVE.add(this);
    }

    public ResourceLocation textureId() {
        return textureId;
    }

    public int getSizePixels() {
        return sizePixels;
    }

    public int getRadiusChunks() {
        return radiusChunks;
    }

    /**
     * Rebuilds if {@code center} differs from the last build, or — while underground — if the
     * player has moved enough vertically that the revealed cave slice would look stale. Safe
     * to call every frame.
     */
    public void maybeRebuild(ChunkKey center) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        int playerY = player != null ? Mth.floor(player.getY()) : 0;
        boolean underground = player != null && level != null &&
                CaveColorSampler.isUnderground(level, player.blockPosition());

        boolean centerChanged = !center.equals(lastCenter);
        boolean caveStale = underground &&
                (!lastUnderground || Math.abs(playerY - lastPlayerY) >= CAVE_Y_REBUILD_THRESHOLD);
        boolean surfacedAgain = !underground && lastUnderground;

        if (!centerChanged && !caveStale && !surfacedAgain) return;

        lastPlayerY = playerY;
        lastUnderground = underground;
        rebuild(center);
    }

    public void rebuild(ChunkKey center) {
        lastCenter = center;
        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        boolean underground = player != null && level != null &&
                CaveColorSampler.isUnderground(level, player.blockPosition());
        int playerY = player != null ? Mth.floor(player.getY()) : 0;

        // Theme-driven "fog" for chunks not yet in the cache, or open cave air within the
        // revealed slice — ARGB from the theme, converted to NativeImage's ABGR pixel format
        // like every other pixel here.
        int fogArgb = SolarisThemeUtils.C_FAINT;
        int fogColor = FastColor.ABGR32.color(FastColor.ARGB32.alpha(fogArgb), FastColor.ARGB32.blue(fogArgb),
                FastColor.ARGB32.green(fogArgb), FastColor.ARGB32.red(fogArgb));
        // Pure black for anything outside the reveal radius while underground — you can't see
        // the surface from a cave, so the last-cached surface color mustn't show through there;
        // this is what actually reads as "underground now" instead of a cave patch pasted onto
        // an otherwise-normal surface map.
        int hiddenColor = FastColor.ABGR32.color(255, 0, 0, 0);

        double saturation = SolarisConfig.SATURATION.get();

        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;
                ChunkKey key = new ChunkKey(center.dimension(), chunkX, chunkZ);
                int baseX = (dx + radiusChunks) * 16;
                int baseZ = (dz + radiusChunks) * 16;

                boolean caveReveal = underground && level != null &&
                        Math.max(Math.abs(dx), Math.abs(dz)) <= CAVE_REVEAL_RADIUS;

                int[] pixels;
                int unknownColor;
                if (underground) {
                    pixels = caveReveal ? CaveColorSampler.sample(level, key.toChunkPos(), playerY) : null;
                    unknownColor = caveReveal ? fogColor : hiddenColor;
                } else {
                    pixels = ChunkColorCache.get(key);
                    unknownColor = fogColor;
                }

                int tint = 0;
                boolean hasTint = false;
                for (SolarisOverlay overlay : overlays) {
                    Optional<Integer> color = overlay.colorAt(center.dimension(), chunkX, chunkZ);
                    if (color.isPresent()) {
                        tint = color.get();
                        hasTint = true;
                        // later (higher-priority) overlays keep overwriting — last one wins
                    }
                }

                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int color = pixels != null ? pixels[lz * 16 + lx] : 0;
                        if (color == 0) {
                            color = unknownColor; // no data / open cave air / hidden
                        } else if (saturation != 1.0) {
                            color = applySaturation(color, saturation);
                        }
                        if (hasTint) color = blend(color, tint);
                        image.setPixelRGBA(baseX + lx, baseZ + lz, color);
                    }
                }
            }
        }

        texture.upload();
    }

    /** Alpha-blends an ARGB overlay color over an existing ABGR-packed base pixel. */
    private static int blend(int baseAbgr, int overlayArgb) {
        int a = FastColor.ARGB32.alpha(overlayArgb);
        if (a <= 0) return baseAbgr;

        int ovR = FastColor.ARGB32.red(overlayArgb);
        int ovG = FastColor.ARGB32.green(overlayArgb);
        int ovB = FastColor.ARGB32.blue(overlayArgb);
        if (a >= 255) return FastColor.ABGR32.color(255, ovB, ovG, ovR);

        int baseR = FastColor.ABGR32.red(baseAbgr);
        int baseG = FastColor.ABGR32.green(baseAbgr);
        int baseB = FastColor.ABGR32.blue(baseAbgr);
        int r = (ovR * a + baseR * (255 - a)) / 255;
        int g = (ovG * a + baseG * (255 - a)) / 255;
        int b = (ovB * a + baseB * (255 - a)) / 255;
        return FastColor.ABGR32.color(255, b, g, r);
    }

    /** Scales an already-ABGR-packed pixel's saturation, leaving hue/brightness/alpha alone. */
    private static int applySaturation(int abgr, double saturation) {
        int a = FastColor.ABGR32.alpha(abgr);
        int r = FastColor.ABGR32.red(abgr);
        int g = FastColor.ABGR32.green(abgr);
        int b = FastColor.ABGR32.blue(abgr);

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float newSat = (float) Mth.clamp(hsb[1] * saturation, 0.0, 1.0);
        int rgb = Color.HSBtoRGB(hsb[0], newSat, hsb[2]);
        return FastColor.ABGR32.color(a, rgb & 255, rgb >> 8 & 255, rgb >> 16 & 255);
    }

    /** Forces the next {@link #maybeRebuild} call to rebuild even if the center is unchanged. */
    public void invalidate() {
        lastCenter = null;
    }

    /** Invalidates every currently-live {@code SolarisTexture} — called by {@code SolarisAPI.requestRefresh}. */
    public static void invalidateAll() {
        for (SolarisTexture texture : ACTIVE) texture.invalidate();
    }

    @Override
    public void close() {
        ACTIVE.remove(this);
        texture.close();
    }
}
