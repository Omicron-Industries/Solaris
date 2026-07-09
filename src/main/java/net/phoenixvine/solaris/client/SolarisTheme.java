package net.phoenixvine.solaris.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Mirrors {@code net.phoenixvine.guilds.client.GuildTheme}'s shape and persistence pattern. */
public class SolarisTheme {

    // ── ThemeColor ────────────────────────────────────────────────────────────

    public static class ThemeColor {

        public String hex;
        private transient Integer cached = null;

        public ThemeColor() {
            this.hex = "FFFFFFFF";
        }

        public ThemeColor(String hex) {
            this.hex = hex;
        }

        public int getColor() {
            if (hex == null) return 0xFFFFFFFF;
            String clean = hex.trim().toUpperCase(Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);
            if (cached == null) {
                try {
                    cached = (int) Long.parseUnsignedLong(clean, 16);
                } catch (Exception e) {
                    cached = 0xFFFFFFFF;
                }
            }
            return cached;
        }

        public String getHex() {
            return hex;
        }

        public void set(String h) {
            this.hex = h;
            this.cached = null;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    // bg: map screen background. panel: minimap/HUD backdrop. header: map title bar.
    // border: panel/minimap outline. accent: player marker + highlights.
    // text/dim: labels. faint: "fog" color for not-yet-sampled chunks.

    public ThemeColor bg, panel, header, border, accent, text, dim, faint;

    // ── Registry ──────────────────────────────────────────────────────────────

    public static final Map<String, SolarisTheme> REGISTRY = new LinkedHashMap<>();
    private static SolarisTheme active = null;
    private static String activeName = "VOID";

    private static final Set<String> BUILTINS = Set.of("VOID", "NEBULA", "SOLAR_FLARE", "ECLIPSE");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();
    private static final Path THEMES_FILE = Paths.get("config", "phoenix_solaris_themes.json");

    // ── Constructors ──────────────────────────────────────────────────────────

    public SolarisTheme() {}

    public SolarisTheme(String bg, String panel, String header, String border,
                        String accent, String text, String dim, String faint) {
        this.bg = new ThemeColor(bg);
        this.panel = new ThemeColor(panel);
        this.header = new ThemeColor(header);
        this.border = new ThemeColor(border);
        this.accent = new ThemeColor(accent);
        this.text = new ThemeColor(text);
        this.dim = new ThemeColor(dim);
        this.faint = new ThemeColor(faint);
    }

    public SolarisTheme copy() {
        return new SolarisTheme(bg.hex, panel.hex, header.hex, border.hex, accent.hex, text.hex, dim.hex, faint.hex);
    }

    // ── Static API ────────────────────────────────────────────────────────────

    public static SolarisTheme current() {
        if (REGISTRY.isEmpty()) loadThemes();
        return active != null ? active : REGISTRY.get("VOID");
    }

    public static String getActiveName() {
        if (REGISTRY.isEmpty()) loadThemes();
        return activeName;
    }

    public static boolean isBuiltin(String name) {
        return BUILTINS.contains(name.toUpperCase(Locale.ROOT));
    }

    public static void setCurrent(String name) {
        String upperName = name.toUpperCase(Locale.ROOT);
        SolarisTheme t = REGISTRY.get(name);
        if (t == null) {
            t = REGISTRY.get(upperName);
            if (t != null) name = upperName;
        }

        if (t != null) {
            active = t;
            activeName = name;
            SolarisThemeUtils.refreshCache();
        }
    }

    public static void saveCustomTheme(String name, SolarisTheme theme) {
        REGISTRY.put(name, theme);
        saveAll();
        if (name.equals(activeName)) SolarisThemeUtils.refreshCache();
    }

    public static boolean deleteCustom(String name) {
        if (isBuiltin(name)) return false;
        REGISTRY.remove(name);
        if (name.equals(activeName)) {
            activeName = "VOID";
            active = REGISTRY.get("VOID");
        }
        saveAll();
        SolarisThemeUtils.refreshCache();
        return true;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static class ThemeSave {

        String active = "VOID";
        Map<String, SolarisTheme> custom = new LinkedHashMap<>();
    }

    public static void saveAll() {
        try {
            Files.createDirectories(THEMES_FILE.getParent());
            ThemeSave save = new ThemeSave();
            save.active = activeName;
            for (Map.Entry<String, SolarisTheme> e : REGISTRY.entrySet())
                if (!isBuiltin(e.getKey())) save.custom.put(e.getKey(), e.getValue());
            Files.writeString(THEMES_FILE, GSON.toJson(save));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadThemes() {
        REGISTRY.clear();

        // Deep-space/phoenix-fire palettes — bg panel header border accent text dim faint
        REGISTRY.put("VOID", new SolarisTheme("CC05040F", "FF0A0818", "FF060512", "FF241C40",
                "FFFF6A1A", "FFF5E8D8", "FF9A8AB0", "FF14101F"));
        REGISTRY.put("NEBULA", new SolarisTheme("CC0A0616", "FF130A22", "FF080512", "FF3A1E52",
                "FFDD44CC", "FFF0E0FA", "FFA888C0", "FF1C1230"));
        REGISTRY.put("SOLAR_FLARE", new SolarisTheme("CC120602", "FF1E0C04", "FF100602", "FF4A2408",
                "FFFFB020", "FFFFF0D8", "FFC49060", "FF241206"));
        REGISTRY.put("ECLIPSE", new SolarisTheme("CC000000", "FF0C0C0C", "FF060606", "FF2A2A2A",
                "FFE8C468", "FFF0F0F0", "FF909090", "FF141414"));

        String loadedActive = "VOID";
        try {
            if (Files.exists(THEMES_FILE)) {
                String json = Files.readString(THEMES_FILE);
                Type type = new TypeToken<ThemeSave>() {}.getType();
                ThemeSave save = GSON.fromJson(json, type);
                if (save != null) {
                    if (save.custom != null) REGISTRY.putAll(save.custom);
                    if (save.active != null) loadedActive = save.active;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        activeName = loadedActive;
        active = REGISTRY.getOrDefault(activeName, REGISTRY.get("VOID"));

        SolarisThemeUtils.refreshCache();
    }
}
