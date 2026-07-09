package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.PhoenixSolaris;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Anti-aliased circle/triangle marker shapes for the map and HUD, in place of the blocky
 * {@code GuiGraphics.fill} scanline shapes {@code WaypointIcon}/{@code PlayerArrow} used to
 * draw directly — those snap every edge to whole pixels, which reads as a jagged rendering
 * bug rather than a style choice at the small sizes markers actually render at. The map/HUD
 * (like quest markers) are one of the few places in this mod family allowed to break from the
 * pixel-art look on purpose.
 *
 * Implemented as two small pre-baked alpha-mask textures (a circle and a south-pointing
 * triangle) with real analytic edge coverage computed per source pixel — not just a hard
 * radius/edge cutoff — then bilinear-filtered on upload ({@link DynamicTexture#setFilter}) so
 * scaling them up for on-screen sizes stays smooth. Tinted per call via
 * {@link RenderSystem#setShaderColor} (reset immediately after) instead of baking one texture
 * per color, since marker colors are arbitrary/player-chosen.
 */
public final class SmoothShapes {

    private static final int TEX_SIZE = 40;
    private static final float CIRCLE_RADIUS_PX = 16f;
    private static final float CIRCLE_SCALE = TEX_SIZE / CIRCLE_RADIUS_PX;

    private static final float TRI_HALF_BASE_PX = 15f;
    private static final float TRI_HEIGHT_PX = 30f;

    private static ResourceLocation circleTex;
    private static ResourceLocation triangleTex;

    private SmoothShapes() {}

    /** Draws a filled circle centered on ({@code cx}, {@code cy}) with the given radius. */
    public static void drawCircle(GuiGraphics g, int cx, int cy, int radius, int argb) {
        ensureLoaded();
        int size = Math.round(radius * CIRCLE_SCALE);
        blitTinted(g, circleTex, cx - size / 2, cy - size / 2, size, size, argb);
    }

    /**
     * Draws a south-pointing (apex-down) triangle whose bounding box top-left is
     * ({@code cx - halfWidth}, {@code topY}) — same box parameters the old scanline
     * {@code drawTriangle} took, so callers didn't need to re-derive their geometry.
     */
    public static void drawTriangleSouth(GuiGraphics g, int cx, int topY, int halfWidth, int height, int argb) {
        ensureLoaded();
        blitTriangle(g, cx, topY, halfWidth, height, argb, false);
    }

    /** Same as {@link #drawTriangleSouth}, but apex-up — reuses the same baked texture, vertically flipped. */
    public static void drawTriangleNorth(GuiGraphics g, int cx, int topY, int halfWidth, int height, int argb) {
        ensureLoaded();
        blitTriangle(g, cx, topY, halfWidth, height, argb, true);
    }

    private static void blitTriangle(GuiGraphics g, int cx, int topY, int halfWidth, int height, int argb,
                                     boolean flip) {
        int x = cx - halfWidth;
        int w = halfWidth * 2;
        setTint(argb);
        // Flipping vertically by sampling from the bottom of the source texture upward (negative
        // vHeight) — same technique vanilla's own PlayerFaceRenderer uses for upside-down faces —
        // avoids baking a second, mirrored triangle texture just for the apex-up icons.
        if (flip) {
            g.blit(triangleTex, x, topY, w, height, 0, TEX_SIZE, TEX_SIZE, -TEX_SIZE, TEX_SIZE, TEX_SIZE);
        } else {
            g.blit(triangleTex, x, topY, w, height, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        }
        clearTint();
    }

    private static void blitTinted(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int argb) {
        setTint(argb);
        g.blit(tex, x, y, w, h, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        clearTint();
    }

    private static void setTint(int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float gr = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        RenderSystem.setShaderColor(r, gr, b, a);
    }

    private static void clearTint() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void ensureLoaded() {
        if (circleTex != null) return;
        circleTex = register("smooth_circle", bakeCircle());
        triangleTex = register("smooth_triangle", bakeTriangle());
    }

    private static ResourceLocation register(String name, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        ResourceLocation loc = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(loc, texture);
        return loc;
    }

    private static NativeImage bakeCircle() {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, false);
        float cx = TEX_SIZE / 2f;
        float cy = TEX_SIZE / 2f;
        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                float dx = x + 0.5f - cx;
                float dy = y + 0.5f - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float coverage = clamp01(CIRCLE_RADIUS_PX - dist + 0.5f);
                image.setPixelRGBA(x, y, packWhite(coverage));
            }
        }
        return image;
    }

    private static NativeImage bakeTriangle() {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, false);
        float pad = (TEX_SIZE - TRI_HEIGHT_PX) / 2f;
        float topY = pad;
        float botY = pad + TRI_HEIGHT_PX;
        float midX = TEX_SIZE / 2f;
        // Base corners at the top, apex at the bottom — matches the old scanline triangle's convention.
        float ax = midX - TRI_HALF_BASE_PX;
        float ay = topY;
        float bx = midX + TRI_HALF_BASE_PX;
        float by = topY;
        float apexX = midX;
        float apexY = botY;

        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float d1 = edgeDistance(ax, ay, bx, by, px, py);
                float d2 = edgeDistance(bx, by, apexX, apexY, px, py);
                float d3 = edgeDistance(apexX, apexY, ax, ay, px, py);
                float coverage = clamp01(Math.min(d1, Math.min(d2, d3)) + 0.5f);
                image.setPixelRGBA(x, y, packWhite(coverage));
            }
        }
        return image;
    }

    /**
     * Signed perpendicular distance from ({@code px}, {@code py}) to the line through
     * ({@code ax}, {@code ay})→({@code bx}, {@code by}) — positive on the triangle's interior
     * side (verified against the actual winding order {@link #bakeTriangle()} uses).
     */
    private static float edgeDistance(float ax, float ay, float bx, float by, float px, float py) {
        float ex = bx - ax;
        float ey = by - ay;
        float len = (float) Math.sqrt(ex * ex + ey * ey);
        float cross = ex * (py - ay) - ey * (px - ax);
        return cross / len;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** White RGB with {@code alpha} coverage — channel order doesn't matter for a pure white pixel. */
    private static int packWhite(float alpha) {
        int a = Math.round(alpha * 255f);
        return a << 24 | 0x00FFFFFF;
    }
}
