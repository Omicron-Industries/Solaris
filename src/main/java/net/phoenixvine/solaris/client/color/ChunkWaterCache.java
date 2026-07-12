package net.phoenixvine.solaris.client.color;

import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, LRU-evicted cache of which columns in a chunk are water at the surface — paired with
 * {@link ChunkHeightCache}, populated from the same {@code ChunkColorSampler.sampleHeights} pass.
 * Kept separate from the height cache (rather than merged) since callers needing height data
 * (globe relief displacement) don't all need the water flag too.
 */
public final class ChunkWaterCache {

    private ChunkWaterCache() {}

    private static final Map<ChunkKey, boolean[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkKey, boolean[]> eldest) {
                    return size() > SolarisConfig.MAX_CACHED_CHUNKS.get();
                }
            });

    public static void put(ChunkKey key, boolean[] water) {
        CACHE.put(key, water);
    }

    public static boolean[] get(ChunkKey key) {
        return CACHE.get(key);
    }

    public static void clear() {
        CACHE.clear();
    }
}
