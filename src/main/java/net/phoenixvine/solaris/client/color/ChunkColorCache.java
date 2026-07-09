package net.phoenixvine.solaris.client.color;

import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, LRU-evicted cache of sampled chunk pixel arrays. In-memory only for v1 — a
 * chunk simply re-samples the next time it (re)loads; nothing is written to disk.
 */
public final class ChunkColorCache {

    private ChunkColorCache() {}

    private static final Map<ChunkKey, int[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkKey, int[]> eldest) {
                    return size() > SolarisConfig.MAX_CACHED_CHUNKS.get();
                }
            });

    public static void put(ChunkKey key, int[] pixels) {
        CACHE.put(key, pixels);
    }

    public static int[] get(ChunkKey key) {
        return CACHE.get(key);
    }

    public static boolean contains(ChunkKey key) {
        return CACHE.containsKey(key);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }
}
