package net.phoenixvine.solaris.integration.gtceu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.lang.reflect.Field;
import java.util.List;

public final class GtceuIntegration {

    public static final String GTCEU_MOD_ID = "gtceu";

    private static boolean initialized = false;
    private static boolean initBroken = false;

    private GtceuIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }

    public static void init() {
        if (initialized || initBroken) return;
        try {
            com.gregtechceu.gtceu.integration.map.WaypointManager
                    .registerWaypointHandler(new SolarisWaypointHandler());
            Field activeField = com.gregtechceu.gtceu.integration.map.WaypointManager.class
                    .getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(null, true);
            initialized = true;
        } catch (Throwable t) {
            initBroken = true;
            PhoenixSolaris.LOGGER.error(
                    "GTCEu is present but registering Solaris as a prospector waypoint target failed — " +
                            "clicking veins in the prospector's map won't pin Solaris waypoints this session.",
                    t);
        }
    }

    public static List<GtOreVein> getVeinsInArea(ResourceLocation dimension, int minX, int minZ, int width,
                                                 int depth) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        return GtVeinRegistry.getInArea(dimKey, minX, minZ, width, depth);
    }

    public record GtOreVein(BlockPos center, String name, int colorArgb, ItemStack icon) {}
}
