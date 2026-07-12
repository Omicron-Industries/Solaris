package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory waypoint list, lazily (re)loaded whenever the current world/server changes —
 * waypoints are meaningless outside the save they were placed in, so each world gets its
 * own JSON file under {@code config/phoenix_solaris/waypoints/}, keyed by server address
 * (multiplayer) or save folder path (singleplayer). Also holds the per-world set of hidden
 * categories and the currently-tracked waypoint (for {@code WaypointCompassOverlay}) —
 * both saved alongside the waypoint list itself rather than as separate state, since neither
 * means anything outside the same per-world scope the waypoints themselves are keyed by.
 */
public final class WaypointManager {

    private WaypointManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static final Set<String> HIDDEN_CATEGORIES = new TreeSet<>();
    private static String trackedId = null;
    private static String loadedWorldKey = null;

    /**
     * The on-disk shape — a thin wrapper so hidden categories/tracked waypoint travel with the waypoints
     * themselves instead of needing their own file.
     */
    private static final class SaveData {

        List<Waypoint> waypoints = new ArrayList<>();
        List<String> hiddenCategories = new ArrayList<>();
        String trackedId;
    }

    public static List<Waypoint> getAll() {
        ensureLoaded();
        return WAYPOINTS;
    }

    /**
     * The single funnel every waypoint-consuming renderer (the list screen, the HUD compass, the
     * in-world beams, the minimap markers) already reads through — {@link
     * SolarisAPI#FEATURE_WAYPOINTS} is enforced here once, at {@code VISIBLE} (the "can see
     * existing waypoints at all" threshold — see {@link #canPlace} for the higher, "can create
     * new ones" threshold), rather than at each of those call sites separately.
     */
    public static List<Waypoint> getVisibleForDimension(ResourceLocation dimension) {
        ensureLoaded();
        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WAYPOINTS, dimension).atLeast(SolarisFeatureState.VISIBLE)) {
            return List.of();
        }

        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : WAYPOINTS) {
            if (w.visible && w.dimension.equals(dimension.toString()) &&
                    !HIDDEN_CATEGORIES.contains(w.categoryOrEmpty())) {
                out.add(w);
            }
        }
        return out;
    }

    /** Whether a new waypoint may currently be placed in {@code dimension} — the {@code ENABLED} threshold. */
    public static boolean canPlace(ResourceLocation dimension) {
        return SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WAYPOINTS, dimension).atLeast(SolarisFeatureState.ENABLED);
    }

    public static void add(Waypoint waypoint) {
        ensureLoaded();
        WAYPOINTS.add(waypoint);
        save();
    }

    public static void remove(String id) {
        ensureLoaded();
        WAYPOINTS.removeIf(w -> w.id.equals(id));
        if (id.equals(trackedId)) trackedId = null;
        save();
    }

    /**
     * Removes by name rather than id — used by server-triggered removal ({@code
     * SolarisServerAPI#removeWaypoint}), since the server never tracked the client-generated
     * {@link Waypoint#id}. Locked waypoints are still removable this way (a server/admin action is
     * a distinct, higher-privilege path than the player's own Delete button, which is what {@code
     * Waypoint#locked} actually restricts).
     */
    public static void removeByName(String name) {
        ensureLoaded();
        WAYPOINTS.removeIf(w -> {
            boolean match = w.name.equals(name);
            if (match && w.id.equals(trackedId)) trackedId = null;
            return match;
        });
        save();
    }

    /** Every distinct category currently in use, sorted, "" (uncategorized) always included. */
    public static List<String> getCategories() {
        ensureLoaded();
        Set<String> categories = new TreeSet<>();
        categories.add("");
        for (Waypoint w : WAYPOINTS) categories.add(w.categoryOrEmpty());
        return new ArrayList<>(categories);
    }

    public static boolean isCategoryHidden(String category) {
        ensureLoaded();
        return HIDDEN_CATEGORIES.contains(category);
    }

    public static void setCategoryHidden(String category, boolean hidden) {
        ensureLoaded();
        if (hidden) HIDDEN_CATEGORIES.add(category);
        else HIDDEN_CATEGORIES.remove(category);
        save();
    }

    /** The waypoint {@code WaypointCompassOverlay} should point at, or {@code null} for "nearest, automatically". */
    public static Waypoint getTracked() {
        ensureLoaded();
        if (trackedId == null) return null;
        for (Waypoint w : WAYPOINTS) {
            if (w.id.equals(trackedId)) return w;
        }
        return null;
    }

    public static void setTracked(String id) {
        ensureLoaded();
        trackedId = id;
        save();
    }

    public static void save() {
        String key = currentWorldKey();
        if (key == null) return;
        try {
            SaveData data = new SaveData();
            data.waypoints = WAYPOINTS;
            data.hiddenCategories = new ArrayList<>(HIDDEN_CATEGORIES);
            data.trackedId = trackedId;

            Path file = fileFor(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureLoaded() {
        String key = currentWorldKey();
        if (key == null || key.equals(loadedWorldKey)) return;

        WAYPOINTS.clear();
        HIDDEN_CATEGORIES.clear();
        trackedId = null;
        try {
            Path file = fileFor(key);
            if (Files.exists(file)) {
                String json = Files.readString(file);
                try {
                    SaveData data = GSON.fromJson(json, SaveData.class);
                    if (data != null) {
                        if (data.waypoints != null) WAYPOINTS.addAll(data.waypoints);
                        if (data.hiddenCategories != null) HIDDEN_CATEGORIES.addAll(data.hiddenCategories);
                        trackedId = data.trackedId;
                    }
                } catch (JsonSyntaxException e) {
                    // Pre-category-support files were a raw waypoint array, not this wrapper
                    // object — fall back to the old shape instead of silently losing them.
                    Type type = new TypeToken<List<Waypoint>>() {}.getType();
                    List<Waypoint> legacy = GSON.fromJson(json, type);
                    if (legacy != null) WAYPOINTS.addAll(legacy);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadedWorldKey = key;
    }

    private static Path fileFor(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get("config", "phoenix_solaris", "waypoints", safe + ".json");
    }

    /** Null when there's no current world to key against (e.g. on the title screen). */
    public static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        ServerData server = mc.getCurrentServer();
        if (server != null) return "mp-" + server.ip;

        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return "sp-" + root.toAbsolutePath().normalize();
        }

        return "unknown";
    }
}
