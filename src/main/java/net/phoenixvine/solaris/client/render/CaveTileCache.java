package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.CaveColorSampler;
import net.phoenixvine.solaris.client.color.PersistentCaveStore;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CaveTileCache {

    public static final int TILE_CHUNKS = MapTileCache.TILE_CHUNKS;
    public static final int TILE_PIXELS = MapTileCache.TILE_PIXELS;

    private static final int MAX_CACHED_TILES = 512;
    private static final int REVEAL_RADIUS_CHUNKS = 3;
    private static final long LIVE_REBUILD_THROTTLE_MS = 250L;

    public record TileKey(ResourceLocation dimension, int tileX, int tileZ, int yBucket) {}

    public static final class CaveTile implements AutoCloseable {

        private final ResourceLocation textureId;
        private final NativeImage image;
        private final DynamicTexture texture;
        private long lastLiveRebuildMillis = 0L;

        private CaveTile(ResourceLocation textureId) {
            this.textureId = textureId;
            this.image = new NativeImage(TILE_PIXELS, TILE_PIXELS, false);
            this.texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);

            texture.setFilter(false, false);
        }

        public ResourceLocation textureId() {
            return textureId;
        }

        @Override
        public void close() {
            texture.close();
        }
    }

    private static int nextTileId = 0;

    private static final Map<TileKey, CaveTile> TILES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, CaveTile> eldest) {
                    if (size() <= MAX_CACHED_TILES) return false;
                    eldest.getValue().close();
                    return true;
                }
            });

    private static final Set<TileKey> DIRTY = ConcurrentHashMap.newKeySet();

    private CaveTileCache() {}

    public static void clearAll() {
        synchronized (TILES) {
            for (CaveTile tile : TILES.values()) tile.close();
            TILES.clear();
        }
        DIRTY.clear();
    }

    private static boolean overlapsReveal(TileKey key, int playerChunkX, int playerChunkZ) {
        int tileMinX = key.tileX() * TILE_CHUNKS;
        int tileMinZ = key.tileZ() * TILE_CHUNKS;
        int tileMaxX = tileMinX + TILE_CHUNKS - 1;
        int tileMaxZ = tileMinZ + TILE_CHUNKS - 1;
        return tileMinX <= playerChunkX + REVEAL_RADIUS_CHUNKS && tileMaxX >= playerChunkX - REVEAL_RADIUS_CHUNKS &&
                tileMinZ <= playerChunkZ + REVEAL_RADIUS_CHUNKS && tileMaxZ >= playerChunkZ - REVEAL_RADIUS_CHUNKS;
    }

    public static CaveTile getOrBuildTile(TileKey key, Level level, Player player, int[] buildBudget) {
        CaveTile tile = TILES.get(key);
        boolean dirty = DIRTY.remove(key);

        int playerChunkX = player != null ? Mth.floor(player.getX()) >> 4 : Integer.MAX_VALUE / 2;
        int playerChunkZ = player != null ? Mth.floor(player.getZ()) >> 4 : Integer.MAX_VALUE / 2;
        boolean live = level != null && player != null && overlapsReveal(key, playerChunkX, playerChunkZ);

        if (tile != null && !dirty) {
            if (!live) return tile;
            long now = System.currentTimeMillis();
            if (now - tile.lastLiveRebuildMillis < LIVE_REBUILD_THROTTLE_MS) return tile;
        }

        if (buildBudget[0] <= 0) {
            DIRTY.add(key);
            return tile;
        }
        buildBudget[0]--;

        if (tile == null) {
            ResourceLocation id = new ResourceLocation(PhoenixSolaris.MOD_ID,
                    "dynamic/cave_tile_" + nextTileId++ + "_" + key.tileX() + "_" + key.tileZ() + "_" +
                            key.yBucket());
            tile = new CaveTile(id);
            TILES.put(key, tile);
        }

        buildTile(key, tile, level, player, playerChunkX, playerChunkZ);
        if (live) tile.lastLiveRebuildMillis = System.currentTimeMillis();
        return tile;
    }

    private static int themeToAbgr(int argb) {
        return FastColor.ABGR32.color(FastColor.ARGB32.alpha(argb), FastColor.ARGB32.blue(argb),
                FastColor.ARGB32.green(argb), FastColor.ARGB32.red(argb));
    }

    private static void buildTile(TileKey key, CaveTile tile, Level level, Player player, int playerChunkX,
                                  int playerChunkZ) {
        NativeImage image = tile.image;
        String dimensionStr = key.dimension().toString();

        int playerY = player != null ? Mth.floor(player.getY()) : 0;

        int fogColor = themeToAbgr(SolarisThemeUtils.C_FAINT);

        UnexploredStyle unexploredStyle = SolarisAPI.getUnexploredStyle(key.dimension());
        double unexploredDensity = SolarisConfig.UNEXPLORED_DENSITY.get();
        double unexploredBrightness = SolarisConfig.UNEXPLORED_BRIGHTNESS.get();
        int starAccent = themeToAbgr(SolarisThemeUtils.C_ACCENT);
        int starDim = themeToAbgr(SolarisThemeUtils.C_DIM);
        int starSpace = SolarisTexture.scaleBrightness(fogColor, 0.15);
        boolean unexploredImageCover = SolarisConfig.UNEXPLORED_IMAGE_COVER.get();

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();
        boolean showClaims = SolarisConfig.SHOW_CLAIMS_MAP.get();

        int tileChunkMinX = key.tileX() * TILE_CHUNKS;
        int tileChunkMinZ = key.tileZ() * TILE_CHUNKS;

        for (int cz = 0; cz < TILE_CHUNKS; cz++) {
            for (int cx = 0; cx < TILE_CHUNKS; cx++) {
                int chunkX = tileChunkMinX + cx;
                int chunkZ = tileChunkMinZ + cz;

                boolean caveReveal = level != null && player != null &&
                        Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ)) <=
                                REVEAL_RADIUS_CHUNKS;

                int[] pixels;
                if (caveReveal) {
                    pixels = CaveColorSampler.sample(level, new ChunkPos(chunkX, chunkZ), playerY);
                    PersistentCaveStore.put(dimensionStr, chunkX, chunkZ, key.yBucket(), pixels);
                } else {
                    pixels = PersistentCaveStore.get(dimensionStr, chunkX, chunkZ, key.yBucket());
                }
                int tint = 0;
                boolean hasTint = false;
                if (showClaims) {
                    for (SolarisOverlay overlay : overlays) {
                        Optional<Integer> color = overlay.colorAt(key.dimension(), chunkX, chunkZ);
                        if (color.isPresent()) {
                            tint = color.get();
                            hasTint = true;
                        }
                    }
                }

                int baseX = cx * 16;
                int baseZ = cz * 16;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int local = lz * 16 + lx;
                        int color = pixels != null ? pixels[local] : 0;
                        if (color == 0) {
                            if (pixels != null) {
                                color = fogColor;
                            } else {
                                int worldPx = chunkX * 16 + lx;
                                int worldPz = chunkZ * 16 + lz;
                                color = switch (unexploredStyle) {
                                    case FOG -> FastColor.ABGR32.color(255, 0, 0, 0);
                                    case STARFIELD -> MapTileCache.starfieldPixel(worldPx, worldPz,
                                            unexploredDensity, unexploredBrightness, starAccent, starDim, starSpace);
                                    case PHOENIX -> MapTileCache.phoenixPixel(worldPx, worldPz, unexploredDensity,
                                            unexploredBrightness);
                                    case CLOUD -> MapTileCache.cloudPixel(worldPx, worldPz, unexploredDensity,
                                            unexploredBrightness);
                                    case IMAGE -> {
                                        if (unexploredImageCover) yield 0;
                                        int imgColor = UnexploredImageStyle.getPixel(worldPx, worldPz);
                                        yield imgColor != 0 ? imgColor : FastColor.ABGR32.color(255, 0, 0, 0);
                                    }
                                };
                            }
                        } else {
                            if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                            if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                            if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                            if (hasColorTint) {
                                color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);
                            }
                        }
                        if (hasTint) color = SolarisTexture.blend(color, tint);
                        image.setPixelRGBA(baseX + lx, baseZ + lz, color);
                    }
                }
            }
        }

        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            applyBlackAndWhite(image);
        }

        tile.texture.bind();
        tile.texture.upload();

        tile.texture.setFilter(false, false);
    }

    private static void applyBlackAndWhite(NativeImage image) {
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int abgr = image.getPixelRGBA(x, z);
                int a = FastColor.ABGR32.alpha(abgr);
                int r = FastColor.ABGR32.red(abgr);
                int g = FastColor.ABGR32.green(abgr);
                int b = FastColor.ABGR32.blue(abgr);
                int gray = SolarisTexture.clampChannel(Math.round(0.299f * r + 0.587f * g + 0.114f * b));
                image.setPixelRGBA(x, z, FastColor.ABGR32.color(a, gray, gray, gray));
            }
        }
    }
}
