package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.solaris.client.render.SmoothShapes;

public enum WaypointIcon {

    DOT,
    HOME,
    DANGER,
    RESOURCE,
    STAR,
    CIRCLE;

    public WaypointIcon next() {
        WaypointIcon[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static WaypointIcon fromId(String id) {
        try {
            return WaypointIcon.valueOf(id);
        } catch (Exception e) {
            return DOT;
        }
    }

    public String label() {
        return switch (this) {
            case DOT -> "Dot";
            case HOME -> "Home";
            case DANGER -> "Danger";
            case RESOURCE -> "Resource";
            case STAR -> "Star";
            case CIRCLE -> "Circle";
        };
    }

    public void draw(GuiGraphics g, int cx, int cy, int radius, int color) {
        switch (this) {
            case HOME -> drawHome(g, cx, cy, radius, color);
            case DANGER -> drawTriangle(g, cx, cy, radius, color);
            case RESOURCE -> drawDiamond(g, cx, cy, radius, color);
            case STAR -> drawCross(g, cx, cy, radius, color);
            case CIRCLE -> drawCircle(g, cx, cy, radius, color);
            default -> drawSquare(g, cx, cy, radius, color);
        }
    }

    private static void drawSquare(GuiGraphics g, int cx, int cy, int r, int color) {
        g.fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, 0xFF000000);
        g.fill(cx - r, cy - r, cx + r, cy + r, color);
    }

    private static void drawDiamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r - 1; dy <= r + 1; dy++) {
            int w = r + 1 - Math.abs(dy);
            if (w < 0) continue;
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, 0xFF000000);
        }
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private static void drawTriangle(GuiGraphics g, int cx, int cy, int r, int color) {
        int h = r * 2;
        SmoothShapes.drawTriangleNorth(g, cx, cy - r - 1, r + 2, h + 3, 0xFF000000);
        SmoothShapes.drawTriangleNorth(g, cx, cy - r, r, h, color);
    }

    private static void drawCross(GuiGraphics g, int cx, int cy, int r, int color) {
        g.fill(cx - r - 1, cy - 2, cx + r + 2, cy + 3, 0xFF000000);
        g.fill(cx - 2, cy - r - 1, cx + 3, cy + r + 2, 0xFF000000);
        g.fill(cx - r, cy - 1, cx + r + 1, cy + 2, color);
        g.fill(cx - 1, cy - r, cx + 2, cy + r + 1, color);
    }

    private static void drawHome(GuiGraphics g, int cx, int cy, int r, int color) {
        drawTriangle(g, cx, cy - r / 2, Math.max(2, r - 1), color);
        g.fill(cx - r, cy, cx + r, cy + r, 0xFF000000);
        g.fill(cx - r + 1, cy + 1, cx + r - 1, cy + r - 1, color);
    }

    private static void drawCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        SmoothShapes.drawCircle(g, cx, cy, r + 1, 0xFF000000);
        SmoothShapes.drawCircle(g, cx, cy, r, color);
    }

    static void drawItem(GuiGraphics g, int cx, int cy, int r, ItemStack stack) {
        int size = Math.max(8, r * 2);
        g.pose().pushPose();
        g.pose().translate(cx - size / 2.0, cy - size / 2.0, 0);
        g.pose().scale(size / 16f, size / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
