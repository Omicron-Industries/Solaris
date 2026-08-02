package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.ChunkColorCache;
import net.phoenixvine.solaris.client.color.ChunkColorSampler;
import net.phoenixvine.solaris.client.color.ChunkFoliageCache;
import net.phoenixvine.solaris.client.color.ChunkHeightCache;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.ChunkLightCache;
import net.phoenixvine.solaris.client.color.ChunkWaterCache;
import net.phoenixvine.solaris.client.color.ChunkWaterDepthCache;
import net.phoenixvine.solaris.client.color.ChunkWaterOceanCache;
import net.phoenixvine.solaris.client.color.ChunkWaterTintCache;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
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

public final class MapTileCache {

    public static final int TILE_CHUNKS = 8;
    public static final int TILE_PIXELS = TILE_CHUNKS * 16;

    private static final int MAX_CACHED_TILES = 512;

    public record TileKey(ResourceLocation dimension, int tileX, int tileZ) {

        public static TileKey ofChunk(ResourceLocation dimension, int chunkX, int chunkZ) {
            return new TileKey(dimension, Math.floorDiv(chunkX, TILE_CHUNKS), Math.floorDiv(chunkZ, TILE_CHUNKS));
        }
    }

    public static final class MapTile implements AutoCloseable {

        private final ResourceLocation textureId;
        private final NativeImage image;
        private final DynamicTexture texture;

        private MapTile(ResourceLocation textureId) {
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

    private static final Map<TileKey, MapTile> TILES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, MapTile> eldest) {
                    if (size() <= MAX_CACHED_TILES) return false;
                    eldest.getValue().close();
                    return true;
                }
            });

    private static final Set<TileKey> DIRTY = ConcurrentHashMap.newKeySet();

    private static double lastNightFactorBucket = -1;
    private static final double NIGHT_FACTOR_BUCKET = 0.05;

    private MapTileCache() {}

    public static void checkNightFactor(Level level) {
        if (level == null) return;
        double bucket = Math.round(ChunkColorSampler.nightFactor(level.getDayTime()) / NIGHT_FACTOR_BUCKET) *
                NIGHT_FACTOR_BUCKET;
        if (bucket == lastNightFactorBucket) return;
        lastNightFactorBucket = bucket;
        synchronized (TILES) {
            DIRTY.addAll(TILES.keySet());
        }
    }

    public static MapTile getOrBuildTile(TileKey key) {
        MapTile tile = TILES.get(key);
        boolean dirty = DIRTY.remove(key);
        if (tile != null && !dirty) return tile;

        if (!PersistentChunkStore.isLoaded(key.dimension().toString())) {
            if (dirty) DIRTY.add(key);
            return tile;
        }

        if (tile == null) {
            ResourceLocation id = new ResourceLocation(PhoenixSolaris.MOD_ID,
                    "dynamic/tile_" + nextTileId++ + "_" + key.tileX() + "_" + key.tileZ());
            tile = new MapTile(id);
            TILES.put(key, tile);
        }
        try {
            buildTile(key, tile);
        } catch (Exception e) {

            DIRTY.add(key);
            PhoenixSolaris.LOGGER.error("[Solaris] Failed to build map tile {} — will retry next frame", key, e);
        }
        return tile;
    }

    public static void markDirty(ChunkKey key) {
        DIRTY.add(TileKey.ofChunk(key.dimension(), key.x(), key.z()));
    }

    public static void clearAll() {
        synchronized (TILES) {
            for (MapTile tile : TILES.values()) tile.close();
            TILES.clear();
        }
        DIRTY.clear();
    }

    private static final int HALO = TILE_PIXELS + 2;

    private static int haloIdx(int x, int z) {
        return (z + 1) * HALO + (x + 1);
    }

    private static int themeToAbgr(int argb) {
        return FastColor.ABGR32.color(FastColor.ARGB32.alpha(argb), FastColor.ARGB32.blue(argb),
                FastColor.ARGB32.green(argb), FastColor.ARGB32.red(argb));
    }

    private static int starfieldPixel(int worldX, int worldZ, double density, double brightness,
                                      int accentAbgr, int dimAbgr, int spaceAbgr) {
        long h = (long) worldX * 0x9E3779B97F4A7C15L + (long) worldZ * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;

        int bucket = (int) (h & 0x3FF);
        int brightThreshold = (int) Math.round(3 * density);
        int dimThreshold = (int) Math.round(12 * density);
        if (bucket < brightThreshold) {
            double variance = 0.85 + ((h >>> 40) & 0x2D) / 180.0;
            return SolarisTexture.scaleBrightness(accentAbgr, brightness * variance);
        }
        if (bucket < dimThreshold) {
            double variance = 0.45 + ((h >>> 40) & 0x4F) / 160.0;
            return SolarisTexture.scaleBrightness(dimAbgr, brightness * variance);
        }
        return spaceAbgr;
    }

    private static final int EMBER_SPACE_COLOR = FastColor.ABGR32.color(255, 3, 4, 18);

    private static int phoenixPixel(int worldX, int worldZ, double density, double brightness) {
        long h = (long) worldX * 0x9E3779B97F4A7C15L + (long) worldZ * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;

        int bucket = (int) (h & 0x3FF);
        int brightThreshold = (int) Math.round(3 * density);
        int dimThreshold = (int) Math.round(14 * density);
        if (bucket < brightThreshold) {
            int r = 255;
            int g = 130 + (int) ((h >>> 40) & 0x3F);
            int b = 20 + (int) ((h >>> 48) & 0x1F);
            return SolarisTexture.scaleBrightness(FastColor.ABGR32.color(255, b, g, r), brightness);
        }
        if (bucket < dimThreshold) {
            int r = 140 + (int) ((h >>> 40) & 0x3F);
            int g = 40 + (int) ((h >>> 48) & 0x2F);
            int b = 8;
            return SolarisTexture.scaleBrightness(FastColor.ABGR32.color(255, b, g, r), brightness * 0.8);
        }
        return EMBER_SPACE_COLOR;
    }

    private static double blobNoise(int worldX, int worldZ, int scale, long seed) {
        int bx = Math.floorDiv(worldX, scale);
        int bz = Math.floorDiv(worldZ, scale);
        long h = (long) bx * 0x9E3779B97F4A7C15L + (long) bz * 0xBF58476D1CE4E5B9L + seed;
        h ^= h >>> 31;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static int cloudPixel(int worldX, int worldZ, double density, double brightness) {
        double coarse = blobNoise(worldX, worldZ, 20, 0x1L);
        double fine = blobNoise(worldX, worldZ, 6, 0x9E3779B9L);
        double combined = coarse * 0.7 + fine * 0.3;

        double coverage = Mth.clamp(0.35 * density, 0.05, 0.9);
        double t = Mth.clamp((combined - (1.0 - coverage)) / coverage, 0.0, 1.0);
        if (t <= 0.0) return 0;

        int alpha = (int) Math.round(Mth.clamp(60 + t * 150 * brightness, 0, 235));
        int gray = Math.min(240, (int) Math.round(190 + t * 40));
        return FastColor.ABGR32.color(alpha, gray, gray, gray);
    }

    private static void buildTile(TileKey key, MapTile tile) {
        NativeImage image = tile.image;
        int[] heights = new int[HALO * HALO];
        boolean[] water = new boolean[HALO * HALO];
        int[] waterDepth = new int[HALO * HALO];
        boolean[] lightEmitting = new boolean[TILE_PIXELS * TILE_PIXELS];

        int fogColor = themeToAbgr(SolarisThemeUtils.C_FAINT);
        UnexploredStyle unexploredStyle = SolarisAPI.getUnexploredStyle(key.dimension());
        double unexploredDensity = SolarisConfig.UNEXPLORED_DENSITY.get();
        double unexploredBrightness = SolarisConfig.UNEXPLORED_BRIGHTNESS.get();
        int starAccent = themeToAbgr(SolarisThemeUtils.C_ACCENT);
        int starDim = themeToAbgr(SolarisThemeUtils.C_DIM);
        int starSpace = SolarisTexture.scaleBrightness(fogColor, 0.3);

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double foliageBrightness = SolarisConfig.FOLIAGE_BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();
        ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks = PersistentChunkStore
                .chunksFor(key.dimension().toString());

        int cacheHits = 0;
        int persistedHits = 0;
        int blankChunks = 0;

        for (int cz = 0; cz < TILE_CHUNKS; cz++) {
            for (int cx = 0; cx < TILE_CHUNKS; cx++) {
                int chunkX = key.tileX() * TILE_CHUNKS + cx;
                int chunkZ = key.tileZ() * TILE_CHUNKS + cz;
                ChunkKey ckey = new ChunkKey(key.dimension(), chunkX, chunkZ);
                int baseX = cx * 16;
                int baseZ = cz * 16;

                int[] pixels = ChunkColorCache.get(ckey);
                int[] chunkHeights = ChunkHeightCache.get(ckey);
                boolean[] chunkWater = ChunkWaterCache.get(ckey);
                boolean[] chunkLight = ChunkLightCache.get(ckey);
                int[] chunkWaterTint = ChunkWaterTintCache.get(ckey);
                int[] chunkWaterDepth = ChunkWaterDepthCache.get(ckey);
                boolean[] chunkWaterOcean = ChunkWaterOceanCache.get(ckey);
                boolean[] chunkFoliage = ChunkFoliageCache.get(ckey);
                if (pixels != null) {
                    cacheHits++;
                } else {
                    PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(ckey) : null;
                    if (persisted != null) {
                        persistedHits++;
                        persisted.touch();
                        pixels = persisted.pixels();
                        chunkHeights = persisted.heights();
                        chunkWater = persisted.water();
                        chunkWaterTint = persisted.waterTint();
                        chunkWaterDepth = persisted.waterDepth();
                        chunkWaterOcean = persisted.waterOcean();
                        chunkFoliage = persisted.foliage();
                        ChunkColorCache.put(ckey, pixels);
                        ChunkHeightCache.put(ckey, chunkHeights);
                        ChunkWaterCache.put(ckey, chunkWater);
                        ChunkWaterTintCache.put(ckey, chunkWaterTint);
                        ChunkWaterDepthCache.put(ckey, chunkWaterDepth);
                        ChunkWaterOceanCache.put(ckey, chunkWaterOcean);
                        ChunkFoliageCache.put(ckey, chunkFoliage);
                    } else {
                        blankChunks++;
                    }
                }

                int tint = 0;
                boolean hasTint = false;
                if (SolarisConfig.SHOW_CLAIMS_MAP.get()) {
                    for (SolarisOverlay overlay : overlays) {
                        Optional<Integer> color = overlay.colorAt(key.dimension(), chunkX, chunkZ);
                        if (color.isPresent()) {
                            tint = color.get();
                            hasTint = true;
                        }
                    }
                }

                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int local = lz * 16 + lx;
                        int height = chunkHeights != null ? chunkHeights[local] : SolarisTexture.DEFAULT_HEIGHT;

                        int color = pixels != null ? pixels[local] : 0;
                        if (color == 0) {
                            int worldPx = chunkX * 16 + lx;
                            int worldPz = chunkZ * 16 + lz;
                            color = switch (unexploredStyle) {
                                case FOG -> fogColor;
                                case STARFIELD -> starfieldPixel(worldPx, worldPz, unexploredDensity,
                                        unexploredBrightness, starAccent, starDim, starSpace);
                                case PHOENIX -> phoenixPixel(worldPx, worldPz, unexploredDensity,
                                        unexploredBrightness);
                                case CLOUD -> cloudPixel(worldPx, worldPz, unexploredDensity, unexploredBrightness);
                            };
                        } else {
                            if (chunkWaterTint != null && chunkWaterTint[local] != 0) {
                                color = ChunkColorSampler.compositeWaterTint(chunkWaterTint[local],
                                        chunkWaterDepth[local], chunkWaterOcean[local], height);
                            }
                            if (saturation != 1.0) color = SolarisTexture.applySaturation(color, saturation);
                            if (contrast != 1.0) color = SolarisTexture.applyContrast(color, contrast);
                            if (brightness != 1.0) color = SolarisTexture.scaleBrightness(color, brightness);
                            if (chunkFoliage != null && chunkFoliage[local] && foliageBrightness != 1.0) {
                                color = SolarisTexture.scaleBrightness(color, foliageBrightness);
                            }
                            if (hasColorTint) {
                                color = SolarisTexture.scaleChannels(color, tintRed, tintGreen, tintBlue);
                            }
                        }
                        if (hasTint) color = SolarisTexture.blend(color, tint);

                        int px = baseX + lx;
                        int pz = baseZ + lz;
                        image.setPixelRGBA(px, pz, color);
                        int idx = haloIdx(px, pz);
                        heights[idx] = height;
                        water[idx] = chunkWater != null && chunkWater[local];
                        lightEmitting[pz * TILE_PIXELS + px] = chunkLight != null && chunkLight[local];
                        waterDepth[idx] = chunkWaterDepth != null ? chunkWaterDepth[local] : 0;
                    }
                }
            }
        }

        fillHalo(key, heights, water, waterDepth, persistedChunks);

        if (SolarisConfig.PERF_LOGGING.get() && blankChunks > 0) {
            PhoenixSolaris.LOGGER.info(
                    "[Solaris tile] {} chunkHits={} persistedHits={} blank={} (blank means neither cache " +
                            "nor disk had data for that chunk when this tile was built)",
                    key, cacheHits, persistedHits, blankChunks);
        }

        boolean anyWater = false;
        for (boolean w : water) {
            if (w) {
                anyWater = true;
                break;
            }
        }
        if (anyWater) {
            blurWater(image, water);
            applyWaterRelief(image, water, waterDepth);
        }
        if (SolarisConfig.HILLSHADING.get()) {
            applyHillshading(image, heights);
        }

        Level level = Minecraft.getInstance().level;
        if (level != null) {
            applyNightDarkening(image, lightEmitting, level);
        }
        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            applyBlackAndWhite(image);
        }

        tile.texture.bind();
        tile.texture.upload();

        tile.texture.setFilter(false, false);
    }

    private static void fillHalo(TileKey key, int[] heights, boolean[] water, int[] waterDepth,
                                 ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks) {
        int tileChunkMinX = key.tileX() * TILE_CHUNKS;
        int tileChunkMinZ = key.tileZ() * TILE_CHUNKS;

        for (int cz = 0; cz < TILE_CHUNKS; cz++) {
            int chunkZ = tileChunkMinZ + cz;
            fillHaloEdge(key.dimension(), tileChunkMinX - 1, chunkZ, 15, persistedChunks, heights, water, waterDepth,
                    true, -1, cz * 16);
            fillHaloEdge(key.dimension(), tileChunkMinX + TILE_CHUNKS, chunkZ, 0, persistedChunks, heights, water,
                    waterDepth, true, TILE_PIXELS, cz * 16);
        }
        for (int cx = 0; cx < TILE_CHUNKS; cx++) {
            int chunkX = tileChunkMinX + cx;
            fillHaloEdge(key.dimension(), chunkX, tileChunkMinZ - 1, 15, persistedChunks, heights, water, waterDepth,
                    false, cx * 16, -1);
            fillHaloEdge(key.dimension(), chunkX, tileChunkMinZ + TILE_CHUNKS, 0, persistedChunks, heights, water,
                    waterDepth, false, cx * 16, TILE_PIXELS);
        }
    }

    private static void fillHaloEdge(ResourceLocation dimension, int neighborChunkX, int neighborChunkZ,
                                     int neighborLocalEdge,
                                     ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks,
                                     int[] heights, boolean[] water, int[] waterDepth, boolean vertical, int haloX,
                                     int haloZ) {
        ChunkKey neighborKey = new ChunkKey(dimension, neighborChunkX, neighborChunkZ);
        int[] nHeights = ChunkHeightCache.get(neighborKey);
        boolean[] nWater = ChunkWaterCache.get(neighborKey);
        int[] nWaterDepth = ChunkWaterDepthCache.get(neighborKey);
        if (nHeights == null) {
            PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(neighborKey) : null;
            if (persisted != null) {
                nHeights = persisted.heights();
                nWater = persisted.water();
                nWaterDepth = persisted.waterDepth();
            }
        }

        for (int i = 0; i < 16; i++) {
            int x = vertical ? haloX : haloX + i;
            int z = vertical ? haloZ + i : haloZ;
            int idx = haloIdx(x, z);
            if (nHeights != null) {
                int local = vertical ? i * 16 + neighborLocalEdge : neighborLocalEdge * 16 + i;
                heights[idx] = nHeights[local];
                water[idx] = nWater != null && nWater[local];
                waterDepth[idx] = nWaterDepth != null ? nWaterDepth[local] : 0;
            } else {

                int fallbackX = vertical ? (haloX < 0 ? 0 : TILE_PIXELS - 1) : x;
                int fallbackZ = vertical ? z : (haloZ < 0 ? 0 : TILE_PIXELS - 1);
                int fallbackIdx = haloIdx(fallbackX, fallbackZ);
                heights[idx] = heights[fallbackIdx];
                water[idx] = water[fallbackIdx];
                waterDepth[idx] = waterDepth[fallbackIdx];
            }
        }
    }

    private static int clampIdx(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static void blurWater(NativeImage image, boolean[] water) {
        int radius = SolarisTexture.WATER_BLUR_RADIUS;
        int[] blurred = new int[TILE_PIXELS * TILE_PIXELS];
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (!water[haloIdx(x, z)]) continue;
                long sumR = 0;
                long sumG = 0;
                long sumB = 0;
                int count = 0;
                for (int dz = -radius; dz <= radius; dz++) {
                    int nz = clampIdx(z + dz, TILE_PIXELS - 1);
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = clampIdx(x + dx, TILE_PIXELS - 1);
                        if (!water[haloIdx(nx, nz)]) continue;
                        int abgr = image.getPixelRGBA(nx, nz);
                        sumR += FastColor.ABGR32.red(abgr);
                        sumG += FastColor.ABGR32.green(abgr);
                        sumB += FastColor.ABGR32.blue(abgr);
                        count++;
                    }
                }
                if (count > 0) {
                    blurred[z * TILE_PIXELS + x] = FastColor.ABGR32.color(255, (int) (sumB / count),
                            (int) (sumG / count), (int) (sumR / count));
                }
            }
        }
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (water[haloIdx(x, z)]) image.setPixelRGBA(x, z, blurred[z * TILE_PIXELS + x]);
            }
        }
    }

    private static void applyWaterRelief(NativeImage image, boolean[] water, int[] waterDepth) {
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int idx = haloIdx(x, z);
                if (!water[idx]) continue;

                int idxWest = haloIdx(x - 1, z);
                int idxEast = haloIdx(x + 1, z);
                int idxNorth = haloIdx(x, z - 1);
                int idxSouth = haloIdx(x, z + 1);

                int depthHere = waterDepth[idx];
                int depthWest = water[idxWest] ? waterDepth[idxWest] : depthHere;
                int depthEast = water[idxEast] ? waterDepth[idxEast] : depthHere;
                int depthNorth = water[idxNorth] ? waterDepth[idxNorth] : depthHere;
                int depthSouth = water[idxSouth] ? waterDepth[idxSouth] : depthHere;

                float dzdx = -(depthEast - depthWest) * 0.5f * SolarisTexture.WATER_RELIEF_NEIGHBOR_SCALE;
                float dzdy = -(depthSouth - depthNorth) * 0.5f * SolarisTexture.WATER_RELIEF_NEIGHBOR_SCALE;
                float factor = shadeFactor(dzdx, dzdy, SolarisTexture.WATER_RELIEF_GAIN);
                factor = Mth.clamp(factor, 1f - SolarisTexture.WATER_RELIEF_CLAMP,
                        1f + SolarisTexture.WATER_RELIEF_CLAMP);
                if (factor == 1f) continue;

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, SolarisTexture.scaleBrightness(abgr, factor));
            }
        }
    }

    private static void applyHillshading(NativeImage image, int[] heights) {
        double strength = SolarisConfig.HILLSHADING_STRENGTH.get();
        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                float dzdx = (heights[haloIdx(x + 1, z)] - heights[haloIdx(x - 1, z)]) * 0.5f;
                float dzdy = (heights[haloIdx(x, z + 1)] - heights[haloIdx(x, z - 1)]) * 0.5f;
                float factor = shadeFactor(dzdx, dzdy, (float) strength * SolarisTexture.HILLSHADE_GAIN);
                factor = Mth.clamp(factor, 0.3f, 1.8f);

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, SolarisTexture.scaleBrightness(abgr, factor));
            }
        }
    }

    private static float shadeFactor(float dzdx, float dzdy, float gain) {
        float nx = -dzdx;
        float ny = -dzdy;
        float nz = 1f;
        float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float shade = (nx * SolarisTexture.LIGHT_X + ny * SolarisTexture.LIGHT_Y + nz * SolarisTexture.LIGHT_Z) /
                (nLen * SolarisTexture.LIGHT_LEN);
        return 1f + gain * (shade - SolarisTexture.FLAT_SHADE);
    }

    private static void applyNightDarkening(NativeImage image, boolean[] lightEmitting, Level level) {
        double factor = ChunkColorSampler.nightFactor(level.getDayTime());
        if (factor <= 0.0) return;

        double strength = SolarisConfig.NIGHT_MODE_STRENGTH.get();
        double multiplier = Mth.lerp(factor, 1.0, 1.0 - strength);

        for (int z = 0; z < TILE_PIXELS; z++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int idx = z * TILE_PIXELS + x;
                if (lightEmitting[idx]) continue;
                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, SolarisTexture.scaleBrightness(abgr, multiplier));
            }
        }
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
