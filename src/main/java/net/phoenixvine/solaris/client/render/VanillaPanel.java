package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import com.mojang.blaze3d.systems.RenderSystem;

public final class VanillaPanel {

    private static final ResourceLocation WINDOW = new ResourceLocation("textures/gui/advancements/window.png");
    private static final int NATIVE_W = 252;
    private static final int NATIVE_H = 140;
    private static final int BORDER = 7;

    private VanillaPanel() {}

    public static void draw(GuiGraphics g, int x, int y, int width, int height, int tintRgb) {
        drawArgb(g, x, y, width, height, 0xFF000000 | (tintRgb & 0xFFFFFF));
    }

    public static void drawArgb(GuiGraphics g, int x, int y, int width, int height, int tintArgb) {
        float a = FastColor.ARGB32.alpha(tintArgb) / 255f;
        float r = FastColor.ARGB32.red(tintArgb) / 255f;
        float gg = FastColor.ARGB32.green(tintArgb) / 255f;
        float b = FastColor.ARGB32.blue(tintArgb) / 255f;

        int bw = Math.min(BORDER, width / 2);
        int bh = Math.min(BORDER, height / 2);

        RenderSystem.setShaderColor(r, gg, b, a);

        g.blit(WINDOW, x, y, bw, bh, 0, 0, bw, bh, NATIVE_W, NATIVE_H);
        g.blit(WINDOW, x + width - bw, y, bw, bh, NATIVE_W - bw, 0, bw, bh, NATIVE_W, NATIVE_H);
        g.blit(WINDOW, x, y + height - bh, bw, bh, 0, NATIVE_H - bh, bw, bh, NATIVE_W, NATIVE_H);
        g.blit(WINDOW, x + width - bw, y + height - bh, bw, bh, NATIVE_W - bw, NATIVE_H - bh, bw, bh, NATIVE_W,
                NATIVE_H);

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
