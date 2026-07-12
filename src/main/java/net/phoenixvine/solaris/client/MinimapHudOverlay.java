package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.render.LineRenderer;
import net.phoenixvine.solaris.client.render.MinimapShape;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SmoothShapes;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.math.Axis;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;

/**
 * Small corner minimap. The backing {@link SolarisTexture} only rebuilds when the player
 * crosses a chunk boundary; the crop window inside it shifts every frame off the player's
 * exact sub-block position, so the minimap still pans smoothly between rebuilds.
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MinimapHudOverlay {

    private static final int MARGIN = 6;

    private static SolarisTexture texture;

    private static SolarisTexture texture() {
        if (texture == null) {
            int radius = Math.min(SolarisConfig.MINIMAP_RADIUS_CHUNKS.get(),
                    SolarisConfig.MAX_MINIMAP_RANGE_CHUNKS.get());
            texture = new SolarisTexture("minimap", radius);
        }
        return texture;
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        // Forge still fires hotbar-overlay events while a Screen is open (vanilla itself keeps
        // rendering the HUD underneath most menus) — without this check the minimap rendered
        // behind/through every Solaris screen, most visibly the fullscreen map itself, where a
        // second, redundant map peeking out from a corner made no sense.
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_MINIMAP, mc.level.dimension().location())
                .atLeast(SolarisFeatureState.VISIBLE)) {
            return;
        }

        SolarisTexture tex = texture();
        int screenSize = SolarisConfig.MINIMAP_SIZE.get();
        // Half of the radius's max-safe span — keeps the crop window in bounds even at the
        // smallest configurable radius (see SolarisTexture's bounds note on this formula).
        int viewPixels = tex.getRadiusChunks() * 16;

        BlockPos blockPos = mc.player.blockPosition();
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        ChunkKey center = ChunkKey.of(mc.level, new ChunkPos(chunkX, chunkZ));
        tex.maybeRebuild(center);

        double fracX = mc.player.getX() - (chunkX << 4);
        double fracZ = mc.player.getZ() - (chunkZ << 4);
        int radiusPixels = tex.getRadiusChunks() * 16;
        double playerPixelX = radiusPixels + fracX;
        double playerPixelZ = radiusPixels + fracZ;

        float u = (float) (playerPixelX - viewPixels / 2.0);
        float v = (float) (playerPixelZ - viewPixels / 2.0);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW - screenSize - MARGIN;
        int y = MARGIN;

        GuiGraphics g = event.getGuiGraphics();
        MinimapShape shape = SolarisConfig.MINIMAP_SHAPE.get();
        int cx = x + screenSize / 2;
        int cy = y + screenSize / 2;

        if (shape == MinimapShape.SQUARE) {
            ModernPanel.draw(g, x - 5, y - 5, screenSize + 10, screenSize + 10, C_BORDER);
        }

        // Rotate-mode always shows the player facing "up" instead of true north — the terrain
        // and every marker rotate around the player instead of the player's own arrow rotating
        // against fixed terrain. Content angle 180-yaw (not just -yaw) and the arrow's own fixed
        // angle of 180 both come from the same verified convention PlayerArrow's chevron already
        // uses (yaw 0 -> south/screen-down): rotating "down" content by 180-yaw lines the yaw
        // direction up with screen-up, and a chevron drawn at a fixed 180 (rather than the live
        // yaw) points the same screen-up direction by that identical convention.
        boolean rotate = SolarisConfig.MINIMAP_ROTATE.get();
        float contentAngle = rotate ? 180f - mc.player.getYRot() : 0f;

        g.enableScissor(x, y, x + screenSize, y + screenSize);
        g.pose().pushPose();
        if (rotate) {
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(contentAngle));
            g.pose().translate(-cx, -cy, 0);
        }

        float scale = screenSize / (float) viewPixels;
        if (shape == MinimapShape.SQUARE) {
            g.blit(tex.textureId(), x, y, screenSize, screenSize, u, v, viewPixels, viewPixels,
                    tex.getSizePixels(), tex.getSizePixels());
        } else {
            drawClippedTerrain(g, shape, tex, x, y, screenSize, u, v, scale);
        }

        int radius = screenSize / 2;
        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            double wPixelX = radiusPixels + (w.x - (chunkX << 4));
            double wPixelZ = radiusPixels + (w.z - (chunkZ << 4));
            if (wPixelX < u || wPixelX > u + viewPixels || wPixelZ < v || wPixelZ > v + viewPixels) continue;
            int wx = x + (int) ((wPixelX - u) * scale);
            int wy = y + (int) ((wPixelZ - v) * scale);
            // Rotating a point around the shape's own center (cx, cy) — the pose transform above
            // — never changes its position relative to that center, so this plain pre-rotation
            // containment check is valid in rotate mode too, not just north-up.
            if (shape != MinimapShape.SQUARE && !containsPoint(shape, wx - x, wy - y, screenSize)) continue;
            g.fill(wx - 2, wy - 2, wx + 2, wy + 2, 0xFF000000);
            g.fill(wx - 1, wy - 1, wx + 1, wy + 1, w.colorArgb());
        }

        g.pose().popPose();
        g.disableScissor();

        if (shape == MinimapShape.CIRCLE) {
            SmoothShapes.drawRing(g, cx, cy, radius + 3, C_BORDER);
        } else if (shape.isPolygon()) {
            drawPolygonOutline(g, shape, cx, cy, screenSize + 6);
        }

        PlayerArrow.draw(g, cx, cy, 6, rotate ? 180f : mc.player.getYRot(), C_ACCENT,
                mc.player.getSkinTextureLocation());
    }

    /**
     * Clips the terrain blit to a non-square shape by never drawing outside it in the first
     * place, rather than masking afterward — vanilla {@code GuiGraphics} only supports
     * rectangular scissor regions, so a real non-rectangular clip isn't otherwise available
     * without a shader pass. Draws the blit as many thin horizontal strips, each cropped to the
     * shape's chord width at that row (exact circle math for {@code CIRCLE}, a per-row polygon
     * scanline against {@link MinimapShape#vertices()} for every other shape); cheap enough for a
     * HUD element (a handful of blit calls, not per-pixel) and correctly anti-aliased-looking at
     * typical minimap sizes since the strip height is small relative to the shape's size.
     */
    private static void drawClippedTerrain(GuiGraphics g, MinimapShape shape, SolarisTexture tex, int x, int y,
                                           int screenSize, float u, float v, float scale) {
        int radius = screenSize / 2;
        int cx = x + radius;
        int stripH = Math.max(1, screenSize / 64);

        for (int i = 0; i < screenSize; i += stripH) {
            int destH = Math.min(stripH, screenSize - i);
            int rowCenter = i + destH / 2;

            int destX;
            int destW;
            if (shape == MinimapShape.CIRCLE) {
                int dy = rowCenter - radius;
                long discriminant = (long) radius * radius - (long) dy * dy;
                if (discriminant < 0) continue;
                int halfW = (int) Math.sqrt(discriminant);
                if (halfW <= 0) continue;
                destX = cx - halfW;
                destW = halfW * 2;
            } else {
                float[] span = rowSpan(shape.vertices(), rowCenter / (float) screenSize);
                if (span == null) continue;
                destX = x + Math.round(span[0] * screenSize);
                destW = Math.round((span[1] - span[0]) * screenSize);
                if (destW <= 0) continue;
            }

            int destY = y + i;
            float srcX = u + (destX - x) / scale;
            float srcY = v + i / scale;
            int srcW = Math.max(1, Math.round(destW / scale));
            int srcH = Math.max(1, Math.round(destH / scale));

            g.blit(tex.textureId(), destX, destY, destW, destH, srcX, srcY, srcW, srcH,
                    tex.getSizePixels(), tex.getSizePixels());
        }
    }

    /** Whether ({@code lx}, {@code ly}) — minimap-local pixel coords — falls inside {@code shape}. */
    private static boolean containsPoint(MinimapShape shape, int lx, int ly, int screenSize) {
        if (shape == MinimapShape.CIRCLE) {
            int radius = screenSize / 2;
            int dx = lx - radius;
            int dy = ly - radius;
            return dx * dx + dy * dy <= radius * radius;
        }
        float[] span = rowSpan(shape.vertices(), ly / (float) screenSize);
        if (span == null) return false;
        float nx = lx / (float) screenSize;
        return nx >= span[0] && nx <= span[1];
    }

    /**
     * The horizontal span (both normalized 0..1) where a horizontal scanline at height {@code ny}
     * crosses {@code verts}' polygon boundary, via standard edge-intersection — correct for any
     * convex polygon (every shape {@link MinimapShape} currently defines), where the min/max of
     * all edge crossings is always a single contiguous span. {@code null} if the scanline misses
     * the polygon entirely (only possible right at the very top/bottom vertex rows).
     */
    private static float[] rowSpan(float[][] verts, float ny) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        int n = verts.length;
        for (int i = 0; i < n; i++) {
            float[] a = verts[i];
            float[] b = verts[(i + 1) % n];
            float ay = a[1];
            float by = b[1];
            if ((ay <= ny && by > ny) || (by <= ny && ay > ny)) {
                float t = (ny - ay) / (by - ay);
                float px = a[0] + t * (b[0] - a[0]);
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
            }
        }
        return minX <= maxX ? new float[] { minX, maxX } : null;
    }

    private static void drawPolygonOutline(GuiGraphics g, MinimapShape shape, int cx, int cy, int outlineSize) {
        float[][] verts = shape.vertices();
        int n = verts.length;
        for (int i = 0; i < n; i++) {
            float[] a = verts[i];
            float[] b = verts[(i + 1) % n];
            int x1 = cx + Math.round((a[0] - 0.5f) * outlineSize);
            int y1 = cy + Math.round((a[1] - 0.5f) * outlineSize);
            int x2 = cx + Math.round((b[0] - 0.5f) * outlineSize);
            int y2 = cy + Math.round((b[1] - 0.5f) * outlineSize);
            LineRenderer.drawLine(g, x1, y1, x2, y2, 2, C_BORDER);
        }
    }
}
