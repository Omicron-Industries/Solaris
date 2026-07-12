package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Samples a local "cave slice" around a given Y level, for the small radius of chunks near
 * the player when they're underground — unlike {@link ChunkColorSampler} (always the world
 * surface, persistently cached per chunk), this is deliberately uncached and recomputed
 * fresh each time it's needed: the result depends on which Y you're viewing from, not just
 * which chunk, and only areas near the player should ever be revealed (no x-raying unvisited
 * caves), matching how cave-mode in other minimap mods behaves.
 *
 * Scans each column from just above the given center Y downward, stopping at the first
 * block with a real map color — usually the ceiling or a wall right above/beside the
 * player. Open air within the scan band (nothing solid found) samples as
 * {@link MapColor#NONE}, whose {@link MapColor#calculateRGBColor} is already fully
 * transparent, so the caller can substitute its own "fog" color for those pixels.
 *
 * Shading reuses {@link ChunkColorSampler}'s north-neighbor relief technique instead of the
 * old flat "which third of the scan band" banding: comparing each column's found depth to its
 * north neighbor's is what actually reads as ridges/walls/drop-offs, since most of any single
 * cave slice sits in the same coarse depth band and a distance-from-centerY split barely
 * varies across it.
 */
public final class CaveColorSampler {

    private static final int UNDERGROUND_THRESHOLD = 4;
    private static final int SCAN_ABOVE = 3;
    private static final int SCAN_BELOW = 12;

    private CaveColorSampler() {}

    /** True if {@code pos} is far enough below the world-surface heightmap to count as "underground". */
    public static boolean isUnderground(Level level, BlockPos pos) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        return pos.getY() < surfaceY - UNDERGROUND_THRESHOLD;
    }

    public static int[] sample(Level level, ChunkPos pos, int centerY) {
        int[] pixels = new int[256];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int top = Math.min(level.getMaxBuildHeight() - 1, centerY + SCAN_ABOVE);
        int bottom = Math.max(level.getMinBuildHeight() + 1, centerY - SCAN_BELOW);

        // Sample a 1-column border around the 16x16 chunk too, so every real pixel has a real
        // north neighbor to diff against instead of guessing at the chunk edge.
        int stride = 18;
        int[] foundYs = new int[stride * stride];
        MapColor[] foundColors = new MapColor[stride * stride];
        for (int lz = -1; lz <= 16; lz++) {
            for (int lx = -1; lx <= 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;

                MapColor found = MapColor.NONE;
                int foundY = bottom;
                for (int y = top; y >= bottom; y--) {
                    cursor.set(worldX, y, worldZ);
                    MapColor mapColor = level.getBlockState(cursor).getMapColor(level, cursor);
                    if (mapColor != MapColor.NONE) {
                        found = mapColor;
                        foundY = y;
                        break;
                    }
                }
                int idx = (lz + 1) * stride + (lx + 1);
                foundYs[idx] = foundY;
                foundColors[idx] = found;
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int idx = (lz + 1) * stride + (lx + 1);
                MapColor found = foundColors[idx];
                if (found == MapColor.NONE) {
                    pixels[lz * 16 + lx] = 0;
                    continue;
                }

                int foundY = foundYs[idx];
                int northIdx = lz * stride + (lx + 1);
                // If the north column is open air (no hit), it has no real depth to diff
                // against — falling back to its default "bottom" value would read as a cliff
                // that isn't really there, so treat it as flush with this column instead.
                int northFoundY = foundColors[northIdx] != MapColor.NONE ? foundYs[northIdx] : foundY;
                double relief = (foundY - northFoundY) * 0.8 + (((lx + lz) & 1) - 0.5) * 0.4;

                // Continuous ramp, not a hard 3-value HIGH/NORMAL/LOW step — the exact same
                // discontinuity ChunkColorSampler's own relief shading used to have, which turned
                // out to be the real cause of this whole map's "line"/"checkerboard" artifacts
                // (see ChunkColorSampler.relief's doc). This sampler kept the old discrete version
                // because it only ever covered a small reveal bubble around the player, where the
                // artifact was subtle — now that the Nether renders through this same sampler
                // across a much larger area (see SolarisTexture.isCaveSliceMode), the same
                // discontinuity would be far more visible if left as-is.
                pixels[lz * 16 + lx] = scaleBrightness(found.calculateRGBColor(MapColor.Brightness.NORMAL),
                        ChunkColorSampler.relief(relief));
            }
        }

        return pixels;
    }

    /** Scales an already-ABGR-packed pixel's RGB channels by {@code factor}, clamped to a valid byte — same convention as {@code ChunkColorSampler}'s own copy. */
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
