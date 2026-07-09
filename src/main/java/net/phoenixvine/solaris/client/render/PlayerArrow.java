package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;

import com.mojang.math.Axis;

/**
 * A player marker: a small colored ring with the player's actual face inside it (like
 * JourneyMap — lets you tell players apart on the map at a glance instead of everyone being
 * the same anonymous dot/arrow), plus a rotating chevron attached to the ring's edge to show
 * facing direction. The face itself never rotates — only the chevron spins with yaw — since a
 * spinning face is harder to recognize than an upright one, matching how JourneyMap and most
 * minimap mods do it.
 *
 * Chevron rotation deliberately mirrors vanilla's own map player-icon rendering exactly —
 * verified against decompiled {@code MapRenderer.MapInstance.draw}, which rotates the icon
 * with {@code poseStack.mulPose(Axis.ZP.rotationDegrees(yaw))} and no extra offset, rather
 * than guessed, since the sign of a screen-space rotation is exactly the kind of thing that's
 * easy to get backwards. Since vanilla applies zero correction, the un-rotated chevron must
 * point the same direction as yaw 0 (south, screen-down on a north-up map, confirmed against
 * {@code Entity}'s look-vector formula: yaw 0 → (0, +1) → south).
 */
public final class PlayerArrow {

    private PlayerArrow() {}

    /**
     * No-face fallback — a flat colored chevron-arrow, for markers with no skin to show (e.g. GT ore veins once did).
     */
    public static void draw(GuiGraphics g, int cx, int cy, int radius, float yawDegrees, int color) {
        draw(g, cx, cy, radius, yawDegrees, color, null);
    }

    /**
     * Draws the marker centered on ({@code cx}, {@code cy}); {@code skin} may be {@code null} to fall back to a flat
     * circle.
     */
    public static void draw(GuiGraphics g, int cx, int cy, int radius, float yawDegrees, int color,
                            ResourceLocation skin) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yawDegrees));
        g.pose().translate(-cx, -cy, 0);
        drawChevronSouth(g, cx, cy, radius, color);
        g.pose().popPose();

        drawFace(g, cx, cy, radius, color, skin);
    }

    /** A short triangular nub attached to the south edge of the ring, tip pointing away from center. */
    private static void drawChevronSouth(GuiGraphics g, int cx, int cy, int r, int color) {
        int len = Math.max(3, r);
        int halfW = Math.max(2, r / 2 + 1);
        int baseY = cy + r - 1;
        SmoothShapes.drawTriangleSouth(g, cx, baseY - 1, halfW + 1, len + 2, 0xFF000000);
        SmoothShapes.drawTriangleSouth(g, cx, baseY, halfW, len, color);
    }

    private static void drawFace(GuiGraphics g, int cx, int cy, int r, int color, ResourceLocation skin) {
        SmoothShapes.drawCircle(g, cx, cy, r + 1, 0xFF000000);
        SmoothShapes.drawCircle(g, cx, cy, r, color);
        if (skin != null) {
            int size = Math.max(6, r * 2 - 1);
            PlayerFaceRenderer.draw(g, skin, cx - size / 2, cy - size / 2, size);
        }
    }
}
