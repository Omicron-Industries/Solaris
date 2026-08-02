package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.event.WaypointEvent;
import net.phoenixvine.solaris.client.render.MinimapStyle;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SolarisClientTickHandler {

    private static final double WAYPOINT_REACH_RADIUS_SQ = 8.0 * 8.0;
    private static final int WAYPOINT_REACH_CHECK_INTERVAL_TICKS = 10;
    private static int reachCheckTickCounter = 0;
    private static final Set<String> NEAR_WAYPOINT_IDS = new HashSet<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (GtceuIntegration.isAvailable()) GtceuIntegration.init();

        if (++reachCheckTickCounter >= WAYPOINT_REACH_CHECK_INTERVAL_TICKS) {
            reachCheckTickCounter = 0;
            checkWaypointsReached(mc);
        }

        while (SolarisKeybinds.OPEN_MAP.consumeClick()) {
            if (mc.screen != null) continue;

            if (!SolarisAPI.openMap(null)) {
                mc.player.displayClientMessage(Component.literal("The map isn't available right now."), true);
            }
        }

        while (SolarisKeybinds.NEW_WAYPOINT.consumeClick()) {
            if (!WaypointManager.canPlace(mc.level.dimension().location())) {
                mc.player.displayClientMessage(Component.literal("Waypoints aren't available here."), true);
                continue;
            }
            String name = "Waypoint " + (WaypointManager.getAll().size() + 1);
            Waypoint w = new Waypoint(name, mc.level.dimension().location(),
                    mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), "FFFFFFFF");
            WaypointManager.add(w);
            mc.player.displayClientMessage(Component.translatable("solaris.waypoint.added", name), true);
        }

        while (SolarisKeybinds.TOGGLE_COMPASS.consumeClick()) {
            boolean on = !SolarisConfig.WAYPOINT_COMPASS.get();
            SolarisConfig.WAYPOINT_COMPASS.set(on);
            SolarisConfig.WAYPOINT_COMPASS.save();
            mc.player.displayClientMessage(
                    Component.literal("Waypoint compass: " + (on ? "ON" : "OFF")), true);
        }

        while (SolarisKeybinds.TOGGLE_MINIMAP_ROTATE.consumeClick()) {
            boolean on = !SolarisConfig.MINIMAP_ROTATE.get();
            SolarisConfig.MINIMAP_ROTATE.set(on);
            SolarisConfig.MINIMAP_ROTATE.save();
            mc.player.displayClientMessage(
                    Component.literal("Minimap rotation: " + (on ? "ON (facing up)" : "OFF (north up)")), true);
        }

        while (SolarisKeybinds.CYCLE_MINIMAP_STYLE.consumeClick()) {
            MinimapStyle current = MinimapStyle.closestTo(SolarisConfig.MINIMAP_SIZE.get(),
                    SolarisConfig.MINIMAP_SHAPE.get());
            MinimapStyle next = current.next();
            SolarisConfig.MINIMAP_SIZE.set(next.size);
            SolarisConfig.MINIMAP_SIZE.save();
            SolarisConfig.MINIMAP_SHAPE.set(next.shape);
            SolarisConfig.MINIMAP_SHAPE.save();
            mc.player.displayClientMessage(Component.literal("Minimap style: " + next.label), true);
        }
    }

    private static void checkWaypointsReached(Minecraft mc) {
        var dimension = mc.level.dimension().location();
        Set<String> stillNear = new HashSet<>();

        for (Waypoint w : WaypointManager.getVisibleForDimension(dimension)) {
            boolean inRange = w.distanceSq(mc.player.getX(), mc.player.getY(), mc.player.getZ()) <=
                    WAYPOINT_REACH_RADIUS_SQ;

            if (inRange) {
                stillNear.add(w.id);
                if (!NEAR_WAYPOINT_IDS.contains(w.id)) {
                    MinecraftForge.EVENT_BUS.post(new WaypointEvent.Reached(mc.player, w));
                }
            }
        }

        NEAR_WAYPOINT_IDS.clear();
        NEAR_WAYPOINT_IDS.addAll(stillNear);
    }
}
