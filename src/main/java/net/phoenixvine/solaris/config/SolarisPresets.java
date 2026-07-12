package net.phoenixvine.solaris.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named snapshots of the Display tab's tunables — saturation, water opacity/deep-only/threshold,
 * waypoint icon scale, hillshading + its strength — so a look you've dialed in can be saved and
 * swapped back to later instead of re-tweaking every slider by hand. Global (one file, not
 * per-world) since none of these settings are world-specific to begin with.
 *
 * Deliberately doesn't touch {@code SolarisTexture.invalidateAll()} itself — that's a rendering
 * concern the UI layer (which already imports the render package) triggers after {@link #load},
 * keeping this class a plain config-values store.
 */
public final class SolarisPresets {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Paths.get("config", "phoenix_solaris", "presets.json");

    private static Map<String, Preset> presets;

    private SolarisPresets() {}

    /** The on-disk shape for one saved preset. */
    private static final class Preset {

        double saturation;
        double waterOpacity;
        boolean waterDeepOnly;
        int waterDeepYThreshold;
        double waypointIconScale;
        boolean hillshading;
        double hillshadingStrength;
    }

    public static List<String> names() {
        ensureLoaded();
        return new ArrayList<>(presets.keySet());
    }

    public static boolean exists(String name) {
        ensureLoaded();
        return presets.containsKey(name);
    }

    public static void save(String name) {
        ensureLoaded();
        Preset p = new Preset();
        p.saturation = SolarisConfig.SATURATION.get();
        p.waterOpacity = SolarisConfig.WATER_OPACITY.get();
        p.waterDeepOnly = SolarisConfig.WATER_DEEP_ONLY.get();
        p.waterDeepYThreshold = SolarisConfig.WATER_DEEP_Y_THRESHOLD.get();
        p.waypointIconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        p.hillshading = SolarisConfig.HILLSHADING.get();
        p.hillshadingStrength = SolarisConfig.HILLSHADING_STRENGTH.get();
        presets.put(name, p);
        persist();
    }

    /** Applies a saved preset's values back into {@link SolarisConfig}. No-op if {@code name} isn't a known preset. */
    public static void load(String name) {
        ensureLoaded();
        Preset p = presets.get(name);
        if (p == null) return;
        SolarisConfig.SATURATION.set(p.saturation);
        SolarisConfig.WATER_OPACITY.set(p.waterOpacity);
        SolarisConfig.WATER_DEEP_ONLY.set(p.waterDeepOnly);
        SolarisConfig.WATER_DEEP_Y_THRESHOLD.set(p.waterDeepYThreshold);
        SolarisConfig.WAYPOINT_ICON_SCALE.set(p.waypointIconScale);
        SolarisConfig.HILLSHADING.set(p.hillshading);
        SolarisConfig.HILLSHADING_STRENGTH.set(p.hillshadingStrength);
        SolarisConfig.SATURATION.save();
        SolarisConfig.WATER_OPACITY.save();
        SolarisConfig.WATER_DEEP_ONLY.save();
        SolarisConfig.WATER_DEEP_Y_THRESHOLD.save();
        SolarisConfig.WAYPOINT_ICON_SCALE.save();
        SolarisConfig.HILLSHADING.save();
        SolarisConfig.HILLSHADING_STRENGTH.save();
    }

    public static void delete(String name) {
        ensureLoaded();
        presets.remove(name);
        persist();
    }

    private static void ensureLoaded() {
        if (presets != null) return;
        presets = new LinkedHashMap<>();
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE);
                Type type = new TypeToken<LinkedHashMap<String, Preset>>() {}.getType();
                Map<String, Preset> loaded = GSON.fromJson(json, type);
                if (loaded != null) presets.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(presets));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
