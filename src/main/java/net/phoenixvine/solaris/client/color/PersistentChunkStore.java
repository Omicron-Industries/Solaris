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

/**
 * Remembers sampled chunk color/height/water data to disk, per world+dimension, so already
 * -explored areas keep showing on the map after they fall out of {@link ChunkColorCache}'s
 * in-memory LRU bound or the player leaves render distance and comes back next session — the
 * "gray far-away chunks" report earlier turned out to be exactly that gap (nothing remembered
 * explored terrain beyond the in-memory working set at all).
 *
 * Deliberately <b>not</b> modeled on how map mods like JourneyMap persist explored area — that
 * approach (one growing image tile per region, kept forever) is the specific thing that balloons
 * on a long-lived world. Instead: a compact binary format (four packed ints per pixel column, not
 * an image), gzip-compressed, one file per dimension, and — the actual anti-ballooning
 * mechanism — a hard cap ({@link SolarisConfig#MAX_PERSISTED_CHUNKS_PER_DIMENSION}) enforced on
 * every save by evicting the least-recently-touched chunks first, same LRU idea already used for
 * the in-memory cache. Total disk usage per dimension is therefore a known, fixed bound, not an
 * ever-growing one.
 *
 * Loading is fully asynchronous and never blocks the render thread: the first {@link #get} for a
 * dimension this session kicks off a background read and returns {@code null} (same as a cache
 * miss) until it completes, at which point {@link SolarisTexture#invalidateAll()} forces open
 * maps to redraw with the newly-available data. Saving is periodic (piggybacked on {@link
 * LiveChunkRefresh}'s tick) and also runs entirely off the render thread.
 */
public final class PersistentChunkStore {

    private static final int MAGIC = 0x534F4C41; // "SOLA"
    private static final int VERSION = 1;

    private PersistentChunkStore() {}

    public static final class Entry {

        final int[] pixels;
        final int[] heights;
        final boolean[] water;
        volatile long lastAccessMillis;

        Entry(int[] pixels, int[] heights, boolean[] water, long lastAccessMillis) {
            this.pixels = pixels;
            this.heights = heights;
            this.water = water;
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
    }

    private static final Map<String, ConcurrentHashMap<ChunkKey, Entry>> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> DIRTY_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static String loadedWorldKey = null;

    /** Returns the persisted entry for {@code key}, or {@code null} if never persisted (or still loading). */
    public static Entry get(ChunkKey key) {
        String dimension = key.dimension().toString();
        ensureLoading(dimension);

        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.get(dimension);
        if (chunks == null) return null;
        Entry entry = chunks.get(key);
        if (entry != null) entry.lastAccessMillis = System.currentTimeMillis();
        return entry;
    }

    /** Records a freshly-sampled chunk. Cheap — a plain map write, no I/O on this thread. */
    public static void put(ChunkKey key, int[] pixels, int[] heights, boolean[] water) {
        String dimension = key.dimension().toString();
        ensureLoading(dimension);

        ConcurrentHashMap<ChunkKey, Entry> chunks = DIMENSIONS.computeIfAbsent(dimension,
                d -> new ConcurrentHashMap<>());
        chunks.put(key, new Entry(pixels, heights, water, System.currentTimeMillis()));
        DIRTY_DIMENSIONS.add(dimension);
    }

    /**
     * Kicks off an async save for every dimension with unsaved changes. Safe to call often — a no-op when nothing's
     * dirty.
     */
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

    /**
     * A new world/server means a completely different set of chunks means nothing here is valid
     * anymore — clears in-memory state so the next {@link #get}/{@link #put} lazily reloads (or
     * starts fresh) for wherever the player just went, mirroring {@code WaypointManager}'s own
     * per-world reset.
     */
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
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return loaded;

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
                loaded.put(new ChunkKey(dimLoc, x, z), new Entry(pixels, heights, water, lastAccess));
            }
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
            for (int i = 0; i < toEvict; i++) chunks.remove(entries.get(i).getKey());
            entries = entries.subList(toEvict, entries.size());
        }

        Path file = fileFor(worldKey, dimension);
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file))))) {
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
                }
            }
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.warn("Failed to save Solaris persisted map data for {}", dimension, e);
        }
    }

    private static Path fileFor(String worldKey, String dimension) {
        String safeWorld = worldKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeDimension = dimension.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get("config", "phoenix_solaris", "mapdata", safeWorld, safeDimension + ".dat");
    }
}
