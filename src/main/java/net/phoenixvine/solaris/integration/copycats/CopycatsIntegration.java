package net.phoenixvine.solaris.integration.copycats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.lang.reflect.Method;

public final class CopycatsIntegration {

    public static final String MOD_ID = "copycats";
    private static final String INTERFACE_NAME = "com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity";

    private static boolean broken = false;
    private static Class<?> copycatBlockEntityClass;
    private static Method hasCustomMaterialMethod;
    private static Method getMaterialMethod;

    private CopycatsIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static BlockState getMaterialState(Level level, BlockPos pos) {
        if (broken) return null;
        try {
            ensureReflectionReady();
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null || !copycatBlockEntityClass.isInstance(entity)) return null;

            boolean hasCustom = (boolean) hasCustomMaterialMethod.invoke(entity);
            if (!hasCustom) return null;
            return (BlockState) getMaterialMethod.invoke(entity);
        } catch (Throwable t) {
            broken = true;
            PhoenixSolaris.LOGGER.error(
                    "Copycats is present but reading its material state failed — copycat blocks will render as " +
                            "their own default color for the rest of this session.",
                    t);
            return null;
        }
    }

    private static void ensureReflectionReady() throws ReflectiveOperationException {
        if (copycatBlockEntityClass != null) return;
        copycatBlockEntityClass = Class.forName(INTERFACE_NAME);
        hasCustomMaterialMethod = copycatBlockEntityClass.getMethod("hasCustomMaterial");
        getMaterialMethod = copycatBlockEntityClass.getMethod("getMaterial");
    }
}
