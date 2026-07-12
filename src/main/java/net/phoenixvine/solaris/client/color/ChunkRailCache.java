package net.phoenixvine.solaris.client.color;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, LRU-evicted cache of which columns in a chunk have a rail block at the surface —
 * paired with {@link ChunkHeightCache}/{@link ChunkWaterCache}, populated from the same {@code
 * ChunkColorSampler.sampleHeights} pass. Feeds {@code SolarisMapScreen}'s connected-rail-line
 * overlay rather than {@code ChunkColorSampler}'s own per-pixel color output — a raster dot per
 * rail block reads as noisy scattered pixels at typical zoom, not a railway; a vector line drawn
 * between adjacent rail columns reads the way a real transit-map line does, and stays clean
 * regardless of zoom level the way raster pixels don't.
 */
public final class ChunkRailCache {

    private ChunkRailCache() {}

    private static final Map<ChunkKey, boolean[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkKey, boolean[]> eldest) {
                    return size() > SolarisConfig.MAX_CACHED_CHUNKS.get();
                }
            });

    public static void put(ChunkKey key, boolean[] rails) {
        CACHE.put(key, rails);
    }

    public static boolean[] get(ChunkKey key) {
        return CACHE.get(key);
    }

    /** Flat world-space lookup — resolves the owning chunk and column itself, for the connected-rail-line overlay. */
    public static boolean isRailAt(ResourceLocation dimension, int worldX, int worldZ) {
        boolean[] rails = get(new ChunkKey(dimension, worldX >> 4, worldZ >> 4));
        if (rails == null) return false;
        return rails[(worldZ & 15) * 16 + (worldX & 15)];
    }

    public static void clear() {
        CACHE.clear();
    }
}
