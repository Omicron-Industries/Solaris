package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SolarisClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (GtceuIntegration.isAvailable()) GtceuIntegration.init();

        while (SolarisKeybinds.OPEN_MAP.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SolarisMapScreen());
        }

        // Instant-saves at the player's exact position rather than opening a form to fill out
        // first — a keybind pressed mid-gameplay shouldn't interrupt it with a modal. Everything
        // the form used to ask for up front (name, color, icon, coords) is editable afterward
        // from the Waypoints list instead.
        while (SolarisKeybinds.NEW_WAYPOINT.consumeClick()) {
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
    }
}
