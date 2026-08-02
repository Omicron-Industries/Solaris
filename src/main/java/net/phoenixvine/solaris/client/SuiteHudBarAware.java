package net.phoenixvine.solaris.client;

/**
 * Marker interface for {@code net.minecraft.client.gui.screens.Screen} subclasses that want the
 * cross-suite HUD bar to keep rendering (and stay clickable) while they're open. The bar is
 * normally a plain HUD overlay, which Minecraft/Forge stop firing the instant any screen opens -
 * a screen has to opt in explicitly by implementing this interface, so the bar doesn't
 * unexpectedly draw on top of arbitrary third-party GUIs that never asked for it. The vanilla
 * inventory/creative screens are a special case handled separately (see
 * {@link SuiteHudBarButton#screenWantsBar}), since this mod obviously can't make vanilla's own
 * classes implement an interface.
 */
public interface SuiteHudBarAware {}
