package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class CaveColorSampler {

    private static final int UNDERGROUND_THRESHOLD = 4;
    private static final int SCAN_ABOVE = 3;
    private static final int SCAN_BELOW = 12;

    private static final int LEAF_SCAN_LIMIT = 32;

    private CaveColorSampler() {}

    public static boolean isUnderground(Level level, BlockPos pos) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), surfaceY - 1, pos.getZ());
        int scanned = 0;
        while (scanned < LEAF_SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof LeavesBlock) {
            cursor.move(0, -1, 0);
            scanned++;
        }
        int realSurfaceY = cursor.getY() + 1;

        return pos.getY() < realSurfaceY - UNDERGROUND_THRESHOLD;
    }

    public static int[] sample(Level level, ChunkPos pos, int centerY) {
        int[] pixels = new int[256];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int top = Math.min(level.getMaxBuildHeight() - 1, centerY + SCAN_ABOVE);
        int bottom = Math.max(level.getMinBuildHeight() + 1, centerY - SCAN_BELOW);

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

                int northFoundY = foundColors[northIdx] != MapColor.NONE ? foundYs[northIdx] : foundY;
                double relief = (foundY - northFoundY) * 0.8 + (((lx + lz) & 1) - 0.5) * 0.4;

                pixels[lz * 16 + lx] = scaleBrightness(found.calculateRGBColor(MapColor.Brightness.NORMAL),
                        ChunkColorSampler.relief(relief));
            }
        }

        return pixels;
    }

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
