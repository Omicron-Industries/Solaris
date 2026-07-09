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

/**
 * Optional integration with GTCEu: shows already-revealed ore veins as markers on Solaris's
 * map, and lets clicking a vein in the prospector scanner's own in-item map pin a Solaris
 * waypoint at it. GTCEu ships its own integrations for Xaero, JourneyMap, and FTB Chunks —
 * Solaris is a new mod GTCEu's maintainers can't have known to support, so this reads GTCEu's
 * own client-side data structures directly instead of waiting on upstream compat that would
 * never come. {@code gtceu} is declared as a non-mandatory, client-only {@code mods.toml}
 * dependency, so nothing in this class may be touched unless {@link #isAvailable()} is true
 * first, and every call must happen from code that only ever runs on the physical client —
 * mirrors {@code DomainsChroniclesIntegration}/{@code DomainsSolarisIntegration} over in
 * Phoenix Domains.
 *
 * Deliberately only surfaces veins already revealed to the player (prospecting, surface
 * indicators, etc.) — this can't show anything the player hasn't already been shown by
 * GTCEu's own systems. It's a different way to look at the same revealed data, not an X-ray.
 */
public final class GtceuIntegration {

    public static final String GTCEU_MOD_ID = "gtceu";

    private static boolean initialized = false;
    private static boolean initBroken = false;

    private GtceuIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }

    /**
     * Registers Solaris as a click-to-waypoint target for GTCEu's prospector scanner map, and
     * forces on the internal switch that gates it — safe to call every tick, real work only
     * happens once. GTCEu's own {@code WaypointManager.init()} only ever turns that switch on
     * for Xaero/JourneyMap/FTB Chunks (it hardcodes those three), so a mod it's never heard of
     * has no way to opt in through the public API alone; the field itself is just a private
     * {@code static boolean}, not final, so flipping it by reflection is far less invasive
     * than mixin-patching the check itself, and breaks the same way a raw API dependency
     * would if GTCEu ever renames it (caught and latched below, same as everywhere else in
     * this class).
     */
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

    /** Call only after {@link #isAvailable()} has returned true. */
    public static List<GtOreVein> getVeinsInArea(ResourceLocation dimension, int minX, int minZ, int width,
                                                 int depth) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        return GtVeinRegistry.getInArea(dimKey, minX, minZ, width, depth);
    }

    /**
     * A GTCEu-free snapshot of one revealed ore vein — safe to hold onto without pulling in
     * any GTCEu classes. {@code icon} is the vein's primary material's raw ore item (e.g. Raw
     * Copper) — that item's own vanilla-rendered icon stands in for a generic marker shape, so
     * a vein reads as "the actual ore" on the map instead of an arbitrary symbol. Falls back to
     * {@link ItemStack#EMPTY} (rendered as the generic STAR shape by the caller) if the
     * material has no raw ore item registered.
     */
    public record GtOreVein(BlockPos center, String name, int colorArgb, ItemStack icon) {}
}
