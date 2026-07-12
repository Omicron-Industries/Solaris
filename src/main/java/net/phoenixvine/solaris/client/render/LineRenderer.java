package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;

import com.mojang.math.Axis;

/**
 * A thin screen-space line between two arbitrary points — {@code GuiGraphics} has no line
 * primitive of its own. Rotates a plain {@code fill} rectangle to the segment's angle rather
 * than rasterizing pixel-by-pixel, the same {@code pushPose}/rotate/{@code fill} technique
 * {@link PlayerArrow}'s chevron already uses for its own non-axis-aligned shape.
 */
public final class LineRenderer {

    private LineRenderer() {}

    public static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int thickness, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5) return;

        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angleDeg));
        g.fill(0, -thickness / 2, (int) Math.round(length), thickness / 2 + 1, color);
        g.pose().popPose();
    }
}
