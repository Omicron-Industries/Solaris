package net.phoenixvine.solaris.client.color;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PersistentChunkStore {

    private static final int MAGIC = 0x534F4C41;

    private static final int VERSION = 4;

    private PersistentChunkStore() {}

    public static final class Entry {

        final int[] pixels;
        final int[] heights;
        final boolean[] water;
        final int[] waterTint;
        final int[] waterDepth;
        final boolean[] waterOcean;
        final boolean[] foliage;
        volatile long lastAccessMillis;

        Entry(int[] pixels, int[] heights, boolean[] water, int[] waterTint, int[] waterDepth,
              boolean[] waterOcean, boolean[] foliage, long lastAccessMillis) {
            this.pixels = pixels;
            this.heights = heights;
            this.water = water;
            this.waterTint = waterTint;
            this.waterDepth = waterDepth;
            this.waterOcean = waterOcean;
            this.foliage = foliage;
            this.lastAccessMillis = lastAccessMillis;
        }

        public int[] pixels() {
            return pixels;
        }

        public int[] heights() {
            return heights;
        }

        public boolean[] water() {
            return water;
        }

        public int[] waterTint() {
            return waterTint;
        }

        public int[] waterDepth() {
            return waterDepth;
        }

        public boolean[] waterOcean() {
            return waterOcean;
        }

        public boolean[] foliage() {
            return foliage;
        }

        public void touch() {
            lastAccessMillis = System.currentTimeMillis();
        }
    }

    private static final Map<String, ConcurrentHashMap<ChunkKey, Entry>> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> DIRTY_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static String loadedWorldKey = null;

    public static Entry get(ChunkKey key) {
        ConcurrentHashMap<ChunkKey, Entry> chunks = chunksFor(key.dimension().toString());
        if (chunks == null) return null;
        Entry entry = chunks.get(key);
        if (entry != null) entry.lastAccessMillis = System.currentTimeMillis();
        return entry;
    }

    public static ConcurrentHashMap<ChunkKey, Entry> chunksFor(String dimension) {
        ensureLoading(dimension);
        return DIMENSIONS.get(dimension);
    }

    public static Map<ChunkKey, Entry> snapshot(String dimension) {
        ensureLoading(dimension);
        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.get(dimension);
        return chunks == null ? Map.of() : Map.copyOf(chunks);
    }

    public static boolean isLoaded(String dimension) {
        ensureLoading(dimension);
        return DIMENSIONS.containsKey(dimension);
    }

    public static void put(ChunkKey key, int[] pixels, int[] heights, boolean[] water, int[] waterTint,
                           int[] waterDepth, boolean[] waterOcean, boolean[] foliage) {
        String dimension = key.dimension().toString();
        ensureLoading(dimension);

        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.computeIfAbsent(dimension,
                d -> new ConcurrentHashMap<>());
        chunks.put(key, new Entry(pixels, heights, water, waterTint, waterDepth, waterOcean, foliage,
                System.currentTimeMillis()));
        DIRTY_DIMENSIONS.add(dimension);
    }

    public static void saveDirtyAsync() {
        String worldKey = WaypointManager.currentWorldKey();
        if (worldKey == null || DIRTY_DIMENSIONS.isEmpty()) return;

        List<String> toSave = new ArrayList<>(DIRTY_DIMENSIONS);
        DIRTY_DIMENSIONS.removeAll(toSave);
        for (String dimension : toSave) {
            ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.get(dimension);
            if (chunks == null) continue;
            Util.ioPool().execute(() -> writeDimension(worldKey, dimension, chunks));
        }
    }

    private static void ensureLoading(String dimension) {
        String worldKey = WaypointManager.currentWorldKey();
        if (worldKey == null) return;

        if (!worldKey.equals(loadedWorldKey)) {
            DIMENSIONS.clear();
            LOADING.clear();
            DIRTY_DIMENSIONS.clear();
            loadedWorldKey = worldKey;
        }

        if (DIMENSIONS.containsKey(dimension) || !LOADING.add(dimension)) return;

        Util.ioPool().execute(() -> {
            ConcurrentHashMap<ChunkKey, Entry> loaded = readDimension(worldKey, dimension);
            DIMENSIONS.put(dimension, loaded);
            LOADING.remove(dimension);
            if (!loaded.isEmpty()) SolarisTexture.invalidateAll();
        });
    }

    private static ConcurrentHashMap<ChunkKey, Entry> readDimension(String worldKey, String dimension) {
        ConcurrentHashMap<ChunkKey, Entry> loaded = new ConcurrentHashMap<>();
        Path file = fileFor(worldKey, dimension);
        if (!Files.exists(file)) return loaded;

        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                PhoenixSolaris.LOGGER.warn(
                        "[Solaris] Discarding persisted map data for {} — found format version {} on disk, " +
                                "expected {} (magic {})",
                        dimension, version, VERSION, magic == MAGIC ? "ok" : "MISMATCH");
                return loaded;
            }

            int count = in.readInt();
            ResourceLocation dimLoc = new ResourceLocation(dimension);
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int z = in.readInt();
                long lastAccess = in.readLong();

                int[] pixels = new int[256];
                for (int p = 0; p < 256; p++) pixels[p] = in.readInt();
                int[] heights = new int[256];
                for (int p = 0; p < 256; p++) heights[p] = in.readInt();
                boolean[] water = new boolean[256];
                for (int p = 0; p < 256; p += 8) {
                    int packed = in.readUnsignedByte();
                    for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                        water[p + bit] = (packed & (1 << bit)) != 0;
                    }
                }
                int[] waterTint = new int[256];
                for (int p = 0; p < 256; p++) waterTint[p] = in.readInt();
                int[] waterDepth = new int[256];
                for (int p = 0; p < 256; p++) waterDepth[p] = in.readInt();
                boolean[] waterOcean = new boolean[256];
                for (int p = 0; p < 256; p += 8) {
                    int packed = in.readUnsignedByte();
                    for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                        waterOcean[p + bit] = (packed & (1 << bit)) != 0;
                    }
                }
                boolean[] foliage = new boolean[256];
                for (int p = 0; p < 256; p += 8) {
                    int packed = in.readUnsignedByte();
                    for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                        foliage[p + bit] = (packed & (1 << bit)) != 0;
                    }
                }
                loaded.put(new ChunkKey(dimLoc, x, z),
                        new Entry(pixels, heights, water, waterTint, waterDepth, waterOcean, foliage, lastAccess));
            }
            PhoenixSolaris.LOGGER.info("[Solaris] Loaded {} persisted chunk(s) for {} from disk", loaded.size(),
                    dimension);
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to load Solaris persisted map data for {}", dimension, e);
        }
        return loaded;
    }

    private static void writeDimension(String worldKey, String dimension, Map<ChunkKey, Entry> chunks) {
        List<Map.Entry<ChunkKey, Entry>> entries = new ArrayList<>(chunks.entrySet());

        int cap = SolarisConfig.MAX_PERSISTED_CHUNKS_PER_DIMENSION.get();
        if (entries.size() > cap) {
            entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccessMillis));
            int toEvict = entries.size() - cap;
            entries = entries.subList(toEvict, entries.size());
        }

        Path file = fileFor(worldKey, dimension);
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(entries.size());
                for (Map.Entry<ChunkKey, Entry> e : entries) {
                    ChunkKey key = e.getKey();
                    Entry entry = e.getValue();
                    out.writeInt(key.x());
                    out.writeInt(key.z());
                    out.writeLong(entry.lastAccessMillis);
                    for (int p : entry.pixels) out.writeInt(p);
                    for (int h : entry.heights) out.writeInt(h);
                    for (int p = 0; p < 256; p += 8) {
                        int packed = 0;
                        for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                            if (entry.water[p + bit]) packed |= 1 << bit;
                        }
                        out.writeByte(packed);
                    }
                    for (int t : entry.waterTint) out.writeInt(t);
                    for (int d : entry.waterDepth) out.writeInt(d);
                    for (int p = 0; p < 256; p += 8) {
                        int packed = 0;
                        for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                            if (entry.waterOcean[p + bit]) packed |= 1 << bit;
                        }
                        out.writeByte(packed);
                    }
                    for (int p = 0; p < 256; p += 8) {
                        int packed = 0;
                        for (int bit = 0; bit < 8 && p + bit < 256; bit++) {
                            if (entry.foliage[p + bit]) packed |= 1 << bit;
                        }
                        out.writeByte(packed);
                    }
                }
            }

            Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            PhoenixSolaris.LOGGER.info("[Solaris] Saved {} persisted chunk(s) for {} to disk ({} in memory)",
                    entries.size(), dimension, chunks.size());
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to save Solaris persisted map data for {}", dimension, e);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    private static Path fileFor(String worldKey, String dimension) {
        String safeWorld = worldKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeDimension = dimension.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get("config", "solaris", "mapdata", safeWorld, safeDimension + ".dat");
    }
}
