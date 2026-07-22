package net.phoenixvine.solaris.client.color;

import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkFoliageCache {

    private ChunkFoliageCache() {}

    private static final Map<ChunkKey, boolean[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkKey, boolean[]> eldest) {
                    return size() > SolarisConfig.MAX_CACHED_CHUNKS.get();
                }
            });

    public static void put(ChunkKey key, boolean[] foliage) {
        CACHE.put(key, foliage);
    }

    public static boolean[] get(ChunkKey key) {
        return CACHE.get(key);
    }

    public static void clear() {
        CACHE.clear();
    }
}
