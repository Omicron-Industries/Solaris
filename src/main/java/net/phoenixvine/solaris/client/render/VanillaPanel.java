package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Draws real Minecraft GUI chrome — the vanilla Advancements-screen panel bezel, tinted
 * toward the active theme — for the BORDER only, not the interior. An earlier version used
 * {@code blitWithBorder}, which tiles the source texture's center fill to cover whatever
 * area is requested; at the panel sizes this mod actually uses (often most of the screen),
 * that meant repeating a texture that isn't perfectly seamless dozens of times, which shows
 * up as a visible grid of faint lines. The border strips are stretched instead of tiled
 * (a continuous bevel gradient, so stretching doesn't create seams the way repeating a
 * fill pattern does), and callers fill the interior with a flat theme color themselves —
 * which is also just correct per the original design direction: vanilla chrome for the
 * frame, flat color for empty space, no texture noise anywhere.
 */
public final class VanillaPanel {

    private static final ResourceLocation WINDOW = new ResourceLocation("textures/gui/advancements/window.png");
    private static final int NATIVE_W = 252;
    private static final int NATIVE_H = 140;
    private static final int BORDER = 7;

    private VanillaPanel() {}

    /** Draws the border tinted to an opaque color (theme's panel/border/header hex). Interior is untouched. */
    public static void draw(GuiGraphics g, int x, int y, int width, int height, int tintRgb) {
        drawArgb(g, x, y, width, height, 0xFF000000 | (tintRgb & 0xFFFFFF));
    }

    /** Draws the border tinted to an ARGB color, alpha included. Interior is untouched. */
    public static void drawArgb(GuiGraphics g, int x, int y, int width, int height, int tintArgb) {
        float a = FastColor.ARGB32.alpha(tintArgb) / 255f;
        float r = FastColor.ARGB32.red(tintArgb) / 255f;
        float gg = FastColor.ARGB32.green(tintArgb) / 255f;
        float b = FastColor.ARGB32.blue(tintArgb) / 255f;

        int bw = Math.min(BORDER, width / 2);
        int bh = Math.min(BORDER, height / 2);

        RenderSystem.setShaderColor(r, gg, b, a);

        // Corners — native size, never stretched.
        g.blit(WINDOW, x, y, bw, bh, 0, 0, bw, bh, NATIVE_W, NATIVE_H);
        g.blit(WINDOW, x + width - bw, y, bw, bh, NATIVE_W - bw, 0, bw, bh, NATIVE_W, NATIVE_H);
        g.blit(WINDOW, x, y + height - bh, bw, bh, 0, NATIVE_H - bh, bw, bh, NATIVE_W, NATIVE_H);
        g.blit(WINDOW, x + width - bw, y + height - bh, bw, bh, NATIVE_W - bw, NATIVE_H - bh, bw, bh, NATIVE_W,
                NATIVE_H);

        // Edges — a continuous bevel gradient, so stretching (not tiling) is what avoids seams.
        int edgeW = width - 2 * bw;
        int edgeH = height - 2 * bh;
        if (edgeW > 0) {
            g.blit(WINDOW, x + bw, y, edgeW, bh, bw, 0, NATIVE_W - 2 * bw, bh, NATIVE_W, NATIVE_H);
            g.blit(WINDOW, x + bw, y + height - bh, edgeW, bh, bw, NATIVE_H - bh, NATIVE_W - 2 * bw, bh, NATIVE_W,
                    NATIVE_H);
        }
        if (edgeH > 0) {
            g.blit(WINDOW, x, y + bh, bw, edgeH, 0, bh, bw, NATIVE_H - 2 * bh, NATIVE_W, NATIVE_H);
            g.blit(WINDOW, x + width - bw, y + bh, bw, edgeH, NATIVE_W - bw, bh, bw, NATIVE_H - 2 * bh, NATIVE_W,
                    NATIVE_H);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
