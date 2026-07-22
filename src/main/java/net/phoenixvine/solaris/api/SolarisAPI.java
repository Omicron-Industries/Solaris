package net.phoenixvine.solaris.api;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.SolarisMapScreen;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

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
                FEATURE_WAYPOINTS, FEATURE_MINIMAP, FEATURE_WORLD_MAP, FEATURE_UNDERGROUND_MAP));
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

    public static void requestRefresh() {
        refreshPending = true;
    }

    @Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class RefreshFlusher {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && refreshPending) {
                refreshPending = false;
                SolarisTexture.invalidateAll();
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

    public static void removeWaypoint(String id) {
        WaypointManager.remove(id);
    }

    public static List<Waypoint> getWaypoints() {
        return WaypointManager.getAll();
    }

    public static boolean openMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return false;

        ResourceLocation dimension = mc.level.dimension().location();
        boolean gatesOpen = isFeatureEnabled(FEATURE_FULLSCREEN_MAP, dimension) &&
                getFeatureState(FEATURE_WORLD_MAP, dimension).atLeast(SolarisFeatureState.VISIBLE);
        if (!gatesOpen) return false;

        mc.setScreen(new SolarisMapScreen());
        return true;
    }

    public static boolean closeMap() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SolarisMapScreen)) return false;

        mc.setScreen(null);
        return true;
    }
}
