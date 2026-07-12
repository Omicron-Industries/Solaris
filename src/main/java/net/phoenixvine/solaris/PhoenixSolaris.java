package net.phoenixvine.solaris;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.phoenixvine.solaris.client.PhoenixSolarisClient;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.network.SolarisNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixSolaris.MOD_ID)
public class PhoenixSolaris {

    public static final String MOD_ID = "phoenix_solaris";
    public static final Logger LOGGER = LogManager.getLogger();

    public PhoenixSolaris() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        SolarisConfig.register();

        modEventBus.addListener(this::commonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PhoenixSolarisClient.init(modEventBus));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Solaris initializing..."));
        SolarisNetwork.init();
    }
}
