package net.phoenixvine.solaris.client.color;

import net.minecraft.Util;
import net.phoenixvine.solaris.PhoenixSolaris;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persists explored cave-slice imagery per chunk per Y-bucket, mirroring {@link PersistentChunkStore}
 * so underground map mode can remember what you've already seen instead of showing flat fog for
 * anything outside the small live-reveal radius around the player.
 */
public final class PersistentCaveStore {

    private static final int MAGIC = 0x534F4C42;
    private static final int VERSION = 1;

    public static final int Y_BUCKET_SIZE = 4;

    private PersistentCaveStore() {}

    public record CaveKey(String dimension, int x, int z, int yBucket) {}

    private static final class Entry {

        final int[] pixels;
        volatile long lastAccessMillis;

        Entry(int[] pixels, long lastAccessMillis) {
            this.pixels = pixels;
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    private static final Map<String, ConcurrentHashMap<CaveKey, Entry>> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> DIRTY_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static String loadedWorldKey = null;

    public static int yBucket(int y) {
        return Math.floorDiv(y, Y_BUCKET_SIZE);
    }

    public static int[] get(String dimension, int x, int z, int yBucket) {
        ensureLoading(dimension);
        ConcurrentHashMap<CaveKey, Entry> chunks = DIMENSIONS.get(dimension);
        if (chunks == null) return null;
        Entry entry = chunks.get(new CaveKey(dimension, x, z, yBucket));
        if (entry == null) return null;
        entry.lastAccessMillis = System.currentTimeMillis();
        return entry.pixels;
    }

    public static void put(String dimension, int x, int z, int yBucket, int[] pixels) {
        ensureLoading(dimension);
        ConcurrentHashMap<CaveKey, Entry> chunks = DIMENSIONS.computeIfAbsent(dimension,
                d -> new ConcurrentHashMap<>());
        chunks.put(new CaveKey(dimension, x, z, yBucket), new Entry(pixels, System.currentTimeMillis()));
        DIRTY_DIMENSIONS.add(dimension);
    }

    public static void saveDirtyAsync() {
        String worldKey = WaypointManager.currentWorldKey();
        if (worldKey == null || DIRTY_DIMENSIONS.isEmpty()) return;

        List<String> toSave = new ArrayList<>(DIRTY_DIMENSIONS);
        DIRTY_DIMENSIONS.removeAll(toSave);
        for (String dimension : toSave) {
            ConcurrentHashMap<CaveKey, Entry> chunks = DIMENSIONS.get(dimension);
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
            ConcurrentHashMap<CaveKey, Entry> loaded = readDimension(worldKey, dimension);
            DIMENSIONS.put(dimension, loaded);
            LOADING.remove(dimension);
        });
    }

    private static ConcurrentHashMap<CaveKey, Entry> readDimension(String worldKey, String dimension) {
        ConcurrentHashMap<CaveKey, Entry> loaded = new ConcurrentHashMap<>();
        Path file = fileFor(worldKey, dimension);
        if (!Files.exists(file)) return loaded;

        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                PhoenixSolaris.LOGGER.warn(
                        "[Solaris] Discarding persisted cave data for {} — found format version {} on disk, " +
                                "expected {} (magic {})",
                        dimension, version, VERSION, magic == MAGIC ? "ok" : "MISMATCH");
                return loaded;
            }

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int z = in.readInt();
                int yBucket = in.readInt();
                long lastAccess = in.readLong();
                int[] pixels = new int[256];
                for (int p = 0; p < 256; p++) pixels[p] = in.readInt();
                loaded.put(new CaveKey(dimension, x, z, yBucket), new Entry(pixels, lastAccess));
            }
            PhoenixSolaris.LOGGER.info("[Solaris] Loaded {} persisted cave slice(s) for {} from disk", loaded.size(),
                    dimension);
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to load Solaris persisted cave data for {}", dimension, e);
        }
        return loaded;
    }

    private static void writeDimension(String worldKey, String dimension, Map<CaveKey, Entry> chunks) {
        List<Map.Entry<CaveKey, Entry>> entries = new ArrayList<>(chunks.entrySet());

        int cap = SolarisConfig.MAX_PERSISTED_CHUNKS_PER_DIMENSION.get() * 4;
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
                for (Map.Entry<CaveKey, Entry> e : entries) {
                    CaveKey key = e.getKey();
                    Entry entry = e.getValue();
                    out.writeInt(key.x());
                    out.writeInt(key.z());
                    out.writeInt(key.yBucket());
                    out.writeLong(entry.lastAccessMillis);
                    for (int p : entry.pixels) out.writeInt(p);
                }
            }
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            PhoenixSolaris.LOGGER.info("[Solaris] Saved {} persisted cave slice(s) for {} to disk ({} in memory)",
                    entries.size(), dimension, chunks.size());
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to save Solaris persisted cave data for {}", dimension, e);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    private static Path fileFor(String worldKey, String dimension) {
        String safeWorld = worldKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeDimension = dimension.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get("config", "solaris", "mapdata", safeWorld, safeDimension + "_caves.dat");
    }
}
