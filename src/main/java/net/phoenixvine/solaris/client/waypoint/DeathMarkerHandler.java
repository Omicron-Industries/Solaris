package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

/** Auto-drops a waypoint at the player's own death location, so lost items are findable again. */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DeathMarkerHandler {

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!SolarisConfig.DEATH_MARKERS.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) return;
        // Per the "shouldn't be placed when waypoints are off" requirement — a silent skip, not
        // a message, since dying is already disruptive enough without an extra chat line.
        if (!WaypointManager.canPlace(mc.player.level().dimension().location())) return;

        Waypoint waypoint = new Waypoint("Death", mc.player.level().dimension().location(),
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), "FF3B30");
        waypoint.icon = WaypointIcon.DANGER.name();
        waypoint.category = "Deaths";
        WaypointManager.add(waypoint);
    }
}
