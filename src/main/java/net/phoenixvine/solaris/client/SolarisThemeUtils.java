package net.phoenixvine.solaris.client;

import net.minecraft.util.Mth;

public final class SolarisThemeUtils {

    private SolarisThemeUtils() {}

    public static int C_BG;
    public static int C_PANEL;
    public static int C_HEADER;
    public static int C_BORDER;
    public static int C_BORDER2;
    public static int C_ACCENT;
    public static int C_TEXT;
    public static int C_DIM;
    public static int C_FAINT;

    public static void refreshCache() {
        SolarisTheme current = SolarisTheme.current();
        if (current == null) return;

        C_BG = current.bg.getColor();
        C_PANEL = current.panel.getColor();
        C_HEADER = current.header.getColor();
        C_BORDER = current.border.getColor();
        C_ACCENT = current.accent.getColor();
        C_TEXT = current.text.getColor();
        C_DIM = current.dim.getColor();
        C_FAINT = current.faint.getColor();

        int a = (C_BORDER >> 24) & 0xFF;
        int r = Mth.clamp(((C_BORDER >> 16) & 0xFF) - 12, 0, 255);
        int g = Mth.clamp(((C_BORDER >> 8) & 0xFF) - 12, 0, 255);
        int b = Mth.clamp((C_BORDER & 0xFF) - 12, 0, 255);
        C_BORDER2 = (a << 24) | (r << 16) | (g << 8) | b;
    }

    static {
        refreshCache();
    }
}
