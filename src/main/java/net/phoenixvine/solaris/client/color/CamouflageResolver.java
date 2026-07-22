package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.solaris.integration.copycats.CopycatsIntegration;
import net.phoenixvine.solaris.integration.domumornamentum.DomumOrnamentumIntegration;
import net.phoenixvine.solaris.integration.framedblocks.FramedBlocksIntegration;

public final class CamouflageResolver {

    private CamouflageResolver() {}

    public static BlockState resolveState(Level level, BlockPos pos, BlockState state) {
        if (FramedBlocksIntegration.isAvailable()) {
            BlockState camo = FramedBlocksIntegration.getCamoState(level, pos);
            if (camo != null) return camo;
        }
        if (CopycatsIntegration.isAvailable()) {
            BlockState material = CopycatsIntegration.getMaterialState(level, pos);
            if (material != null) return material;
        }
        return null;
    }

    public static Integer resolveDirectRgb(Level level, BlockPos pos, BlockState state) {
        if (DomumOrnamentumIntegration.isAvailable()) {
            return DomumOrnamentumIntegration.getAverageRgb(level, pos);
        }
        return null;
    }
}
