package net.phoenixvine.solaris.client.overlay;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A layer drawn on top of Solaris's terrain base layer, one chunk at a time. Register with
 * {@link net.phoenixvine.solaris.api.SolarisAPI#registerOverlay}. Colors are standard
 * ARGB ints (alpha in the top byte) — an overlay can return a fully-opaque color to paint
 * over terrain entirely, or a partially-transparent one to tint it (e.g. Phoenix Domains
 * drawing claim borders/colors without hiding the terrain underneath).
 */
public interface SolarisOverlay {

    /** Returns the tint color for this chunk, or empty to leave the terrain layer untouched. */
    Optional<Integer> colorAt(ResourceLocation dimension, int chunkX, int chunkZ);

    /** Higher-priority overlays composite later (on top). Default 0. */
    default int priority() {
        return 0;
    }
}
