package net.phoenixvine.solaris.client.plan;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class PlanShapeManager {

    private PlanShapeManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<PlanShape> SHAPES = new ArrayList<>();
    private static String loadedWorldKey = null;

    public static List<PlanShape> getAll() {
        ensureLoaded();
        return SHAPES;
    }

    public static List<PlanShape> getVisibleForDimension(ResourceLocation dimension) {
        ensureLoaded();
        List<PlanShape> out = new ArrayList<>();
        for (PlanShape s : SHAPES) {
            if (s.visible && s.dimension.equals(dimension.toString())) out.add(s);
        }
        return out;
    }

    public static void add(PlanShape shape) {
        ensureLoaded();
        SHAPES.add(shape);
        save();
    }

    public static void remove(String id) {
        ensureLoaded();
        SHAPES.removeIf(s -> s.id.equals(id));
        save();
    }

    public static void save() {
        String key = WaypointManager.currentWorldKey();
        if (key == null) return;
        try {
            Path file = fileFor(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(SHAPES));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureLoaded() {
        String key = WaypointManager.currentWorldKey();
        if (key == null || key.equals(loadedWorldKey)) return;

        SHAPES.clear();
        try {
            Path file = fileFor(key);
            if (Files.exists(file)) {
                String json = Files.readString(file);
                Type type = new TypeToken<List<PlanShape>>() {}.getType();
                List<PlanShape> loaded = GSON.fromJson(json, type);
                if (loaded != null) SHAPES.addAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadedWorldKey = key;
    }

    private static Path fileFor(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get("config", "solaris", "shapes", safe + ".json");
    }
}
