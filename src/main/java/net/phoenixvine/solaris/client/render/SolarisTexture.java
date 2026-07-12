package net.phoenixvine.solaris.client.render;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.CaveColorSampler;
import net.phoenixvine.solaris.client.color.ChunkColorCache;
import net.phoenixvine.solaris.client.color.ChunkColorSampler;
import net.phoenixvine.solaris.client.color.ChunkHeightCache;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.ChunkWaterCache;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
    /** Y used for chunks with no cached height yet (or while underground) — vanilla's sea level, a neutral baseline. */
    private static final int DEFAULT_HEIGHT = 63;

    private final String name;
    private final ResourceLocation textureId;
    private final int radiusChunks;
    private final int sizePixels;
    private final NativeImage image;
    private final DynamicTexture texture;
    /** Row-major (z*sizePixels+x) surface Y per pixel, same grid as {@code image} — the globe's relief data. */
    private final int[] heights;
    /** Row-major (z*sizePixels+x), whether that pixel's surface is water — see {@link #ensureGlobeTexture}. */
    private final boolean[] water;

    // Lazily created only once the globe is actually opened — the minimap and flat map never
    // touch this, so they never pay for it. See ensureGlobeTexture() on why the globe needs a
    // texture variant distinct from the flat map's.
    private ResourceLocation globeTextureId;
    private NativeImage globeImage;
    private DynamicTexture globeTexture;
    private int rebuildGeneration = 0;
    private int lastGlobeGeneration = -1;

    private ChunkKey lastCenter;
    private int lastPlayerY = Integer.MIN_VALUE;
    private boolean lastUnderground = false;

    public SolarisTexture(String name, int radiusChunks) {
        this.name = name;
        this.radiusChunks = radiusChunks;
        int spanChunks = radiusChunks * 2 + 1;
        this.sizePixels = spanChunks * 16;

        this.textureId = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/" + name);
        this.image = new NativeImage(sizePixels, sizePixels, false);
        this.texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        // Never set anywhere before — this texture was sitting at nearest-neighbor point
        // sampling with no mipmap. Zoomed out (many texture pixels per screen pixel), point
        // sampling aliases any periodic content in the source data into Moiré-style banding,
        // completely independent of how clean the underlying pixel data actually is — matches
        // the "still there after an aggressive blur" report exactly (blurring the source can't
        // fix an artifact that's introduced at display/sampling time, not baked into the data).
        // SmoothShapes already does this correctly for its own baked textures; the main terrain
        // texture just never got the same treatment.
        texture.setFilter(true, false);
        this.heights = new int[sizePixels * sizePixels];
        this.water = new boolean[sizePixels * sizePixels];

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

    /** Row-major (z*getSizePixels()+x) surface Y per pixel, same grid {@link #textureId()}'s pixels use. */
    public int[] getHeights() {
        return heights;
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter EXPORT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    /**
     * Snapshots the currently-rendered terrain texture (whatever {@link #rebuild} last wrote —
     * flat mode's own view, not the globe's de-relief variant) to a timestamped PNG under
     * {@code <gameDir>/solaris_maps/}, mirroring vanilla {@code Screenshot.grab}'s own pattern:
     * copy the pixels synchronously (since {@link #image} keeps getting overwritten in place on
     * the render thread every rebuild) then hand the actual disk write to {@code Util.ioPool()}
     * so a large map doesn't stall a frame. Reports success/failure back through
     * {@code messageConsumer} the same way, including a clickable open-file link.
     */
    public void exportToPng(Consumer<Component> messageConsumer) {
        NativeImage snapshot = new NativeImage(sizePixels, sizePixels, false);
        snapshot.copyFrom(image);

        File dir = new File(Minecraft.getInstance().gameDirectory, "solaris_maps");
        dir.mkdirs();
        File file = new File(dir, name + "_" + LocalDateTime.now().format(EXPORT_NAME_FORMAT) + ".png");

        Util.ioPool().execute(() -> {
            try {
                snapshot.writeToFile(file);
                Component link = Component.literal(file.getName()).withStyle(ChatFormatting.UNDERLINE).withStyle(
                        style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
                messageConsumer.accept(Component.translatable("solaris.export.success", link));
            } catch (IOException e) {
                LOGGER.warn("Couldn't export Solaris map", e);
                messageConsumer.accept(Component.translatable("solaris.export.failure", e.getMessage()));
            } finally {
                snapshot.close();
            }
        });
    }

    /**
     * Whether this texture should render a {@link CaveColorSampler} slice at the player's current
     * Y instead of the normal cached world-surface terrain — true either because the player is
     * genuinely underground in a dimension with real sky, or because the whole dimension is one
     * where "world surface" doesn't mean anything to begin with.
     *
     * Verified against decompiled vanilla {@code MapItem.update}: vanilla's own map item special
     * -cases exactly this condition ({@code level.dimensionType().hasCeiling()}, true only for the
     * Nether) — and vanilla's fix is to give up on real terrain entirely, rendering a fixed
     * pseudo-random dirt/stone static pattern instead, because {@code Heightmap.Types
     * .WORLD_SURFACE} finds the solid bedrock roof there, not anything a player would recognize as
     * "the map". Since Solaris already has a real slice-at-a-given-Y renderer (built for the
     * overworld's own underground cave-reveal mode), reusing it here instead gives an actually
     * useful Nether map — real nearby terrain/lava/structures at the player's altitude — rather
     * than either vanilla's fake static or a broken solid-roof-color map. {@code hasCeiling()} is
     * checked unconditionally (not gated behind an X/Z heightmap comparison the way ordinary
     * underground detection is), since that comparison is exactly the thing that's meaningless in
     * a ceiling dimension in the first place.
     */
    private static boolean isCaveSliceMode(Level level, Player player) {
        if (level == null) return false;
        if (level.dimensionType().hasCeiling()) return true;
        return player != null && CaveColorSampler.isUnderground(level, player.blockPosition());
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
        boolean underground = isCaveSliceMode(level, player);

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
        long profileStart = SolarisProfiler.start();
        lastCenter = center;
        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        boolean underground = isCaveSliceMode(level, player);
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
                int[] chunkHeights;
                boolean[] chunkWater;
                if (underground) {
                    pixels = caveReveal ? CaveColorSampler.sample(level, key.toChunkPos(), playerY) : null;
                    unknownColor = caveReveal ? fogColor : hiddenColor;
                    // Surface relief for the globe — always the real cached surface height
                    // regardless of the cave-slice branch above, since the globe shows terrain
                    // shape, not whatever cave slice happens to be revealed underground.
                    chunkHeights = ChunkHeightCache.get(key);
                    chunkWater = ChunkWaterCache.get(key);
                } else {
                    pixels = ChunkColorCache.get(key);
                    chunkHeights = ChunkHeightCache.get(key);
                    chunkWater = ChunkWaterCache.get(key);
                    if (pixels == null) {
                        // Not in the in-memory working cache — either never sampled this
                        // session, or aged out of its LRU bound. Fall back to whatever was last
                        // persisted to disk (see PersistentChunkStore's doc) rather than showing
                        // fog for terrain the player has genuinely already explored; also
                        // backfills the hot cache so this lookup only costs a map miss once, not
                        // every rebuild. Deliberately skipped for cave slices above — an
                        // underground reveal is inherently live/ephemeral, not something worth
                        // remembering to disk.
                        PersistentChunkStore.Entry persisted = PersistentChunkStore.get(key);
                        if (persisted != null) {
                            pixels = persisted.pixels();
                            chunkHeights = persisted.heights();
                            chunkWater = persisted.water();
                            ChunkColorCache.put(key, pixels);
                            ChunkHeightCache.put(key, chunkHeights);
                            ChunkWaterCache.put(key, chunkWater);
                        }
                    }
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

                        int height = chunkHeights != null ? chunkHeights[lz * 16 + lx] : DEFAULT_HEIGHT;
                        int idx = (baseZ + lz) * sizePixels + (baseX + lx);
                        heights[idx] = height;
                        water[idx] = chunkWater != null && chunkWater[lz * 16 + lx];
                    }
                }
            }
        }

        SolarisProfiler.time("waterBlur:" + name, this::blurWater);
        if (SolarisConfig.HILLSHADING.get()) {
            SolarisProfiler.time("hillshading:" + name, this::applyHillshading);
        }

        texture.upload();
        rebuildGeneration++;
        SolarisProfiler.end("textureRebuild:" + name, profileStart);
    }

    /**
     * Blur kernel half-width — was 1 (3x3), then 2 (5x5), then 4 (9x9), widened again each time
     * after the previous size survived. User-confirmed (deterministic across forced chunk
     * reloads, same biome on both sides, no ice nearby, unaffected by display zoom level — so
     * it's baked into the actual pixel data, not a display-time aliasing effect) that this is
     * genuine fine-grained seafloor depth variation feeding {@code sampleWater}'s depth-based
     * tint curve, rendered at true 1:1 pixel resolution — vanilla's own map effectively never
     * shows this because it's rendered at much lower effective resolution. Still visible at 9x9
     * per a follow-up report ("a lot smoother now but still very obvious"), meaning the
     * underlying variation's period is wider than three successive conservative doublings — 17x17
     * is a decisive jump rather than another small increment, since incremental widening alone
     * has already taken three rounds to only partially work.
     */
    private static final int WATER_BLUR_RADIUS = 8;

    /**
     * A box blur (see {@link #WATER_BLUR_RADIUS}) over water pixels only (checked against
     * {@link #water}, and only averaging in neighbors that are themselves water — a coastline
     * pixel shouldn't get muddied with land color). Three earlier attempts at smoothing the
     * water-color seam at ocean sub-biome boundaries (kelp/seagrass relief exemption, a wider
     * floor-scan depth, then a wider world-space neighbor-biome sample for the tint color) all
     * worked at the mechanism they targeted but the seam kept recurring — most recently as a
     * visible checkerboard of alternating tile colors specific to frozen/deep ocean. A
     * world-space sample taken at fixed offsets can alias against whatever period the underlying
     * biome noise has (sampling "smoothing" points that land back on the same two alternating
     * values instead of a genuine spread), which a plain pixel-space blur over the final
     * rendered colors can't: it operates on every single pixel, not sparse samples, so it can't
     * miss a pattern regardless of its period. Runs unconditionally (not gated behind
     * {@link SolarisConfig#HILLSHADING}) since this is fixing a real artifact, not an optional
     * stylistic effect.
     *
     * Implemented as a <b>separable</b> two-pass sliding-window sum (horizontal then vertical)
     * rather than the straightforward nested-loop box blur this used to be. That naive version
     * re-summed a fresh {@code (2r+1)x(2r+1)} neighborhood from scratch for every single water
     * pixel — {@link SolarisProfiler} caught it directly costing 200-400ms per rebuild once
     * {@link #WATER_BLUR_RADIUS} was widened to 8 (289 samples/pixel) to actually beat the
     * banding, a real, self-inflicted hitch (the same session's log showed a server "can't keep
     * up" warning land right on top of it). A 2D box sum is mathematically separable into a
     * horizontal pass followed by a vertical pass — true even for this <i>masked</i> version
     * (only summing water-flagged neighbors), since {@code mask*value} and {@code mask} are just
     * two more values being box-summed, and box-summing is linear — and each pass uses a sliding
     * window (add the pixel entering the window, subtract the one leaving) instead of re-summing,
     * so total cost is O(1) amortized per pixel per pass instead of O(r^2) per pixel. Produces the
     * identical result to the old code at any radius, just without paying for it quadratically.
     */
    private void blurWater() {
        long[] hSumR = new long[sizePixels * sizePixels];
        long[] hSumG = new long[sizePixels * sizePixels];
        long[] hSumB = new long[sizePixels * sizePixels];
        int[] hCount = new int[sizePixels * sizePixels];

        // Horizontal pass: for each row, a sliding window sum over x of water-flagged neighbors.
        for (int z = 0; z < sizePixels; z++) {
            int rowBase = z * sizePixels;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            int count = 0;
            for (int x = 0; x <= Math.min(WATER_BLUR_RADIUS, sizePixels - 1); x++) {
                if (!water[rowBase + x]) continue;
                int abgr = image.getPixelRGBA(x, z);
                sumR += FastColor.ABGR32.red(abgr);
                sumG += FastColor.ABGR32.green(abgr);
                sumB += FastColor.ABGR32.blue(abgr);
                count++;
            }
            hSumR[rowBase] = sumR;
            hSumG[rowBase] = sumG;
            hSumB[rowBase] = sumB;
            hCount[rowBase] = count;

            for (int x = 1; x < sizePixels; x++) {
                int addX = x + WATER_BLUR_RADIUS;
                int removeX = x - WATER_BLUR_RADIUS - 1;
                if (addX < sizePixels && water[rowBase + addX]) {
                    int abgr = image.getPixelRGBA(addX, z);
                    sumR += FastColor.ABGR32.red(abgr);
                    sumG += FastColor.ABGR32.green(abgr);
                    sumB += FastColor.ABGR32.blue(abgr);
                    count++;
                }
                if (removeX >= 0 && water[rowBase + removeX]) {
                    int abgr = image.getPixelRGBA(removeX, z);
                    sumR -= FastColor.ABGR32.red(abgr);
                    sumG -= FastColor.ABGR32.green(abgr);
                    sumB -= FastColor.ABGR32.blue(abgr);
                    count--;
                }
                int idx = rowBase + x;
                hSumR[idx] = sumR;
                hSumG[idx] = sumG;
                hSumB[idx] = sumB;
                hCount[idx] = count;
            }
        }

        // Vertical pass: slide the same kind of window over z, summing the horizontal-pass
        // results — combined, this is exactly the full 2D masked box sum for every pixel.
        int[] blurred = new int[sizePixels * sizePixels];
        for (int x = 0; x < sizePixels; x++) {
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            long count = 0;
            for (int z = 0; z <= Math.min(WATER_BLUR_RADIUS, sizePixels - 1); z++) {
                int idx = z * sizePixels + x;
                sumR += hSumR[idx];
                sumG += hSumG[idx];
                sumB += hSumB[idx];
                count += hCount[idx];
            }
            setBlurredPixel(blurred, x, 0, sumR, sumG, sumB, count);

            for (int z = 1; z < sizePixels; z++) {
                int addZ = z + WATER_BLUR_RADIUS;
                int removeZ = z - WATER_BLUR_RADIUS - 1;
                if (addZ < sizePixels) {
                    int idx = addZ * sizePixels + x;
                    sumR += hSumR[idx];
                    sumG += hSumG[idx];
                    sumB += hSumB[idx];
                    count += hCount[idx];
                }
                if (removeZ >= 0) {
                    int idx = removeZ * sizePixels + x;
                    sumR -= hSumR[idx];
                    sumG -= hSumG[idx];
                    sumB -= hSumB[idx];
                    count -= hCount[idx];
                }
                setBlurredPixel(blurred, x, z, sumR, sumG, sumB, count);
            }
        }

        for (int z = 0; z < sizePixels; z++) {
            for (int x = 0; x < sizePixels; x++) {
                int idx = z * sizePixels + x;
                if (water[idx]) image.setPixelRGBA(x, z, blurred[idx]);
            }
        }
    }

    /**
     * Writes the averaged color for {@code (x, z)} into {@code blurred} if that pixel is water and the window found any
     * water neighbors.
     */
    private void setBlurredPixel(int[] blurred, int x, int z, long sumR, long sumG, long sumB, long count) {
        int idx = z * sizePixels + x;
        if (!water[idx] || count == 0) return;
        blurred[idx] = FastColor.ABGR32.color(255, (int) (sumB / count), (int) (sumG / count), (int) (sumR / count));
    }

    // Fixed light direction (elevated northwest, the classic cartographic hillshade convention)
    // rather than anything tied to the actual sun — this is a stylized relief effect, not a
    // real-time lighting simulation, and a fixed angle keeps every part of the map lit
    // consistently regardless of time of day or which way the player's facing.
    private static final float LIGHT_X = -0.5f;
    private static final float LIGHT_Y = -0.5f;
    private static final float LIGHT_Z = 0.8f;
    private static final float LIGHT_LEN = (float) Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);
    /**
     * Flat ground's own shade value (normal (0,0,1) dotted with the light above) — the neutral point, no
     * brightening/darkening.
     */
    private static final float FLAT_SHADE = LIGHT_Z / LIGHT_LEN;
    /**
     * How much a fully lit/shadowed slope brightens/darkens relative to flat ground, before
     * {@code HILLSHADING_STRENGTH} scales it.
     */
    private static final float HILLSHADE_GAIN = 1.8f;

    /**
     * Real cartographic hillshading — a smooth slope-based light/dark gradient from each pixel's
     * full surrounding neighborhood (a proper surface normal via central differences), rather
     * than {@link ChunkColorSampler}'s single north-neighbor comparison already baked into the
     * cached pixel colors this texture reads. That single-neighbor approach is a cheap
     * approximation, not real relief — and, being a single fixed direction with a hard on/off
     * threshold rather than a continuous gradient, it's also the source of most of this map's
     * historical chunk-edge-aligned streaking bugs. This applies on top of it as a genuine second
     * pass (multiplying the already-written pixel colors) rather than trying to first undo it,
     * since there's no way to recover the pre-relief color from the cache at this point — for
     * most pixels the single-neighbor effect is subtle enough that layering reads fine, and this
     * is opt-in (see {@link SolarisConfig#HILLSHADING}) specifically because it's a full second
     * pass over every pixel, not free.
     */
    private void applyHillshading() {
        double strength = SolarisConfig.HILLSHADING_STRENGTH.get();
        for (int z = 0; z < sizePixels; z++) {
            int north = Math.max(0, z - 1);
            int south = Math.min(sizePixels - 1, z + 1);
            for (int x = 0; x < sizePixels; x++) {
                int west = Math.max(0, x - 1);
                int east = Math.min(sizePixels - 1, x + 1);

                float dzdx = (heights[z * sizePixels + east] - heights[z * sizePixels + west]) * 0.5f;
                float dzdy = (heights[south * sizePixels + x] - heights[north * sizePixels + x]) * 0.5f;

                float nx = -dzdx;
                float ny = -dzdy;
                float nz = 1f;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

                float shade = (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / (nLen * LIGHT_LEN);
                float factor = 1f + (float) strength * HILLSHADE_GAIN * (shade - FLAT_SHADE);
                factor = Mth.clamp(factor, 0.3f, 1.8f);

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, scaleBrightness(abgr, factor));
            }
        }
    }

    /**
     * Builds (or refreshes, if stale) a texture variant for the globe specifically, and returns
     * its id. Cheap to call every frame — the actual de-relief pass below only runs again once
     * {@link #rebuild} has actually run since the last call.
     *
     * The flat map's relief shading ({@link ChunkColorSampler}'s north-neighbor highlight/shadow,
     * baked directly into the cached pixel colors) is correct only when viewed from its fixed
     * north-up top-down angle. Wrap that same shaded texture onto a rotatable sphere and at most
     * rotations the baked lighting direction no longer matches the actual view angle, so terrain
     * bumps (most visibly small ones, like individual tree canopies) read as pits instead —
     * reported as "trees are upside down". The globe already has its own genuinely
     * angle-correct 3D shading ({@code SphereMesh}'s rim lighting + real height displacement),
     * making the flat map's 2D-simulated relief redundant AND actively wrong there. This
     * approximately un-applies that shading per pixel, using the same relief-factor thresholds
     * {@link ChunkColorSampler} used to apply it, recomputed here from the already-cached
     * {@link #heights} array rather than re-sampling the world — skipped entirely for water
     * pixels ({@link #water}), since {@link ChunkColorSampler#sample} never applied relief
     * shading to water's color in the first place (see its {@code MapColor.WATER} branch); water
     * height is otherwise near-constant at sea level except for tiny kelp/seagrass noise, which
     * un-applying a "relief" that was never there just turned into streaky, sea-noise-shaped
     * artifacts across every stretch of water.
     */
    public ResourceLocation ensureGlobeTexture() {
        if (globeTexture == null) {
            globeTextureId = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/" + name + "_globe");
            globeImage = new NativeImage(sizePixels, sizePixels, false);
            globeTexture = new DynamicTexture(globeImage);
            Minecraft.getInstance().getTextureManager().register(globeTextureId, globeTexture);
            // Same fix as the main texture's constructor — nearest-neighbor sampling on a
            // sphere viewed at variable zoom is exactly the same aliasing risk.
            globeTexture.setFilter(true, false);
        }
        if (lastGlobeGeneration == rebuildGeneration) return globeTextureId;
        lastGlobeGeneration = rebuildGeneration;

        for (int z = 0; z < sizePixels; z++) {
            for (int x = 0; x < sizePixels; x++) {
                int idx = z * sizePixels + x;
                int abgr = image.getPixelRGBA(x, z);
                double reliefFactor = 1.0;
                if (z > 0 && !water[idx]) {
                    double delta = (heights[idx] - heights[idx - sizePixels]) * ChunkColorSampler.RELIEF_NEIGHBOR_SCALE;
                    reliefFactor = ChunkColorSampler.relief(delta);
                }
                int unReliefed = reliefFactor != 1.0 ? scaleBrightness(abgr, 1.0 / reliefFactor) : abgr;
                globeImage.setPixelRGBA(x, z, unReliefed);
            }
        }
        globeTexture.upload();
        return globeTextureId;
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

    /** Scales an already-ABGR-packed pixel's RGB channels by {@code factor}, clamped to a valid byte. */
    private static int scaleBrightness(int abgr, double factor) {
        int a = abgr >>> 24;
        int r = clampChannel((int) ((abgr & 255) * factor));
        int g = clampChannel((int) ((abgr >> 8 & 255) * factor));
        int b = clampChannel((int) ((abgr >> 16 & 255) * factor));
        return a << 24 | b << 16 | g << 8 | r;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
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
        if (globeTexture != null) globeTexture.close();
    }
}
