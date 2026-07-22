package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import com.mojang.math.Axis;

public final class PlayerArrow {

    private PlayerArrow() {}

    public static void draw(GuiGraphics g, int cx, int cy, int radius, float yawDegrees, int color) {
        draw(g, cx, cy, radius, yawDegrees, color, null);
    }

    public static void draw(GuiGraphics g, int cx, int cy, int radius, float yawDegrees, int color,
                            ResourceLocation skin) {
        drawChevron(g, cx, cy, radius, yawDegrees, color);
        drawFace(g, cx, cy, radius, color, skin);
    }

    public static void drawMob(GuiGraphics g, Mob mob, int cx, int cy, int radius, int color) {
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, color);
        MobFaceIcons.draw(g, mob, cx, cy, radius * 2);
    }

    public static void drawFlatSquare(GuiGraphics g, int cx, int cy, int radius, int color) {
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, color);
    }

    private static void drawChevron(GuiGraphics g, int cx, int cy, int radius, float yawDegrees, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yawDegrees));
        g.pose().translate(-cx, -cy, 0);
        drawChevronSouth(g, cx, cy, radius, color);
        g.pose().popPose();
    }

    private static void drawChevronSouth(GuiGraphics g, int cx, int cy, int r, int color) {
        int halfW = Math.max(3, Math.round(r * 0.7f));
        int len = Math.max(7, Math.round(r * 2.0f));
        int baseY = cy + Math.round(r * 0.4f);
        SmoothShapes.drawTriangleSouth(g, cx, baseY, halfW, len, color);
    }

    private static void drawFace(GuiGraphics g, int cx, int cy, int r, int color, ResourceLocation skin) {
        g.fill(cx - r, cy - r, cx + r, cy + r, color);
        if (skin != null) {
            int size = r * 2;
            PlayerFaceRenderer.draw(g, skin, cx - size / 2, cy - size / 2, size);
        }
    }
}
