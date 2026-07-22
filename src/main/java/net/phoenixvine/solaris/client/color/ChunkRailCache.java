package net.phoenixvine.solaris.client.color;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public static boolean isRailAt(ResourceLocation dimension, int worldX, int worldZ) {
        boolean[] rails = get(new ChunkKey(dimension, worldX >> 4, worldZ >> 4));
        if (rails == null) return false;
        return rails[(worldZ & 15) * 16 + (worldX & 15)];
    }

    public static void clear() {
        CACHE.clear();
    }
}
