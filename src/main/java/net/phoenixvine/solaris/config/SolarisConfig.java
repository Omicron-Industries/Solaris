package net.phoenixvine.solaris.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.phoenixvine.solaris.client.render.LabelSide;
import net.phoenixvine.solaris.client.render.MinimapShape;

public final class SolarisConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE;
    public static final ForgeConfigSpec.IntValue MINIMAP_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAP_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_CACHED_CHUNKS;
    public static final ForgeConfigSpec.IntValue WATER_BLEND_RADIUS;
    public static final ForgeConfigSpec.DoubleValue ZOOM_MIN;
    public static final ForgeConfigSpec.DoubleValue ZOOM_MAX;
    public static final ForgeConfigSpec.DoubleValue SATURATION;
    public static final ForgeConfigSpec.DoubleValue CONTRAST;
    public static final ForgeConfigSpec.DoubleValue BRIGHTNESS;
    public static final ForgeConfigSpec.DoubleValue FOLIAGE_BRIGHTNESS;
    public static final ForgeConfigSpec.DoubleValue TINT_RED;
    public static final ForgeConfigSpec.DoubleValue TINT_GREEN;
    public static final ForgeConfigSpec.DoubleValue TINT_BLUE;
    public static final ForgeConfigSpec.BooleanValue VIGNETTE;
    public static final ForgeConfigSpec.DoubleValue VIGNETTE_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue BLACK_AND_WHITE;
    public static final ForgeConfigSpec.EnumValue<net.phoenixvine.solaris.client.render.UnexploredStyle> UNEXPLORED_STYLE;
    public static final ForgeConfigSpec.DoubleValue UNEXPLORED_DENSITY;
    public static final ForgeConfigSpec.DoubleValue UNEXPLORED_BRIGHTNESS;
    public static final ForgeConfigSpec.BooleanValue SHOW_BLOCK_TOOLTIP;
    public static final ForgeConfigSpec.DoubleValue WATER_OPACITY;
    public static final ForgeConfigSpec.BooleanValue WATER_DEEP_ONLY;
    public static final ForgeConfigSpec.IntValue WATER_DEEP_Y_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue WAYPOINT_ICON_SCALE;
    public static final ForgeConfigSpec.BooleanValue SHOW_GT_ORE_VEINS;
    public static final ForgeConfigSpec.BooleanValue SHOW_MOBS;
    public static final ForgeConfigSpec.BooleanValue HILLSHADING;
    public static final ForgeConfigSpec.DoubleValue HILLSHADING_STRENGTH;
    public static final ForgeConfigSpec.EnumValue<LabelSide> LABEL_SIDE;
    public static final ForgeConfigSpec.BooleanValue WAYPOINT_BEAMS;
    public static final ForgeConfigSpec.IntValue WAYPOINT_BEAM_RANGE;
    public static final ForgeConfigSpec.BooleanValue WAYPOINT_COMPASS;
    public static final ForgeConfigSpec.BooleanValue DEATH_MARKERS;
    public static final ForgeConfigSpec.BooleanValue SHOW_PLAN_SHAPES;
    public static final ForgeConfigSpec.IntValue PLAN_SHAPE_RANGE;
    public static final ForgeConfigSpec.IntValue LIVE_REFRESH_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue LIVE_REFRESH_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue PERF_LOGGING;
    public static final ForgeConfigSpec.IntValue PERF_LOG_THRESHOLD_MS;
    public static final ForgeConfigSpec.IntValue PERF_SUMMARY_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_PERSISTED_CHUNKS_PER_DIMENSION;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_ROTATE;
    public static final ForgeConfigSpec.EnumValue<net.phoenixvine.solaris.client.render.MinimapShape> MINIMAP_SHAPE;
    public static final ForgeConfigSpec.BooleanValue SHOW_RAIL_NETWORK;
    public static final ForgeConfigSpec.IntValue RAIL_NETWORK_RANGE;
    public static final ForgeConfigSpec.IntValue MAX_MINIMAP_RANGE_CHUNKS;
    public static final ForgeConfigSpec.IntValue WORLD_MAP_WRITE_RANGE_CHUNKS;
    public static final ForgeConfigSpec.DoubleValue MINIMAP_ZOOM;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_SHOW_TIME;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_SHOW_COORDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_CHUNK_GRID;
    public static final ForgeConfigSpec.DoubleValue NIGHT_MODE_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue GLOBE_VIEW_ENABLED;
    public static final ForgeConfigSpec.EnumValue<MinimapShape> MAP_SHAPE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("minimap");
        MINIMAP_SIZE = builder.comment("On-screen size, in pixels, of the corner minimap.")
                .defineInRange("size", 96, 32, 512);
        MINIMAP_RADIUS_CHUNKS = builder
                .comment("How many chunks in each direction the minimap's backing texture covers.")
                .defineInRange("radiusChunks", 4, 1, 16);
        MINIMAP_ROTATE = builder
                .comment("Rotate the minimap so it always faces the direction you're looking (player " +
                        "arrow fixed pointing up), instead of the default fixed north-up orientation.")
                .define("rotate", false);
        MINIMAP_SHAPE = builder
                .comment("Outline shape of the corner minimap — square or circle. Cycled together with " +
                        "minimap size by the \"cycle minimap style\" keybind.")
                .defineEnum("shape", net.phoenixvine.solaris.client.render.MinimapShape.SQUARE);
        MINIMAP_ZOOM = builder
                .comment("How magnified the minimap view is. 1.0 shows the full sampled radius around " +
                        "you (minimapRadiusChunks); higher values crop that down to a smaller, more " +
                        "zoomed-in area. Can't zoom out past 1.0 — there's no more sampled terrain beyond " +
                        "the radius to show.")
                .defineInRange("zoom", 1.0, 1.0, 8.0);
        MINIMAP_SHOW_TIME = builder
                .comment("Show the current in-world time of day on the minimap.")
                .define("showTime", false);
        MINIMAP_SHOW_COORDS = builder
                .comment("Show your current X/Y/Z coordinates on the minimap.")
                .define("showCoords", false);
        builder.pop();

        builder.push("map");
        MAP_RADIUS_CHUNKS = builder
                .comment("How many chunks in each direction the fullscreen map's backing texture covers.")
                .defineInRange("radiusChunks", 24, 4, 64);
        ZOOM_MIN = builder.comment("Minimum zoom factor on the fullscreen map.")
                .defineInRange("zoomMin", 0.25, 0.05, 1.0);
        ZOOM_MAX = builder.comment("Maximum zoom factor on the fullscreen map.")
                .defineInRange("zoomMax", 32.0, 1.0, 48.0);
        SHOW_CHUNK_GRID = builder
                .comment("Draw thin lines along chunk boundaries on the fullscreen map.")
                .define("showChunkGrid", false);
        MAP_SHAPE = builder
                .comment("Clip the fullscreen map's visible terrain into this outline shape instead of a plain " +
                        "rectangle — the same shape options the corner minimap already offers, applied to the " +
                        "big map too. The clip is fixed to the panel's own frame; terrain still pans/zooms " +
                        "underneath it exactly as before.")
                .defineEnum("mapShape", MinimapShape.SQUARE);
        builder.pop();

        builder.push("display");
        SATURATION = builder
                .comment("Saturation multiplier applied to sampled map colors. 1.0 = unchanged, " +
                        "0.0 = grayscale, above 1.0 = more vivid. Default lowered from a flat 1.0 per " +
                        "community feedback comparing side-by-side against JourneyMap/Xaero — full vanilla-biome " +
                        "saturation read as noticeably more vivid than either of those, and a value roughly " +
                        "halfway toward their look was the preferred middle ground.")
                .defineInRange("saturation", 0.85, 0.0, 2.0);
        CONTRAST = builder
                .comment("Contrast multiplier applied to sampled map colors, around a mid-gray pivot. 1.0 = " +
                        "unchanged, above 1.0 = more contrast (dark areas darker, light areas lighter). Added " +
                        "alongside lowering the saturation default — per the same community comparison, " +
                        "Solaris's biggest gap next to JourneyMap/Xaero wasn't color intensity but flatness; a " +
                        "little extra contrast is what actually closes that gap.")
                .defineInRange("contrast", 1.3, 0.0, 3.0);
        BRIGHTNESS = builder
                .comment("Flat brightness multiplier applied to every sampled map color, on top of saturation/" +
                        "contrast. 1.0 = unchanged, below 1.0 = darker. Contrast alone (a mid-gray pivot) makes " +
                        "bright areas brighter along with dark areas darker, so it can't uniformly darken the " +
                        "whole map on its own — this does that directly. Went 0.8 (too dark) -> 1.04 (still " +
                        "reported too dark outside of foliage) -> 1.2, a more decisive jump since two small " +
                        "increments in a row both landed short. See foliageBrightness below for why foliage " +
                        "specifically doesn't just inherit this same increase.")
                .defineInRange("brightness", 1.2, 0.0, 2.0);
        FOLIAGE_BRIGHTNESS = builder
                .comment("Extra brightness multiplier applied only to foliage/tree-canopy pixels, on top of the " +
                        "flat brightness above — 1.0 = no extra adjustment beyond that. Exists because raising " +
                        "brightness reads fine on ordinary terrain but makes foliage specifically look washed " +
                        "out/unnaturally light, so this cancels the flat increase back out for foliage only " +
                        "(default ~0.67, recomputed each time brightness moves so brightness * " +
                        "foliageBrightness stays pinned at the same ~0.8 net foliage darkness throughout — " +
                        "foliage was reported as already correct and shouldn't move when brightness does), " +
                        "while every other block still gets the full increase.")
                .defineInRange("foliageBrightness", 0.67, 0.0, 2.0);
        TINT_RED = builder
                .comment("Per-channel red multiplier applied to every sampled map color, on top of everything " +
                        "else above (saturation/contrast/brightness/foliageBrightness). 1.0 = unchanged. Unlike " +
                        "those, this and tintGreen/tintBlue let the whole map be pushed toward a custom hue " +
                        "(warm/cool/sepia/etc.), not just intensity — file-only for now (no slider), since " +
                        "per-channel tuning is fiddly and better suited to hand-editing this file directly.")
                .defineInRange("tintRed", 1.0, 0.0, 2.0);
        TINT_GREEN = builder
                .comment("Per-channel green multiplier — see tintRed's comment.")
                .defineInRange("tintGreen", 1.0, 0.0, 2.0);
        TINT_BLUE = builder
                .comment("Per-channel blue multiplier — see tintRed's comment.")
                .defineInRange("tintBlue", 1.0, 0.0, 2.0);
        VIGNETTE = builder
                .comment("Darken the map toward its edges, like a photo vignette — a purely stylistic effect, " +
                        "independent of saturation/contrast/brightness/tint (stacks with any combination of " +
                        "them), same as Hillshading/Night Mode are already independent toggles.")
                .define("vignette", false);
        VIGNETTE_STRENGTH = builder
                .comment("How strongly the vignette darkens the corners/edges. 0.0 = no effect even if " +
                        "vignette is on, 1.0 = strongest.")
                .defineInRange("vignetteStrength", 0.5, 0.0, 1.0);
        BLACK_AND_WHITE = builder
                .comment("Render the whole fullscreen/minimap in grayscale — a full luminance-only " +
                        "conversion, applied last (after every other color/lighting effect), independent of " +
                        "the Saturation slider (that just partially desaturates; this forces it all the way).")
                .define("blackAndWhite", false);
        UNEXPLORED_STYLE = builder
                .comment("How never-explored chunks render on the fullscreen map. FOG = flat theme fog color " +
                        "(default). STARFIELD = a deterministic per-position star pattern colored from the " +
                        "active theme's accent/dim/faint colors. PHOENIX = a deterministic ember pattern in " +
                        "fixed warm fire colors. CLOUD = soft semi-transparent fog-of-war cloud blobs with real " +
                        "gaps between them. All three patterns are stable across rebuilds and get pushed back " +
                        "by real terrain as you explore. Purely cosmetic.")
                .defineEnum("unexploredStyle", net.phoenixvine.solaris.client.render.UnexploredStyle.FOG);
        UNEXPLORED_DENSITY = builder
                .comment("Multiplier on how many stars/embers appear for the starfield/phoenix unexploredStyle. " +
                        "1.0 = default, higher = denser, lower = sparser. No effect when unexploredStyle is FOG.")
                .defineInRange("unexploredDensity", 1.0, 0.25, 4.0);
        UNEXPLORED_BRIGHTNESS = builder
                .comment("Multiplier on star/ember brightness for the starfield/phoenix unexploredStyle. " +
                        "1.0 = default. No effect when unexploredStyle is FOG.")
                .defineInRange("unexploredBrightness", 1.0, 0.25, 2.5);
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
                .defineInRange("waterOpacity", 0.0, 0.0, 1.0);
        WATER_BLEND_RADIUS = builder
                .comment("Radius (in blocks) for sampling and averaging water biome colors. " +
                        "Snaps to increments of 4 (0, 4, 8, 12, 16). 0 = OFF, 8 = default (5x5 grid).")
                .defineInRange("waterBlendRadius", 8, 0, 16);
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
        SHOW_MOBS = builder
                .comment("Show nearby living mobs (hostile and passive) as markers on the fullscreen map and " +
                        "globe, same way other players already show up.")
                .define("showMobs", true);
        HILLSHADING = builder
                .comment("Replace the flat map's simple north-neighbor relief shading with real " +
                        "cartographic hillshading — a smooth slope-based light/dark gradient computed from " +
                        "each pixel's full surrounding neighborhood, applied to every pixel including water, " +
                        "not just the current single-comparison approximation. Noticeably more textured/alive " +
                        "looking, at the cost of a full-map post-process pass on every rebuild — off by " +
                        "default since it's meaningfully more expensive than the default shading.")
                .define("hillshading", false);
        HILLSHADING_STRENGTH = builder
                .comment("How strong the hillshading light/dark gradient is. 0 = no effect (flat), " +
                        "1 = full strength. Defaults below full strength — most of the effect reads clearly " +
                        "well under 100%, and full strength tends to look overdone/noisy rather than more " +
                        "detailed.")
                .defineInRange("hillshadingStrength", 0.7, 0.0, 1.0);
        NIGHT_MODE_STRENGTH = builder
                .comment("How dark the map gets at full night (except light-emitting blocks like lava, which " +
                        "stay bright regardless of time of day) — always applied, no heavier than the normal " +
                        "day-mode render. 0 = no effect, 1 = fully black. No settings-screen slider for this " +
                        "yet — edit the config directly to tune it.")
                .defineInRange("nightModeStrength", 0.55, 0.0, 1.0);
        LABEL_SIDE = builder
                .comment("Which side of a marker (waypoint or GT ore vein) its name label draws on. With " +
                        "a lot of markers on screen at once, a label fixed to one side can run off the " +
                        "map's edge or overlap a neighboring icon — pick whichever side fits your layout.")
                .defineEnum("labelSide", LabelSide.RIGHT);
        SHOW_RAIL_NETWORK = builder
                .comment("Draw connected rail track as a clean line overlay (like a transit map), instead of " +
                        "just the flat per-pixel rail color already baked into the terrain.")
                .define("showRailNetwork", true);
        RAIL_NETWORK_RANGE = builder
                .comment("The rail-line overlay only renders within this many blocks of the player, to avoid " +
                        "scanning/drawing across the whole loaded area every frame.")
                .defineInRange("railNetworkRange", 256, 16, 2048);
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

        builder.push("planning");
        SHOW_PLAN_SHAPES = builder
                .comment("Show planned build shapes (drawn on the map) as in-world wireframe outlines — " +
                        "see a build's footprint/height before placing a single real block.")
                .define("showPlanShapes", true);
        PLAN_SHAPE_RANGE = builder
                .comment("Plan shapes only render in-world within this many blocks, to avoid drawing dozens " +
                        "of wireframes across the whole loaded area at once.")
                .defineInRange("planShapeRange", 256, 16, 2048);
        LIVE_REFRESH_INTERVAL_SECONDS = builder
                .comment("How often (in seconds) currently-loaded chunks near the player get re-sampled from " +
                        "the world, so recent block edits show up on the map without needing to leave and " +
                        "re-enter render distance. Only touches a small radius around the player each pass " +
                        "(see liveRefreshRadiusChunks), so this stays cheap even at a short interval.")
                .defineInRange("liveRefreshIntervalSeconds", 60, 10, 600);
        LIVE_REFRESH_RADIUS_CHUNKS = builder
                .comment("How many chunks around the player get re-sampled on each live refresh pass.")
                .defineInRange("liveRefreshRadiusChunks", 8, 2, 32);
        builder.pop();

        builder.push("featureRanges");
        MAX_MINIMAP_RANGE_CHUNKS = builder
                .comment("Server-owner safety ceiling on the minimap's radius (minimapRadiusChunks), " +
                        "independent of any per-player/team feature-state toggle — a static cap, not something " +
                        "an event/progression system grants per player.")
                .defineInRange("maxMinimapRangeChunks", 16, 1, 32);
        WORLD_MAP_WRITE_RANGE_CHUNKS = builder
                .comment("Server-owner safety ceiling (in chunks, from the player) on how far the world map is " +
                        "allowed to actively sample/write new chunk data, when the per-player/team world-map " +
                        "feature state is ENABLED. Browsing already-cached data beyond this range is unaffected " +
                        "— this only caps new writes.")
                .defineInRange("worldMapWriteRangeChunks", 32, 2, 128);
        builder.pop();

        builder.push("cache");
        MAX_CACHED_CHUNKS = builder
                .comment("Maximum number of sampled chunk color arrays kept in memory (LRU-evicted beyond " +
                        "this), shared across every open map/minimap. A single fullscreen map at the default " +
                        "24-chunk radius alone needs ~2401 chunks in view at once — the old default of 4096 " +
                        "left barely any headroom once the always-on minimap and normal panning/exploring " +
                        "were sharing the same bound, so already-explored chunks kept aging out and " +
                        "re-painting as unexplored fog the moment the view moved on. Raised well past the " +
                        "largest default single-view working set.")
                .defineInRange("maxCachedChunks", 16384, 256, 65536);
        builder.pop();

        builder.push("performance");
        PERF_LOGGING = builder
                .comment("Log a warning whenever a Solaris operation (texture rebuild, chunk sampling, water " +
                        "blur, etc.) takes longer than perfLogThresholdMs, plus a periodic summary — a map " +
                        "mod's cost is easy to miss otherwise, since a single slow rebuild just looks like a " +
                        "generic frame hitch with nothing pointing back at what caused it.")
                .define("perfLogging", false);
        PERF_LOG_THRESHOLD_MS = builder
                .comment("An individual operation slower than this (in milliseconds) gets logged immediately.")
                .defineInRange("perfLogThresholdMs", 50, 1, 5000);
        PERF_SUMMARY_INTERVAL_SECONDS = builder
                .comment("How often (in seconds) a count/average/max summary per operation gets logged — " +
                        "catches a cost that's individually too small to trip perfLogThresholdMs but adds up " +
                        "from running very often (e.g. every chunk load).")
                .defineInRange("perfSummaryIntervalSeconds", 300, 30, 3600);
        builder.pop();

        builder.push("persistence");
        MAX_PERSISTED_CHUNKS_PER_DIMENSION = builder
                .comment("Explored chunks are remembered to disk so they still show on the map after leaving " +
                        "render distance or restarting — but capped per dimension, oldest-visited-first, so " +
                        "this can never grow without bound the way some other map mods' saved data does. At " +
                        "the default, ~50000 chunks is roughly 30-60MB on disk (compressed) — comfortably " +
                        "covers a large, actively-explored world while staying a known, fixed size rather " +
                        "than an ever-growing one.")
                .defineInRange("maxPersistedChunksPerDimension", 50000, 1000, 500000);
        builder.pop();

        builder.push("experimental");
        GLOBE_VIEW_ENABLED = builder
                .comment("EXPERIMENTAL — the 3D globe map view. Off by default and file-only (no in-game " +
                        "toggle): the Globe View button on the fullscreen map only appears at all when this is " +
                        "set to true here. Not exposed as a normal display setting because it isn't considered " +
                        "stable/finished yet.")
                .define("globeViewEnabled", false);
        builder.pop();

        SPEC = builder.build();
    }

    private SolarisConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
