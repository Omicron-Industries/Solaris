package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A clean, flat border — no beveled pixel-art texture like {@link VanillaPanel} uses. Built
 * for the fullscreen map specifically: the map's own content (terrain, markers, icons) is all
 * smoothly anti-aliased now, and framing it in vanilla's chunky Advancements-screen bezel read
 * as visibly dated next to it — one screen, two different decades of UI style.
 */
public final class ModernPanel {

    private static final int THICKNESS = 2;

    private ModernPanel() {}

    /** Draws a flat rectangular border of the given color around (x, y, x+w, y+h). Interior is untouched. */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, int color) {
        int t = Math.min(THICKNESS, Math.min(w, h) / 2);
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y + t, x + t, y + h - t, color);
        g.fill(x + w - t, y + t, x + w, y + h - t, color);
    }
}
