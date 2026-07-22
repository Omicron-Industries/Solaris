package net.phoenixvine.solaris.integration.framedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.solaris.PhoenixSolaris;

import xfacthd.framedblocks.api.block.FramedBlockEntity;
import xfacthd.framedblocks.api.camo.CamoContainer;

public final class FramedBlocksIntegration {

    public static final String MOD_ID = "framedblocks";

    private static boolean broken = false;

    private FramedBlocksIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static BlockState getCamoState(Level level, BlockPos pos) {
        if (broken) return null;
        try {
            if (!(level.getBlockEntity(pos) instanceof FramedBlockEntity fbe)) return null;
            CamoContainer camo = fbe.getCamo();
            if (camo.isEmpty()) return null;
            return camo.getState();
        } catch (Throwable t) {
            broken = true;
            PhoenixSolaris.LOGGER.error(
                    "FramedBlocks is present but reading its camo state failed — framed blocks will render as " +
                            "their own default color for the rest of this session.",
                    t);
            return null;
        }
    }
}
