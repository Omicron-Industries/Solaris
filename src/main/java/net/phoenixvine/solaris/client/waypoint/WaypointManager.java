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

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class WaypointManager {

    private WaypointManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static final Set<String> HIDDEN_CATEGORIES = new TreeSet<>();
    private static String trackedId = null;
    private static String loadedWorldKey = null;

    private static final class SaveData {

        List<Waypoint> waypoints = new ArrayList<>();
        List<String> hiddenCategories = new ArrayList<>();
        String trackedId;
    }

    public static List<Waypoint> getAll() {
        ensureLoaded();
        return WAYPOINTS;
    }

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

    public static void removeByName(String name) {
        ensureLoaded();
        WAYPOINTS.removeIf(w -> {
            boolean match = w.name.equals(name);
            if (match && w.id.equals(trackedId)) trackedId = null;
            return match;
        });
        save();
    }

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
        return Paths.get("config", "solaris", "waypoints", safe + ".json");
    }

    public static Path currentWaypointsFile() {
        String key = currentWorldKey();
        return key == null ? null : fileFor(key);
    }

    private static String cachedWorldKey = null;
    private static boolean cachedWorldKeyValid = false;

    public static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            cachedWorldKeyValid = false;
            return null;
        }
        if (cachedWorldKeyValid) return cachedWorldKey;

        ServerData server = mc.getCurrentServer();
        String key;
        if (server != null) {
            key = "mp-" + server.ip;
        } else if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            key = "sp-" + singleplayerWorldId(root);
        } else {
            key = "unknown";
        }

        cachedWorldKey = key;
        cachedWorldKeyValid = true;
        return key;
    }

    private static final String WORLD_ID_FILE = "solaris_world_id.txt";

    private static String singleplayerWorldId(Path worldRoot) {
        Path idFile = worldRoot.resolve(WORLD_ID_FILE);
        try {
            if (Files.exists(idFile)) {
                String existing = Files.readString(idFile).trim();
                if (!existing.isEmpty()) return existing;
            }
            String fresh = UUID.randomUUID().toString();
            Files.createDirectories(worldRoot);
            Files.writeString(idFile, fresh);
            return fresh;
        } catch (IOException e) {

            return worldRoot.toAbsolutePath().normalize().toString();
        }
    }
}
