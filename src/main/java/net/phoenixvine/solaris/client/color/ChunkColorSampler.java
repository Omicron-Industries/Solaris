package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Set;

/**
 * Samples one chunk's surface terrain into a flat 16x16 (row-major, {@code z*16+x}) array
 * of packed pixel colors ready for {@link com.mojang.blaze3d.platform.NativeImage#setPixelRGBA}.
 *
 * Verified-not-guessed details, all easy to get subtly wrong:
 * <ul>
 * <li>{@link Heightmap.Types#WORLD_SURFACE} returns the Y of the block <i>above</i> the
 * top solid block, so the actual surface block is sampled at {@code y - 1}.</li>
 * <li>Not every block has a real {@link MapColor} — decorations like signs, torches,
 * flower pots, and carpets report {@link MapColor#NONE}, whose {@code calculateRGBColor}
 * returns 0, which {@code SolarisTexture} reads as "no data" and paints as unexplored fog.
 * Left alone, standing a torch on grass would make that one pixel look unexplored. Verified
 * against vanilla's own {@code MapItem.update}, which has exactly this problem solved
 * already: a {@code do-while} that keeps descending past {@code NONE} blocks until it finds
 * a real color (or hits the world bottom) — reproduced here rather than guessed at.</li>
 * <li>{@link MapColor#calculateRGBColor} does <b>not</b> return standard ARGB — verified
 * against the decompiled source, it already packs red and blue pre-swapped (plus full
 * alpha) into exactly the layout {@code NativeImage.setPixelRGBA} wants, since Mojang
 * uses it directly for GL-facing pixel data. Feed its result straight to NativeImage;
 * re-"converting" it via {@code FastColor.ARGB32}/{@code ABGR32} (as an earlier version of
 * this class did) treats an already-swapped value as if it still needed swapping, which
 * un-cancels into the wrong swap — that's what made water render like lava.</li>
 * <li>Relief shading (HIGH/NORMAL/LOW by comparing a pixel's surface height to its north
 * neighbor's) is applied as a manual post-scale on top of {@code calculateRGBColor(NORMAL)}
 * — see {@link #scaleBrightness} — rather than by picking between
 * {@code MapColor.Brightness}'s own HIGH/NORMAL/LOW variants. Vanilla's own variants only
 * swing brightness ~18%/+16% (180/220/255), which read as too subtle/"flat" once actually
 * compared side by side against a reference (JourneyMap). The manual scale goes wider
 * (0.62x/1.0x/1.3x) for terrain that actually reads as three-dimensional. This also made it
 * possible to apply the same contrast to biome-tinted grass/foliage colors, which don't come
 * from {@code calculateRGBColor} at all and previously used vanilla's narrower range.</li>
 * <li>{@code WORLD_SURFACE} for that comparison — {@code MOTION_BLOCKING} was tried briefly to
 * dodge decorative-plant noise, but per correction from someone who actually works on
 * worldgen: {@code MOTION_BLOCKING} counts leaf blocks too, so it trades grass/flower
 * artifacts for tree-canopy ones — not actually a fix, just a different noise source.</li>
 * <li>The real cause of the stray HIGH/LOW streaks ("random lines... it's not elevation")
 * was the north-neighbor lookup crossing into a chunk that may not be loaded yet at sample
 * time — {@code ChunkColorEvents} originally sampled each chunk exactly once, on load, in
 * whatever order the client happens to receive them, and a not-yet-loaded neighbor's
 * heightmap read back as 0/empty, which the relief diff saw as a huge fake elevation drop
 * along that chunk's entire north edge. {@link Level#hasChunk} guards the lookup below so
 * that fallback is flat shading instead of garbage, and — since a thin permanently-flat seam
 * is still visibly a seam next to properly-shaded terrain — {@code ChunkColorEvents} now also
 * resamples a chunk's south neighbor when that chunk loads, on the chance the south chunk
 * sampled before this one existed and is still carrying that fallback on its north edge.</li>
 * <li>{@code Biome.getGrassColor(x,z)}/{@code getFoliageColor()}/{@code getWaterColor()}
 * (verified against decompiled {@code Biome.java}) return plain {@code 0xRRGGBB} ints, no
 * alpha and not pre-swapped like {@code MapColor} — {@link #packAbgr} applies the same
 * channel-swap {@code calculateRGBColor} does, by hand, so biome colors land in the same
 * pixel format as everything else on this texture.</li>
 * <li>Biome boundary lines (an earlier attempt at matching a JourneyMap reference
 * screenshot) were removed entirely after two rounds of the same failure mode: several
 * vanilla biomes come in near-identical-looking sub-variants generated in a fine noise
 * mosaic (ocean/warm/lukewarm/cold/frozen ocean; several forest/plains sub-biomes on land
 * too, per a follow-up report showing the exact same boxy grid on land this time) — each
 * pair is a genuinely different {@code Biome} instance but reads as the same color, so
 * boundary detection fired constantly and chopped the whole map into a grid instead of
 * drawing occasional real boundaries. Excluding water alone (the first fix attempt) wasn't
 * enough since land has the same underlying problem.</li>
 * </ul>
 */
public final class ChunkColorSampler {

    /**
     * How far straight down through a water column to hunt for the real floor block. Was 40 —
     * too shallow for deep ocean specifically (its whole defining trait is greater depth, and
     * underwater ravines/cave openings — far more common under deep ocean than shallow — can
     * plunge well past that). Columns that hit this limit before finding real floor fell back to
     * a flat, fixed "fake stone" color instead of the real (varied) seafloor, creating a
     * patchwork of real-vs-fake floor color exactly where depth happened to cross this
     * threshold — reported as chunk-grid-aligned lines, specific to deep ocean. Raised well past
     * any realistic depth (nearly the full build-height range) rather than guessing at a new
     * "safe" cutoff.
     */
    private static final int MAX_WATER_SCAN = 320;

    /**
     * Widened to package-visible (from {@code private}) so {@code SolarisTexture} can
     * approximately un-apply this same north-relative shading when building the globe's own
     * texture variant — see {@code SolarisTexture.ensureGlobeTexture} for why the flat map's
     * relief shading, correct for its fixed north-up view, reads backwards from most angles on
     * a rotatable sphere.
     */
    public static final double RELIEF_LOW = 0.62;
    public static final double RELIEF_HIGH = 1.3;
    /** North-neighbor height-delta multiplier and threshold the relief factor above is picked from. */
    public static final double RELIEF_NEIGHBOR_SCALE = 0.8;
    public static final double RELIEF_THRESHOLD = 1.2;

    /**
     * Water always gets at least this much blue tint, even at the shallowest/most see-through settings —
     * enough that it still reads as water rather than looking dried up.
     */
    private static final double MIN_TINT_STRENGTH = 0.2;

    /**
     * Water whose surface sits in this Y band (ordinary rivers/oceans, not an elevated pond
     * or waterfall) gets {@link #SEA_LEVEL_BONUS} added on top of the depth curve.
     */
    private static final int SEA_LEVEL_MIN = 60;
    private static final int SEA_LEVEL_MAX = 65;
    private static final double SEA_LEVEL_BONUS = 0.2;

    /** Tint never reaches a literal 100%, so there's always at least a hint of whatever's actually down there. */
    private static final double MAX_TINT_STRENGTH = 0.92;

    private ChunkColorSampler() {}

    /**
     * Kelp/seagrass sitting in water — {@code WORLD_SURFACE} returns the plant's own top block
     * for these, not the water surface, so they need explicit exemption anywhere water itself is
     * (relief shading, the {@link ChunkWaterCache} mask) since a plant's essentially-random
     * per-column height isn't real terrain elevation.
     */
    private static boolean isAquaticPlant(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) ||
                state.is(Blocks.TALL_SEAGRASS);
    }

    /**
     * Ice/iceberg material — frozen/deep frozen ocean generate actual icebergs that can jut
     * 10+ blocks above sea level, scattered essentially at random per column. Those columns
     * report {@code MapColor.ICE}, not water, so they take the ordinary relief-shaded terrain
     * branch below, and iceberg height variance is dramatic enough to trip the single
     * -north-neighbor relief threshold on nearly every column — a chunk-grid-aligned noise
     * pattern confined to frozen ocean, the "still there" report after every water-specific fix
     * (which none of these touch, since iceberg columns were never classified as water at all).
     */
    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) ||
                state.is(Blocks.FROSTED_ICE);
    }

    /**
     * Every vanilla glass block/pane variant (verified against decompiled {@code Blocks.java}:
     * plain glass and glass panes both default to {@code MapColor.NONE} by omission, same as
     * rails/iron bars; tinted and stained glass/panes each set their own real {@code MapColor}).
     * Handled uniformly here rather than through {@link BlockColorOverrides}/{@code MapColor} at
     * all — glass is deliberately see-through in-world, so it gets the same "descend past it,
     * then blend a light tint of its own color over the real block underneath" treatment as
     * {@link #sampleWater}'s floor-peek, instead of either hiding completely (misleading — a
     * whole greenhouse would just vanish) or painting as a flat opaque tile (misleading the other
     * way — reads as a solid wall on the map).
     */
    private static final Set<Block> GLASS_BLOCKS = Set.of(Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
            Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS, Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.ORANGE_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
            Blocks.YELLOW_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS_PANE, Blocks.PINK_STAINED_GLASS_PANE,
            Blocks.GRAY_STAINED_GLASS_PANE, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Blocks.CYAN_STAINED_GLASS_PANE,
            Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE, Blocks.BROWN_STAINED_GLASS_PANE,
            Blocks.GREEN_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS_PANE);

    private static boolean isGlass(BlockState state) {
        return GLASS_BLOCKS.contains(state.getBlock());
    }

    /**
     * How strongly a glass tint reads over whatever's beneath it — enough to see color, not enough to hide the floor.
     */
    private static final double GLASS_TINT_STRENGTH = 0.3;

    /**
     * Plain/undyed glass's tint color — used for {@link Blocks#GLASS}/{@link Blocks#GLASS_PANE},
     * which have no {@code MapColor} of their own (see {@link #glassTintPixel}) and, being
     * connected/multipart-shaped models, aren't a safe {@link BlockTextureColors} read either.
     * Already ABGR-packed (see the class doc on {@code calculateRGBColor}'s layout), matching
     * every other value {@link #glassTintPixel} can return.
     */
    private static final int PLAIN_GLASS_TINT_ABGR = packAbgr(0xC8E8F0, 255);

    /**
     * Returns the already-ABGR-packed tint color for {@code state} if it's a glass block, else
     * {@code null}. Deliberately does <b>not</b> go through {@link BlockTextureColors}: glass
     * panes (colored or plain) are built from a "multipart" blockstate — verified against the
     * decompiled model JSON, each connection direction is its own conditional sub-model — and a
     * baked model composed that way doesn't reliably resolve a real representative particle-icon
     * sprite the way a plain single-texture block does, silently landing on
     * {@code BlockTextureColors}'s missing-texture fallback (flat gray) instead of the pane's
     * actual color. Stained/tinted glass already has a real, dye-accurate {@code MapColor} set
     * directly in {@code Blocks.java} ({@code stainedGlass(color)} calls {@code .mapColor(color)}
     * itself) — reusing that sidesteps the unreliable texture read entirely, and is arguably a
     * *better* source than a texture average anyway, since stained glass textures bake in bright
     * highlight/streak pixels that would otherwise wash a naive average out lighter than the
     * block's true, saturated color.
     */
    private static Integer glassTintPixel(BlockState state, MapColor mapColor) {
        if (!isGlass(state)) return null;
        if (mapColor != MapColor.NONE) {
            return mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
        }
        return PLAIN_GLASS_TINT_ABGR;
    }

    /**
     * Smoothly ramps from {@link #RELIEF_LOW} (at {@code delta <= -RELIEF_THRESHOLD}) through
     * 1.0 (flat, {@code delta == 0}) to {@link #RELIEF_HIGH} (at {@code delta >= RELIEF_THRESHOLD})
     * — a continuous function of the north-neighbor height delta, replacing what used to be a
     * hard 3-value step with nothing in between. That discontinuity, not any water/ice/biome
     * data issue, was the actual source of this map's recurring "line"/"checkerboard" reports:
     * any two adjacent columns straddling the old threshold got a sudden brightness jump against
     * each other, on every kind of terrain and content alike. Public so {@code SolarisTexture}'s
     * globe de-relief pass can recompute this exact same curve rather than duplicating (and now
     * risking drifting out of sync with) the formula.
     */
    public static double relief(double delta) {
        double t = Mth.clamp(delta / RELIEF_THRESHOLD, -1.0, 1.0);
        return t >= 0 ? Mth.lerp(t, 1.0, RELIEF_HIGH) : Mth.lerp(-t, 1.0, RELIEF_LOW);
    }

    public static int[] sample(Level level, ChunkPos pos) {
        int[] pixels = new int[256];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                cursor.set(worldX, surfaceY, worldZ);

                BlockState state = level.getBlockState(cursor);
                MapColor mapColor = state.getMapColor(level, cursor);
                // Topmost glass layer's own tint, captured on the way down — see
                // glassTintPixel's doc. Only the first one found (nearest the surface) counts, so
                // stacked panes don't compound into an ever-thicker tint. Already ABGR-packed.
                Integer glassTintAbgr = glassTintPixel(state, mapColor);

                // Skip blocks with no real map color (signs, torches, flower pots, carpets,
                // etc.) — mirrors vanilla's own do-while descent in MapItem.update, so a pixel
                // shows whatever's actually beneath a colorless decoration instead of reading
                // as "unexplored" (MapColor.NONE's calculateRGBColor returns 0, which
                // SolarisTexture treats as no-data and paints as fog). Also descends past
                // BlockColorOverrides.isTransparent blocks (short/tall grass) and all glass
                // variants (see isGlass) the same way, so clutter — or a window — on top of real
                // terrain doesn't hide it.
                //
                // Crucially, a block with a BlockColorOverrides entry is NEVER descended past,
                // even if its own vanilla MapColor is NONE — a huge number of blocks (rails,
                // fences, doors, most redstone components, iron bars, banners...) default to
                // MapColor.NONE simply because nobody at Mojang ever bothered to set one, not
                // because they're meant to be invisible on a map. Checking the override FIRST is
                // what actually lets those show up at all; an override map entry alone (without
                // this check) would silently never be reached, since the loop below would already
                // have walked straight past the block before anything downstream ever looked at
                // what it was. Glass is deliberately exempt from that override-wins rule (see
                // isGlass) — it's always descended past regardless, since it's never meant to be
                // an opaque final color, just a tint layered on afterward.
                int minY = level.getMinBuildHeight() + 1;
                while (surfaceY > minY && (BlockColorOverrides.isTransparent(state.getBlock()) || isGlass(state) ||
                        (mapColor == MapColor.NONE && BlockColorOverrides.get(state.getBlock()) == null))) {
                    surfaceY--;
                    cursor.set(worldX, surfaceY, worldZ);
                    state = level.getBlockState(cursor);
                    mapColor = state.getMapColor(level, cursor);
                    if (glassTintAbgr == null) {
                        glassTintAbgr = glassTintPixel(state, mapColor);
                    }
                }

                Holder<Biome> biomeHolder = level.getBiome(cursor);
                Biome biome = biomeHolder.value();

                // Guard against the chunk to the north not being loaded yet — see the class doc
                // above on why an unguarded lookup there is what actually caused the streaks.
                // Also skip entirely for kelp/seagrass — WORLD_SURFACE returns the plant's own
                // top block for a column where it reaches the water surface (MapColor.PLANT, not
                // WATER), so it takes the relief-shaded PLANT branch below unless excluded here;
                // kelp height is essentially random per-column (1-25 blocks), which the relief
                // comparison misreads as real terrain elevation, producing a noisy, chunk-edge
                // -aligned pattern across any water with a kelp/seagrass forest in it. Ice/iceberg
                // gets the same exemption for the same reason — see isIce's doc.
                //
                // reliefFactor itself is a smooth, continuous ramp from RELIEF_LOW through 1.0 to
                // RELIEF_HIGH (see relief() below) — it used to be a hard 3-value step (exactly
                // RELIEF_LOW, 1.0, or RELIEF_HIGH, nothing between), which meant ANY column whose
                // north-neighbor height delta crossed the threshold got a sudden, discontinuous
                // brightness jump against its immediate neighbor. That's what actually produced
                // every "line"/"checkerboard" report across this whole investigation — not
                // water, not ice, not biome data, but this discontinuity applied indiscriminately
                // to every pixel (a player-built structure showed it too, with zero data noise
                // involved, which is what finally pinned it down). Darker/blue tones show a
                // brightness-multiplier's effect more visibly than lighter ones, which is why
                // water/ice were the most-reported cases, but the mechanism was never specific to
                // them.
                double reliefFactor = 1.0;
                if (!isAquaticPlant(state) && !isIce(state) && level.hasChunk(worldX >> 4, (worldZ - 1) >> 4)) {
                    int northY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ - 1) - 1;
                    reliefFactor = relief((surfaceY - northY) * RELIEF_NEIGHBOR_SCALE);
                }

                int finalPixel;
                Integer override = BlockColorOverrides.get(state.getBlock());
                if (override != null) {
                    // A curated, final color — not run through vanilla's MapColor branches (no
                    // biome tint to apply, it's not a biome-dependent color), but still relief
                    // -shaded like everything else so overridden blocks aren't visually flat
                    // against shaded terrain around them.
                    finalPixel = scaleBrightness(packAbgr(override, 255), reliefFactor);
                } else if (mapColor == MapColor.WATER || state.is(Blocks.ICE)) {
                    // Plain surface ICE — specifically not PACKED_ICE/BLUE_ICE, which stay their
                    // own distinct MapColor.ICE below since those are deliberately-placed
                    // iceberg material worth standing out — is worldgen's own "freeze_top_layer"
                    // feature (verified in cold_ocean.json/frozen_ocean.json/etc.) converting
                    // scattered individual surface-water columns to ice, patchily, within
                    // cold-temperature ocean biomes specifically. Every one of those columns
                    // previously fell through to the generic branch below and got MapColor.ICE's
                    // flat pale color — a stark jump from adjacent open water's much darker,
                    // floor-blended tint — which is what actually produced the persistent
                    // "checkerboard" reported in cold/frozen/deep-cold ocean and nowhere else
                    // (freeze_top_layer isn't present in warm/lukewarm/regular ocean at all).
                    // Routing it through the same sampleWater() blend as real water instead
                    // makes frozen-over columns read as a shade of water, not a different
                    // material entirely.
                    finalPixel = sampleWater(level, cursor, worldX, worldZ, surfaceY, biome,
                            biomeHolder.is(BiomeTags.IS_OCEAN));
                } else if (mapColor == MapColor.GRASS) {
                    finalPixel = scaleBrightness(packAbgr(biome.getGrassColor(worldX, worldZ), 255), reliefFactor);
                } else if (mapColor == MapColor.PLANT) {
                    finalPixel = scaleBrightness(packAbgr(biome.getFoliageColor(), 255), reliefFactor);
                } else {
                    // Solaris's own color source in place of vanilla's MapColor bucket — see
                    // BlockTextureColors's class doc. BlockColorOverrides (checked first, above)
                    // still wins for the handful of blocks a flat curated color reads better on
                    // than a texture average (flowers-as-dye-color, most obviously).
                    finalPixel = scaleBrightness(packAbgr(BlockTextureColors.get(state), 255), reliefFactor);
                }

                if (glassTintAbgr != null) {
                    // Applied after relief shading, not scaled by it — same treatment
                    // sampleWater's own tint gets over its floor color, for the same reason: this
                    // is a translucent layer sitting on top of the real surface, not part of the
                    // surface's own relief.
                    finalPixel = blendAbgr(finalPixel, glassTintAbgr, GLASS_TINT_STRENGTH);
                }

                pixels[lz * 16 + lx] = finalPixel;
            }
        }

        return pixels;
    }

    /** Paired output of {@link #sampleHeights} — height plus whether that column's surface is water. */
    public static final class HeightSample {

        public final int[] heights;
        public final boolean[] water;

        private HeightSample(int[] heights, boolean[] water) {
            this.heights = heights;
            this.water = water;
        }
    }

    /**
     * A separate, much cheaper pass — just the raw {@code WORLD_SURFACE} height (and whether
     * that surface is water) per column, no biome/relief work — for {@code
     * SolarisGlobeRenderer}'s terrain-relief displacement and {@code SolarisTexture}'s globe
     * de-relief pass. Kept independent of {@link #sample} rather than folded into its return
     * value so that method's carefully-tuned pixel logic didn't need touching to add this.
     *
     * The water flag matters: unlike every other surface type, water pixels never had relief
     * shading applied to their color in {@link #sample} in the first place (see the {@code
     * MapColor.WATER} branch there, which calls {@link #sampleWater} directly, bypassing {@code
     * scaleBrightness} entirely) — water's own surface height is otherwise almost always dead
     * flat at sea level anyway, except for tiny noise from kelp/seagrass poking above it, which
     * a relief-based effect misreads as terrain bumps if not excluded.
     */
    public static HeightSample sampleHeights(Level level, ChunkPos pos) {
        int[] heights = new int[256];
        boolean[] water = new boolean[256];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                cursor.set(worldX, surfaceY, worldZ);
                BlockState state = level.getBlockState(cursor);

                // Same descend-past-clutter behavior as sample()'s color pass (including the
                // override-takes-priority check and the glass exemption — see the comments
                // there), so relief/globe displacement reads the real ground height rather than a
                // grass blade's, or a greenhouse roof's.
                int minY = level.getMinBuildHeight() + 1;
                while (surfaceY > minY && (BlockColorOverrides.isTransparent(state.getBlock()) || isGlass(state) ||
                        (state.getMapColor(level, cursor) == MapColor.NONE &&
                                BlockColorOverrides.get(state.getBlock()) == null))) {
                    surfaceY--;
                    cursor.set(worldX, surfaceY, worldZ);
                    state = level.getBlockState(cursor);
                }

                heights[lz * 16 + lx] = surfaceY;
                // Named for its original, dominant case (water), but really means "exempt from
                // SolarisTexture's globe de-relief recompute" — ice/iceberg needs the exact same
                // exemption there as it does in sample()'s own relief calc, for the same reason.
                water[lz * 16 + lx] = state.getMapColor(level, cursor) == MapColor.WATER || isAquaticPlant(state) ||
                        isIce(state);
            }
        }
        return new HeightSample(heights, water);
    }

    /**
     * Water blends the real floor block's color underneath a blue tint, rather than showing
     * either extreme on its own: pure floor color (tried first, per "let us see all of blocks
     * in river" — but with nothing to mark it as water at all, deep water just looked like a
     * gray blob of whatever the seabed happened to be) or pure depth-shaded water color with
     * no floor at all (tried next, per a JourneyMap reference asking for visible seabed
     * shading — but that discarded the one thing a later reference screenshot specifically
     * asked to keep: something built underwater, like a railway, should still show through as
     * its own recognizable shape, just tinted blue, the way JourneyMap renders it). Blending
     * both is what actually satisfies all of that at once — floor material provides the shape,
     * the tint is what marks it as underwater, and tint strength still scales with depth so
     * shallow water reads clearly while deep water reads as properly filled.
     * {@link SolarisConfig#WATER_OPACITY} controls how strong that tint gets;
     * {@link SolarisConfig#WATER_DEEP_ONLY}, if enabled, pins ordinary sea-level water to the
     * lightest tint regardless of actual depth, so only real trenches below the configured Y
     * get the full curve. Both only apply to ocean biomes ({@code BiomeTags.IS_OCEAN}) —
     * rivers, ponds, and every other water body always render at
     * {@link #MAX_TINT_STRENGTH}, full stop. Per the reasoning behind the request: there's
     * nothing interesting to see under a river, so it's not worth the visual noise of variable
     * transparency there — the floor-peeking effect is worth keeping specifically for oceans,
     * where a monument, ruins, or a player-built structure might actually be down there.
     */
    private static int sampleWater(Level level, BlockPos.MutableBlockPos cursor, int worldX, int worldZ,
                                   int surfaceY, Biome biome, boolean isOcean) {
        int minY = Math.max(level.getMinBuildHeight() + 1, surfaceY - MAX_WATER_SCAN);
        MapColor floorColor = MapColor.NONE;
        int floorY = minY;
        for (int y = surfaceY - 1; y >= minY; y--) {
            cursor.set(worldX, y, worldZ);
            MapColor mc = level.getBlockState(cursor).getMapColor(level, cursor);
            if (mc != MapColor.WATER && mc != MapColor.NONE) {
                floorColor = mc;
                floorY = y;
                break;
            }
        }

        int floorPixel = floorColor == MapColor.NONE ? MapColor.STONE.calculateRGBColor(MapColor.Brightness.LOW) :
                floorColor.calculateRGBColor(MapColor.Brightness.NORMAL);
        int tintPixel = packAbgr(blendedWaterColor(level, worldX, worldZ, surfaceY, biome), 255);

        double tintStrength;
        if (!isOcean) {
            tintStrength = MAX_TINT_STRENGTH;
        } else {
            boolean forceShallow = SolarisConfig.WATER_DEEP_ONLY.get() &&
                    surfaceY >= SolarisConfig.WATER_DEEP_Y_THRESHOLD.get();
            if (forceShallow) {
                tintStrength = MIN_TINT_STRENGTH;
            } else {
                int depth = surfaceY - floorY;
                double depthFactor = Math.min(1.0, depth / 12.0);
                double shallowTint = MIN_TINT_STRENGTH + 0.55 * SolarisConfig.WATER_OPACITY.get();
                double deepTint = 0.8 + 0.2 * SolarisConfig.WATER_OPACITY.get();
                tintStrength = Math.max(MIN_TINT_STRENGTH, shallowTint + (deepTint - shallowTint) * depthFactor);

                if (surfaceY >= SEA_LEVEL_MIN && surfaceY <= SEA_LEVEL_MAX) {
                    tintStrength += SEA_LEVEL_BONUS;
                }
                tintStrength = Math.min(MAX_TINT_STRENGTH, tintStrength);
            }
        }

        return blendAbgr(floorPixel, tintPixel, tintStrength);
    }

    /**
     * Step (blocks) between sampled neighbor points for {@link #blendedWaterColor} — the grid spacing itself isn't
     * user-tunable, only its extent.
     */
    private static final int WATER_BLEND_STEP = 4;

    /**
     * Averages {@code Biome#getWaterColor()} over a grid of sample points (this pixel plus
     * neighbors out to {@link SolarisConfig#WATER_BLEND_RADIUS} blocks, at {@link
     * #WATER_BLEND_STEP} spacing — the default 8 gives the original 5x5/24-neighbor grid) instead
     * of reading it raw at a single point. Every other color source on this map already goes
     * through some smoothing — grass/foliage get the same treatment vanilla's own map does
     * implicitly via biome noise being fairly gradual there, land relief is an explicit
     * north-neighbor comparison — but a raw, single-point biome lookup for water tint left hard,
     * distinctly grid-aligned lighter/darker seams wherever two ocean sub-biomes (regular/warm/
     * lukewarm/cold/frozen, plus their deep variants) meet, reported as "fine lines of lighter
     * blue around every chunk". Blending smooths exactly that, the same way vanilla's own
     * {@code BiomeColors} resolvers blend grass/foliage/water color over a neighborhood rather
     * than reading one biome's raw value.
     *
     * User-tunable (radius 0-16, snapped to a multiple of {@link #WATER_BLEND_STEP}) rather than
     * fixed, since a follow-up report was the opposite of "still not enough blending" — some
     * players specifically like the sharper, more grid-visible look blending was built to smooth
     * away. Radius 0 skips the neighbor loop entirely and returns the biome's own raw color,
     * exactly matching pre-blending behavior. Default (8) is unchanged from before this became
     * configurable, so nobody's map changes appearance without them touching the setting.
     */
    private static int blendedWaterColor(Level level, int worldX, int worldZ, int surfaceY, Biome centerBiome) {
        int radius = (SolarisConfig.WATER_BLEND_RADIUS.get() / WATER_BLEND_STEP) * WATER_BLEND_STEP;
        if (radius <= 0) return centerBiome.getWaterColor();

        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        for (int dz = -radius; dz <= radius; dz += WATER_BLEND_STEP) {
            for (int dx = -radius; dx <= radius; dx += WATER_BLEND_STEP) {
                int rgb;
                if (dx == 0 && dz == 0) {
                    rgb = centerBiome.getWaterColor();
                } else {
                    samplePos.set(worldX + dx, surfaceY, worldZ + dz);
                    rgb = level.getBiome(samplePos).value().getWaterColor();
                }
                r += rgb >> 16 & 255;
                g += rgb >> 8 & 255;
                b += rgb & 255;
                count++;
            }
        }
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    /** Applies {@code MapColor.calculateRGBColor}'s channel-swap by hand to a plain 0xRRGGBB color. */
    private static int packAbgr(int rgb, int modifier) {
        int r = (rgb >> 16 & 255) * modifier / 255;
        int g = (rgb >> 8 & 255) * modifier / 255;
        int b = (rgb & 255) * modifier / 255;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    /** Alpha-blends two already-ABGR-packed pixels. */
    private static int blendAbgr(int baseAbgr, int overlayAbgr, double alpha) {
        int a = (int) Math.round(alpha * 255);
        int baseR = baseAbgr & 255;
        int baseG = baseAbgr >> 8 & 255;
        int baseB = baseAbgr >> 16 & 255;
        int ovR = overlayAbgr & 255;
        int ovG = overlayAbgr >> 8 & 255;
        int ovB = overlayAbgr >> 16 & 255;
        int r = (ovR * a + baseR * (255 - a)) / 255;
        int g = (ovG * a + baseG * (255 - a)) / 255;
        int b = (ovB * a + baseB * (255 - a)) / 255;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    /**
     * Scales an already-ABGR-packed pixel's RGB channels by {@code factor}, clamped to a valid byte — the relief
     * shading effect.
     */
    private static int scaleBrightness(int abgr, double factor) {
        if (factor == 1.0) return abgr;
        int a = abgr >>> 24;
        int r = clampChannel((int) ((abgr & 255) * factor));
        int g = clampChannel((int) ((abgr >> 8 & 255) * factor));
        int b = clampChannel((int) ((abgr >> 16 & 255) * factor));
        return a << 24 | b << 16 | g << 8 | r;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
