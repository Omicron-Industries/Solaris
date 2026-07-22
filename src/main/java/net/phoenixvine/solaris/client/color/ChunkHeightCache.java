package net.phoenixvine.solaris.client.color;

import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkHeightCache {

    private ChunkHeightCache() {}

    private static final Map<ChunkKey, int[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkKey, int[]> eldest) {
                    return size() > SolarisConfig.MAX_CACHED_CHUNKS.get();
                }
            });

    public static void put(ChunkKey key, int[] heights) {
        CACHE.put(key, heights);
    }

    public static int[] get(ChunkKey key) {
        return CACHE.get(key);
    }

    public static void clear() {
        CACHE.clear();
    }
}
