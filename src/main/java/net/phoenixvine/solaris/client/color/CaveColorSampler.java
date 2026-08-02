package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

public final class CaveColorSampler {

    private static final int UNDERGROUND_THRESHOLD = 5;
    private static final int SCAN_ABOVE = 3;
    private static final int SCAN_BELOW = 12;

    private static final int TERRAIN_SCAN_LIMIT = 192;
    private static final int ROOF_AIR_LOOKAHEAD = 3;

    // Modpacks add planetary/dimension terrain (Ad Astra's Mars/Moon stone, etc.) that vanilla
    // and Forge tags don't recognize as "stone" or "terrain" — an allowlist of terrain blocks is
    // never complete. Instead we blocklist namespaces that are reliably *never* terrain: heavy
    // industrial mods whose pipe/cable/casing stacks can run dense and tall with no air gaps.
    // Anything else defaults to "this is real ground" unless it's leaves or a thin covering.
    private static final Set<String> STRUCTURE_NAMESPACES = Set.of(
            "gtceu", "gregtech", "mekanism", "mekanismgenerators", "mekanismtools", "thermal",
            "immersiveengineering", "ae2", "appliedenergistics2", "industrialforegoing", "create",
            "pneumaticcraft", "refinedstorage", "computercraft", "cc_tweaked", "flux_networks",
            "railcraft", "extendedcrafting", "modern_industrialization", "techreborn");

    private CaveColorSampler() {}

    /**
     * Whether this block is something to search past rather than treat as real ground: leaves,
     * a thin covering with open air right beneath it (roof, floor, attic — any material), or a
     * block from a known industrial/tech mod (whose pipe/cable/casing runs can be dense and tall
     * with no air gaps, defeating the thin-covering check).
     */
    private static boolean isLikelyStructure(Level level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LEAVES)) return true;

        Block block = state.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null && STRUCTURE_NAMESPACES.contains(id.getNamespace())) return true;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        for (int i = 1; i <= ROOF_AIR_LOOKAHEAD; i++) {
            cursor.move(0, -1, 0);
            if (level.getBlockState(cursor).isAir()) return true;
        }
        return false;
    }

    public static boolean isUnderground(Level level, BlockPos pos) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());

        // Walk down from the column's topmost block, skipping over anything that reads as
        // built/grown rather than real ground (leaves, a roof, floors, factory pipe/cable/machine
        // stacks — however tall or dense) so a tower or canopy elsewhere in the column doesn't get
        // mistaken for "the surface" here. Stops at the first real ground block, which is what
        // actually determines depth.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), surfaceY - 1, pos.getZ());
        int scanned = 0;
        while (scanned < TERRAIN_SCAN_LIMIT && cursor.getY() > level.getMinBuildHeight() + 1 &&
                isLikelyStructure(level, cursor, level.getBlockState(cursor))) {
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
        int[] foundRgb = new int[stride * stride];
        boolean[] foundBlock = new boolean[stride * stride];
        for (int lz = -1; lz <= 16; lz++) {
            for (int lx = -1; lx <= 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;

                boolean found = false;
                int rgb = 0;
                int foundY = bottom;
                for (int y = top; y >= bottom; y--) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;

                    // Not every block (especially modded planetary/dimension terrain) sets a
                    // vanilla MapColor — fall back to an averaged texture color like the surface
                    // sampler does, instead of leaving the cell blank.
                    MapColor mapColor = state.getMapColor(level, cursor);
                    rgb = mapColor != MapColor.NONE ? mapColor.calculateRGBColor(MapColor.Brightness.NORMAL) :
                            packAbgr(BlockTextureColors.get(state));
                    found = true;
                    foundY = y;
                    break;
                }
                int idx = (lz + 1) * stride + (lx + 1);
                foundYs[idx] = foundY;
                foundRgb[idx] = rgb;
                foundBlock[idx] = found;
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int idx = (lz + 1) * stride + (lx + 1);
                if (!foundBlock[idx]) {
                    pixels[lz * 16 + lx] = 0;
                    continue;
                }

                int foundY = foundYs[idx];
                int northIdx = lz * stride + (lx + 1);

                int northFoundY = foundBlock[northIdx] ? foundYs[northIdx] : foundY;
                double relief = (foundY - northFoundY) * 0.8 + (((lx + lz) & 1) - 0.5) * 0.4;

                pixels[lz * 16 + lx] = scaleBrightness(foundRgb[idx], ChunkColorSampler.relief(relief));
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

    private static int packAbgr(int rgb) {
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }
}
