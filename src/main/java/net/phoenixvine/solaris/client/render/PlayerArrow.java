package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;

import com.mojang.math.Axis;

/**
 * A player marker: a square badge with the player's actual face inside it (like JourneyMap —
 * lets you tell players apart on the map at a glance instead of everyone being the same
 * anonymous dot/arrow) and a thin circle inscribed inside the square, tangent to the middle of
 * each side, plus a rotating chevron to show facing direction. The face itself never rotates —
 * only the chevron spins with yaw — since a spinning face is harder to recognize than an
 * upright one, matching how JourneyMap and most minimap mods do it. Flat colors throughout, no
 * black outline/border on any piece — square, circle, or chevron.
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
     * square.
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

    /**
     * A narrow triangular needle whose base sits INSIDE the face square (the face is drawn
     * afterward, on top, so that part is simply covered) with only the tip poking out past the
     * edge — like a compass needle through a badge, rather than a wide flag glued to the
     * outside of it.
     *
     * Floors (not just multiples of {@code r}) on both dimensions — at small marker sizes
     * (fully zoomed out on the map, or the minimap's fixed small radius), scaling purely off
     * {@code r} shrank the needle down to 1-2px, unreadable against busy terrain.
     */
    private static void drawChevronSouth(GuiGraphics g, int cx, int cy, int r, int color) {
        int halfW = Math.max(3, Math.round(r * 0.7f));
        int len = Math.max(7, Math.round(r * 2.0f));
        int baseY = cy + Math.round(r * 0.4f);
        SmoothShapes.drawTriangleSouth(g, cx, baseY, halfW, len, color);
    }

    /**
     * A square badge (side length {@code 2r}) with the face texture filling it and a thin
     * circle inscribed inside — tangent to the midpoint of each side, i.e. ring radius equal to
     * the square's half-side ({@code r}) — drawn on top of the face as a decorative accent.
     */
    private static void drawFace(GuiGraphics g, int cx, int cy, int r, int color, ResourceLocation skin) {
        g.fill(cx - r, cy - r, cx + r, cy + r, color);
        if (skin != null) {
            int size = r * 2;
            PlayerFaceRenderer.draw(g, skin, cx - size / 2, cy - size / 2, size);
        }
        SmoothShapes.drawRing(g, cx, cy, r, color);
    }
}
