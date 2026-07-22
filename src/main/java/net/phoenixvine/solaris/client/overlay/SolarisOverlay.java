package net.phoenixvine.solaris.client.overlay;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface SolarisOverlay {

    Optional<Integer> colorAt(ResourceLocation dimension, int chunkX, int chunkZ);

    default int priority() {
        return 0;
    }
}
