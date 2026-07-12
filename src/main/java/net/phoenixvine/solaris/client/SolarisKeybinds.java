package net.phoenixvine.solaris.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SolarisKeybinds {

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.phoenix_solaris.map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.phoenix_solaris");

    public static final KeyMapping NEW_WAYPOINT = new KeyMapping(
            "key.phoenix_solaris.new_waypoint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.phoenix_solaris");

    public static final KeyMapping TOGGLE_COMPASS = new KeyMapping(
            "key.phoenix_solaris.toggle_compass",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.phoenix_solaris");

    public static final KeyMapping TOGGLE_MINIMAP_ROTATE = new KeyMapping(
            "key.phoenix_solaris.toggle_minimap_rotate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.phoenix_solaris");

    public static final KeyMapping CYCLE_MINIMAP_STYLE = new KeyMapping(
            "key.phoenix_solaris.cycle_minimap_style",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.phoenix_solaris");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
        event.register(NEW_WAYPOINT);
        event.register(TOGGLE_COMPASS);
        event.register(TOGGLE_MINIMAP_ROTATE);
        event.register(CYCLE_MINIMAP_STYLE);
    }
}
