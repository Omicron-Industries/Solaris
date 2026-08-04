package net.phoenixvine.solaris.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.phoenixvine.solaris.client.render.CaveTileCache;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.MobIconOverrides;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;

public class PhoenixSolarisClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(SolarisKeybinds.class);
        MobIconOverrides.loadConfig();

        modEventBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event));
        modEventBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event));
    }

    private static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SolarisConfig.SPEC) return;
        SolarisTexture.invalidateAll();
        MapTileCache.clearAll();
        CaveTileCache.clearAll();
    }
}
