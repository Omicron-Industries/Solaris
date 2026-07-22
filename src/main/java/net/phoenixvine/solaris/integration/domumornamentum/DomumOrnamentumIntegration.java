package net.phoenixvine.solaris.integration.domumornamentum;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.color.BlockTextureColors;

import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DomumOrnamentumIntegration {

    public static final String MOD_ID = "domum_ornamentum";

    private static boolean broken = false;

    private DomumOrnamentumIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static Integer getAverageRgb(Level level, BlockPos pos) {
        if (broken) return null;
        try {
            if (!(level.getBlockEntity(pos) instanceof IMateriallyTexturedBlockEntity mtbe)) return null;
            Map<ResourceLocation, Block> components = mtbe.getTextureData().getTexturedComponents();
            if (components.isEmpty()) return null;

            Set<Block> distinctMaterials = new LinkedHashSet<>(components.values());
            long r = 0;
            long g = 0;
            long b = 0;
            int count = 0;
            for (Block material : distinctMaterials) {
                int rgb = BlockTextureColors.get(material.defaultBlockState());
                r += rgb >> 16 & 255;
                g += rgb >> 8 & 255;
                b += rgb & 255;
                count++;
            }
            if (count == 0) return null;
            return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
        } catch (Throwable t) {
            broken = true;
            PhoenixSolaris.LOGGER.error(
                    "Domum Ornamentum is present but reading its material data failed — its blocks will render " +
                            "as their own default color for the rest of this session.",
                    t);
            return null;
        }
    }
}
