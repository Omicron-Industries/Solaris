package net.phoenixvine.solaris.client;

import net.minecraftforge.eventbus.api.IEventBus;

public class PhoenixSolarisClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(SolarisKeybinds.class);
    }
}
