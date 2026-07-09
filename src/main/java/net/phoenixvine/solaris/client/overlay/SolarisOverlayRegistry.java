package net.phoenixvine.solaris.client.overlay;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SolarisOverlayRegistry {

    private SolarisOverlayRegistry() {}

    private static final List<SolarisOverlay> OVERLAYS = new CopyOnWriteArrayList<>();

    public static void register(SolarisOverlay overlay) {
        OVERLAYS.remove(overlay);
        OVERLAYS.add(overlay);
        OVERLAYS.sort(Comparator.comparingInt(SolarisOverlay::priority));
    }

    public static void unregister(SolarisOverlay overlay) {
        OVERLAYS.remove(overlay);
    }

    public static List<SolarisOverlay> getOverlays() {
        return OVERLAYS;
    }
}
