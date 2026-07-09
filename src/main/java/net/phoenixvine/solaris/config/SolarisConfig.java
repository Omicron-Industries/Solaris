package net.phoenixvine.solaris.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.phoenixvine.solaris.client.render.LabelSide;

/** Client-side tuning for minimap/map size, zoom bounds, and cache limits. */
public final class SolarisConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE;
    public static final ForgeConfigSpec.IntValue MINIMAP_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAP_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_CACHED_CHUNKS;
    public static final ForgeConfigSpec.DoubleValue ZOOM_MIN;
    public static final ForgeConfigSpec.DoubleValue ZOOM_MAX;
    public static final ForgeConfigSpec.DoubleValue SATURATION;
    public static final ForgeConfigSpec.BooleanValue SHOW_BLOCK_TOOLTIP;
    public static final ForgeConfigSpec.DoubleValue WATER_OPACITY;
    public static final ForgeConfigSpec.BooleanValue WATER_DEEP_ONLY;
    public static final ForgeConfigSpec.IntValue WATER_DEEP_Y_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue WAYPOINT_ICON_SCALE;
    public static final ForgeConfigSpec.BooleanValue SHOW_GT_ORE_VEINS;
    public static final ForgeConfigSpec.EnumValue<LabelSide> LABEL_SIDE;
    public static final ForgeConfigSpec.BooleanValue WAYPOINT_BEAMS;
    public static final ForgeConfigSpec.IntValue WAYPOINT_BEAM_RANGE;
    public static final ForgeConfigSpec.BooleanValue WAYPOINT_COMPASS;
    public static final ForgeConfigSpec.BooleanValue DEATH_MARKERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("minimap");
        MINIMAP_SIZE = builder.comment("On-screen size, in pixels, of the corner minimap.")
                .defineInRange("size", 96, 32, 512);
        MINIMAP_RADIUS_CHUNKS = builder
                .comment("How many chunks in each direction the minimap's backing texture covers.")
                .defineInRange("radiusChunks", 4, 1, 16);
        builder.pop();

        builder.push("map");
        MAP_RADIUS_CHUNKS = builder
                .comment("How many chunks in each direction the fullscreen map's backing texture covers.")
                .defineInRange("radiusChunks", 24, 4, 64);
        ZOOM_MIN = builder.comment("Minimum zoom factor on the fullscreen map.")
                .defineInRange("zoomMin", 0.25, 0.05, 1.0);
        ZOOM_MAX = builder.comment("Maximum zoom factor on the fullscreen map.")
                .defineInRange("zoomMax", 4.0, 1.0, 16.0);
        builder.pop();

        builder.push("display");
        SATURATION = builder
                .comment("Saturation multiplier applied to sampled map colors. 1.0 = unchanged, " +
                        "0.0 = grayscale, above 1.0 = more vivid.")
                .defineInRange("saturation", 1.0, 0.0, 2.0);
        SHOW_BLOCK_TOOLTIP = builder
                .comment("Show the block you're hovering over on the fullscreen map as a tooltip. " +
                        "Reveals block info you may not have discovered in-world yet, so it's off by default.")
                .define("showBlockTooltip", false);
        WATER_OPACITY = builder
                .comment("How strongly water tints the real floor block color underneath it (a railway, " +
                        "ruins, etc. still show through as their own shape, just tinted — this isn't a " +
                        "flat water color painted over everything). 0.0 = mostly see-through the floor, " +
                        "1.0 = strong blue tint. Scales up with depth regardless of this setting, so deep " +
                        "water still reads as properly filled even at low values.")
                .defineInRange("waterOpacity", 0.6, 0.0, 1.0);
        WATER_DEEP_ONLY = builder
                .comment("If enabled, water at or above waterDeepYThreshold is pinned to the lightest tint " +
                        "regardless of actual depth (e.g. rivers/lakes at normal sea level show the floor " +
                        "clearly) — only water whose surface is below that Y gets the full depth-based tint " +
                        "curve. An alternative to the continuous depth curve above, not a replacement for it.")
                .define("waterDeepOnly", false);
        WATER_DEEP_Y_THRESHOLD = builder
                .comment("Y level below which water still tints when waterDeepOnly is enabled.")
                .defineInRange("waterDeepYThreshold", 50, -64, 320);
        WAYPOINT_ICON_SCALE = builder
                .comment("Size multiplier for waypoint icons on the fullscreen map and waypoint list " +
                        "(the corner minimap keeps its own small fixed dots regardless — too little screen " +
                        "space there for a real icon to read).")
                .defineInRange("waypointIconScale", 1.0, 0.5, 3.0);
        SHOW_GT_ORE_VEINS = builder
                .comment("Show GTCEu ore veins you've already had revealed to you (prospecting, surface " +
                        "indicators, etc.) as markers on the fullscreen map. No effect if GTCEu isn't " +
                        "installed. Not a cheat/X-ray — only shows veins GTCEu itself already revealed.")
                .define("showGtOreVeins", true);
        LABEL_SIDE = builder
                .comment("Which side of a marker (waypoint or GT ore vein) its name label draws on. With " +
                        "a lot of markers on screen at once, a label fixed to one side can run off the " +
                        "map's edge or overlap a neighboring icon — pick whichever side fits your layout.")
                .defineEnum("labelSide", LabelSide.RIGHT);
        builder.pop();

        builder.push("waypoints");
        WAYPOINT_BEAMS = builder
                .comment("Show a thin vertical beam in the 3D world at each visible waypoint's location, " +
                        "not just on the map — makes a waypoint findable by looking around, not just by " +
                        "opening the map.")
                .define("beams", true);
        WAYPOINT_BEAM_RANGE = builder
                .comment("Beams only render for waypoints within this many blocks, to avoid drawing dozens " +
                        "of beams across the whole loaded area at once.")
                .defineInRange("beamRange", 384, 16, 2048);
        WAYPOINT_COMPASS = builder
                .comment("Show a small HUD arrow + distance pointing toward the tracked waypoint (or the " +
                        "nearest one, if none is explicitly tracked) — lets you navigate toward it without " +
                        "opening the map.")
                .define("compass", true);
        DEATH_MARKERS = builder
                .comment("Automatically add a waypoint at your position whenever you die, so you can find " +
                        "your way back to lost items.")
                .define("deathMarkers", true);
        builder.pop();

        builder.push("cache");
        MAX_CACHED_CHUNKS = builder
                .comment("Maximum number of sampled chunk color arrays kept in memory (LRU-evicted beyond this).")
                .defineInRange("maxCachedChunks", 4096, 256, 65536);
        builder.pop();

        SPEC = builder.build();
    }

    private SolarisConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
