package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;

public final class ModernPanel {

    private static final int THICKNESS = 2;

    private ModernPanel() {}

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int color) {
        int t = Math.min(THICKNESS, Math.min(w, h) / 2);
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y + t, x + t, y + h - t, color);
        g.fill(x + w - t, y + t, x + w, y + h - t, color);
    }
}
