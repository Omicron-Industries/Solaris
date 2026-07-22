package net.phoenixvine.solaris.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.solaris.client.render.MobIconOverrides;

public class PhoenixSolarisClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(SolarisKeybinds.class);
        MobIconOverrides.loadConfig();
    }
}
