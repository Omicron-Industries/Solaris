package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.phoenixvine.solaris.config.SolarisConfig;

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

    /** How far straight down through a water column to hunt for the real floor block. */
    private static final int MAX_WATER_SCAN = 40;

    private static final double RELIEF_LOW = 0.62;
    private static final double RELIEF_HIGH = 1.3;

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

                // Skip blocks with no real map color (signs, torches, flower pots, carpets,
                // etc.) — mirrors vanilla's own do-while descent in MapItem.update, so a pixel
                // shows whatever's actually beneath a colorless decoration instead of reading
                // as "unexplored" (MapColor.NONE's calculateRGBColor returns 0, which
                // SolarisTexture treats as no-data and paints as fog).
                int minY = level.getMinBuildHeight() + 1;
                while (mapColor == MapColor.NONE && surfaceY > minY) {
                    surfaceY--;
                    cursor.set(worldX, surfaceY, worldZ);
                    state = level.getBlockState(cursor);
                    mapColor = state.getMapColor(level, cursor);
                }

                Holder<Biome> biomeHolder = level.getBiome(cursor);
                Biome biome = biomeHolder.value();

                // Guard against the chunk to the north not being loaded yet — see the class doc
                // above on why an unguarded lookup there is what actually caused the streaks.
                double reliefFactor = 1.0;
                if (level.hasChunk(worldX >> 4, (worldZ - 1) >> 4)) {
                    int northY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ - 1) - 1;
                    double relief = (surfaceY - northY) * 0.8;
                    // Threshold raised somewhat from vanilla's 0.6 — at true 1:1 scale (no
                    // zoomed-out averaging like the in-game map has) a single-block bump too
                    // small to notice on foot could otherwise cross it. Not pushed further than
                    // this, though: the real source of the persistent line artifacts was the
                    // permanent chunk-load-order fallback above, now fixed at the source in
                    // ChunkColorEvents, so this only needs to filter genuine single-block noise.
                    if (relief > 1.2) reliefFactor = RELIEF_HIGH;
                    else if (relief < -1.2) reliefFactor = RELIEF_LOW;
                }

                int finalPixel;
                if (mapColor == MapColor.WATER) {
                    finalPixel = sampleWater(level, cursor, worldX, worldZ, surfaceY, biome,
                            biomeHolder.is(BiomeTags.IS_OCEAN));
                } else if (mapColor == MapColor.GRASS) {
                    finalPixel = scaleBrightness(packAbgr(biome.getGrassColor(worldX, worldZ), 255), reliefFactor);
                } else if (mapColor == MapColor.PLANT) {
                    finalPixel = scaleBrightness(packAbgr(biome.getFoliageColor(), 255), reliefFactor);
                } else {
                    // Already NativeImage-ready — see the class doc on calculateRGBColor above.
                    finalPixel = scaleBrightness(mapColor.calculateRGBColor(MapColor.Brightness.NORMAL), reliefFactor);
                }

                pixels[lz * 16 + lx] = finalPixel;
            }
        }

        return pixels;
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
        int tintPixel = packAbgr(biome.getWaterColor(), 255);

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
