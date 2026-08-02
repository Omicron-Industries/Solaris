package net.phoenixvine.solaris.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.SolarisMapScreen;
import net.phoenixvine.solaris.client.SolarisTheme;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.UnexploredStyle;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class SolarisAPI {

    public static final String FEATURE_GLOBE_VIEW = "globe_view";
    public static final String FEATURE_SHAPE_PLANNER = "shape_planner";
    public static final String FEATURE_PNG_EXPORT = "png_export";
    public static final String FEATURE_WEB_EXPORT = "web_export";
    public static final String FEATURE_GUILD_SHARE = "guild_share";
    public static final String FEATURE_FULLSCREEN_MAP = "fullscreen_map";
    public static final String FEATURE_SHOW_COORDINATES = "show_coordinates";
    public static final String FEATURE_WAYPOINTS = "waypoints";
    public static final String FEATURE_MINIMAP = "minimap";
    public static final String FEATURE_WORLD_MAP = "world_map";
    public static final String FEATURE_UNDERGROUND_MAP = "underground_map";
    public static final String FEATURE_SETTINGS_MENU = "settings_menu";
    public static final String FEATURE_GOTO_COORDINATE = "goto_coordinate";
    public static final String FEATURE_THEME_SELECT = "theme_select";
    public static final String FEATURE_HILLSHADING = "hillshading";
    public static final String FEATURE_VIGNETTE = "vignette";
    public static final String FEATURE_BLACK_AND_WHITE = "black_and_white";
    public static final String FEATURE_CHUNK_GRID = "chunk_grid";
    public static final String FEATURE_SHOW_MOBS = "show_mobs";
    public static final String FEATURE_RAIL_NETWORK = "rail_network";
    public static final String FEATURE_UNEXPLORED_STYLE = "unexplored_style";

    private static final Map<String, BooleanSupplier> FEATURE_GATES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Integer> DIMENSION_TIERS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, Integer>> TIER_REQUIREMENTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, SolarisFeatureState>> FEATURE_STATES = new ConcurrentHashMap<>();

    private static final Set<String> KNOWN_FEATURE_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_UNKNOWN_FEATURE_IDS = ConcurrentHashMap.newKeySet();

    static {
        KNOWN_FEATURE_IDS.addAll(List.of(
                FEATURE_GLOBE_VIEW, FEATURE_SHAPE_PLANNER, FEATURE_PNG_EXPORT, FEATURE_WEB_EXPORT,
                FEATURE_GUILD_SHARE, FEATURE_FULLSCREEN_MAP, FEATURE_SHOW_COORDINATES,
                FEATURE_WAYPOINTS, FEATURE_MINIMAP, FEATURE_WORLD_MAP, FEATURE_UNDERGROUND_MAP,
                FEATURE_SETTINGS_MENU, FEATURE_GOTO_COORDINATE, FEATURE_THEME_SELECT, FEATURE_HILLSHADING,
                FEATURE_VIGNETTE, FEATURE_BLACK_AND_WHITE, FEATURE_CHUNK_GRID, FEATURE_SHOW_MOBS,
                FEATURE_RAIL_NETWORK, FEATURE_UNEXPLORED_STYLE));
    }

    private static volatile boolean refreshPending = false;

    private SolarisAPI() {}

    public static void registerFeatureGate(String featureId, BooleanSupplier check) {
        KNOWN_FEATURE_IDS.add(featureId);
        FEATURE_GATES.put(featureId, check);
    }

    public static void setFeatureEnabled(String featureId, boolean enabled) {
        registerFeatureGate(featureId, () -> enabled);
    }

    public static void clearFeatureGate(String featureId) {
        FEATURE_GATES.remove(featureId);
    }

    public static void setTier(ResourceLocation dimension, int tier) {
        DIMENSION_TIERS.put(dimension, tier);
        requestRefresh();
    }

    public static int getTier(ResourceLocation dimension) {
        return DIMENSION_TIERS.getOrDefault(dimension, 0);
    }

    public static void requireTier(String featureId, ResourceLocation dimension, int requiredTier) {
        KNOWN_FEATURE_IDS.add(featureId);
        TIER_REQUIREMENTS.computeIfAbsent(featureId, id -> new ConcurrentHashMap<>()).put(dimension, requiredTier);
    }

    public static void clearTierRequirement(String featureId, ResourceLocation dimension) {
        Map<ResourceLocation, Integer> perDimension = TIER_REQUIREMENTS.get(featureId);
        if (perDimension != null) {
            perDimension.remove(dimension);
        }
    }

    @Deprecated
    public static boolean isFeatureEnabled(String featureId) {
        return isFeatureGloballyEnabled(featureId);
    }

    public static boolean isFeatureGloballyEnabled(String featureId) {
        warnIfUnknown(featureId);
        return checkGate(featureId);
    }

    public static boolean isFeatureEnabled(String featureId, ResourceLocation dimension) {
        warnIfUnknown(featureId);
        if (!checkGate(featureId)) {
            return false;
        } else {
            Map<ResourceLocation, Integer> perDimension = TIER_REQUIREMENTS.get(featureId);
            if (perDimension == null) {
                return true;
            } else {
                Integer required = perDimension.get(dimension);
                return required == null || getTier(dimension) >= required;
            }
        }
    }

    private static boolean checkGate(String featureId) {
        BooleanSupplier check = FEATURE_GATES.get(featureId);
        if (check == null) {
            return true;
        } else {
            try {
                return check.getAsBoolean();
            } catch (Exception e) {
                PhoenixSolaris.LOGGER.warn("Feature gate for '{}' threw, defaulting to enabled", featureId, e);
                return true;
            }
        }
    }

    private static void warnIfUnknown(String featureId) {
        if (!KNOWN_FEATURE_IDS.contains(featureId) && WARNED_UNKNOWN_FEATURE_IDS.add(featureId)) {
            PhoenixSolaris.LOGGER.debug(
                    "Feature id '{}' was queried but has never been gated, tiered, or given an explicit" +
                            " state \u2014 defaulting to enabled. Fine if that's intentional; if not, check for a" +
                            " typo against whatever was supposed to configure it.",
                    featureId);
        }
    }

    public static void setFeatureState(String featureId, ResourceLocation dimension, SolarisFeatureState state) {
        KNOWN_FEATURE_IDS.add(featureId);
        FEATURE_STATES.computeIfAbsent(featureId, id -> new ConcurrentHashMap<>()).put(dimension, state);
        requestRefresh();
    }

    public static SolarisFeatureState getFeatureState(String featureId, ResourceLocation dimension) {
        warnIfUnknown(featureId);
        Map<ResourceLocation, SolarisFeatureState> perDimension = FEATURE_STATES.get(featureId);
        return perDimension == null ? SolarisFeatureState.ENABLED :
                perDimension.getOrDefault(dimension, SolarisFeatureState.ENABLED);
    }

    public static void registerOverlay(SolarisOverlay overlay) {
        SolarisOverlayRegistry.register(overlay);
        requestRefresh();
    }

    public static void unregisterOverlay(SolarisOverlay overlay) {
        SolarisOverlayRegistry.unregister(overlay);
        requestRefresh();
    }

    private static final Map<String, SolarisOverlay> SCRIPT_OVERLAYS = new ConcurrentHashMap<>();

    public static void registerScriptOverlay(String id, ScriptOverlay fn) {
        SolarisOverlay previous = SCRIPT_OVERLAYS.remove(id);
        if (previous != null) SolarisOverlayRegistry.unregister(previous);

        SolarisOverlay overlay = (dimension, chunkX, chunkZ) -> java.util.Optional
                .ofNullable(fn.colorAt(dimension.toString(), chunkX, chunkZ));
        SCRIPT_OVERLAYS.put(id, overlay);
        registerOverlay(overlay);
    }

    public static void unregisterScriptOverlay(String id) {
        SolarisOverlay overlay = SCRIPT_OVERLAYS.remove(id);
        if (overlay != null) unregisterOverlay(overlay);
    }

    @FunctionalInterface
    public interface ScriptOverlay {

        Integer colorAt(String dimension, int chunkX, int chunkZ);
    }

    public static void requestRefresh() {
        refreshPending = true;
    }

    public static void refreshRendering() {
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
    }

    @Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class RefreshFlusher {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && refreshPending) {
                refreshPending = false;
                SolarisTexture.invalidateAll();
                MapTileCache.clearAll();
            }
        }
    }

    public static Waypoint addWaypoint(String name, ResourceLocation dimension, int x, int y, int z, String colorHex) {
        if (!WaypointManager.canPlace(dimension)) {
            return null;
        } else {
            Waypoint w = new Waypoint(name, dimension, x, y, z, colorHex);
            WaypointManager.add(w);
            return w;
        }
    }

    public static boolean removeWaypoint(String id) {
        return WaypointManager.remove(id);
    }

    public static List<Waypoint> getWaypoints() {
        return WaypointManager.getAll();
    }

    public static boolean openMap(Screen previousScreen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        ResourceLocation dimension = mc.level.dimension().location();
        boolean gatesOpen = isFeatureEnabled(FEATURE_FULLSCREEN_MAP, dimension) &&
                getFeatureState(FEATURE_WORLD_MAP, dimension).atLeast(SolarisFeatureState.VISIBLE);
        if (!gatesOpen) return false;

        mc.setScreen(new SolarisMapScreen(previousScreen));
        return true;
    }

    public static boolean closeMap() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SolarisMapScreen)) return false;

        mc.setScreen(null);
        return true;
    }

    public static String getTheme() {
        return SolarisTheme.getActiveName();
    }

    public static List<String> getAvailableThemes() {
        return List.copyOf(SolarisTheme.REGISTRY.keySet());
    }

    public static boolean setTheme(String name) {
        if (!SolarisTheme.REGISTRY.containsKey(name) && !SolarisTheme.REGISTRY.containsKey(name.toUpperCase())) {
            return false;
        }
        SolarisTheme.setCurrent(name);
        return true;
    }

    public static UnexploredStyle getUnexploredStyle() {
        return SolarisConfig.UNEXPLORED_STYLE.get();
    }

    public static void setUnexploredStyle(UnexploredStyle style) {
        SolarisConfig.UNEXPLORED_STYLE.set(style);
        MapTileCache.clearAll();
    }

    private static final Map<ResourceLocation, UnexploredStyle> DIMENSION_UNEXPLORED_STYLE = new ConcurrentHashMap<>();

    public static void setUnexploredStyle(ResourceLocation dimension, UnexploredStyle style) {
        DIMENSION_UNEXPLORED_STYLE.put(dimension, style);
        MapTileCache.clearAll();
    }

    public static void clearUnexploredStyleOverride(ResourceLocation dimension) {
        DIMENSION_UNEXPLORED_STYLE.remove(dimension);
        MapTileCache.clearAll();
    }

    public static UnexploredStyle getUnexploredStyle(ResourceLocation dimension) {
        return DIMENSION_UNEXPLORED_STYLE.getOrDefault(dimension, SolarisConfig.UNEXPLORED_STYLE.get());
    }

    public static boolean isHillshadingEnabled() {
        return SolarisConfig.HILLSHADING.get();
    }

    public static void setHillshadingEnabled(boolean enabled) {
        SolarisConfig.HILLSHADING.set(enabled);
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
    }

    public static boolean isVignetteEnabled() {
        return SolarisConfig.VIGNETTE.get();
    }

    public static void setVignetteEnabled(boolean enabled) {
        SolarisConfig.VIGNETTE.set(enabled);
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
    }

    public static boolean isBlackAndWhiteEnabled() {
        return SolarisConfig.BLACK_AND_WHITE.get();
    }

    public static void setBlackAndWhiteEnabled(boolean enabled) {
        SolarisConfig.BLACK_AND_WHITE.set(enabled);
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
    }

    public static boolean isChunkGridEnabled() {
        return SolarisConfig.SHOW_CHUNK_GRID.get();
    }

    public static void setChunkGridEnabled(boolean enabled) {
        SolarisConfig.SHOW_CHUNK_GRID.set(enabled);
    }

    public static boolean isShowMobsEnabled() {
        return SolarisConfig.SHOW_MOBS.get();
    }

    public static void setShowMobsEnabled(boolean enabled) {
        SolarisConfig.SHOW_MOBS.set(enabled);
    }

    public static boolean isRailNetworkEnabled() {
        return SolarisConfig.SHOW_RAIL_NETWORK.get();
    }

    public static void setRailNetworkEnabled(boolean enabled) {
        SolarisConfig.SHOW_RAIL_NETWORK.set(enabled);
        SolarisTexture.invalidateAll();
    }
}
