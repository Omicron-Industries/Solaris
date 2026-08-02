package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
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
import net.phoenixvine.solaris.client.render.TextureAddressing;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.math.Axis;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;

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

    private static boolean isVisible(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return false;
        return SolarisAPI.getFeatureState(SolarisAPI.FEATURE_MINIMAP, mc.level.dimension().location())
                .atLeast(SolarisFeatureState.VISIBLE);
    }

    /**
     * Screen-space bounding box the minimap currently occupies — same square region every shape
     * (circle/polygon) is inscribed within, used both for rendering and for scroll-to-zoom hit-testing.
     */
    private static int[] bounds(Minecraft mc) {
        int screenSize = SolarisConfig.MINIMAP_SIZE.get();
        int screenW = mc.getWindow().getGuiScaledWidth();
        return new int[] { screenW - screenSize - MARGIN, MARGIN, screenSize };
    }

    private static final double ZOOM_SCROLL_STEP = 0.5;

    /**
     * Scrolling while hovering the minimap AND holding the "cycle minimap style" keybind adjusts
     * its zoom — held, not just tapped, so a bare scroll (e.g. switching hotbar slots near the
     * corner) doesn't also change the minimap zoom. Cancelled while active so it doesn't also
     * cycle the hotbar's selected slot underneath.
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isVisible(mc)) return;
        if (!SolarisKeybinds.CYCLE_MINIMAP_STYLE.isDown()) return;

        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        int[] b = bounds(mc);
        if (mx < b[0] || mx > b[0] + b[2] || my < b[1] || my > b[1] + b[2]) return;

        double zoom = Mth.clamp(SolarisConfig.MINIMAP_ZOOM.get() + event.getScrollDelta() * ZOOM_SCROLL_STEP, 1.0,
                8.0);
        SolarisConfig.MINIMAP_ZOOM.set(zoom);
        SolarisConfig.MINIMAP_ZOOM.save();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!isVisible(mc)) return;

        SolarisTexture tex = texture();
        int screenSize = SolarisConfig.MINIMAP_SIZE.get();

        int viewPixels = Math.max(16, (int) (tex.getRadiusChunks() * 16 / SolarisConfig.MINIMAP_ZOOM.get()));

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

        int spanChunks = tex.getRadiusChunks() * 2 + 1;
        int originX = TextureAddressing.properMod(chunkX - tex.getRadiusChunks(), spanChunks) * 16;
        int originZ = TextureAddressing.properMod(chunkZ - tex.getRadiusChunks(), spanChunks) * 16;

        int[] b = bounds(mc);
        int x = b[0];
        int y = b[1];

        GuiGraphics g = event.getGuiGraphics();
        MinimapShape shape = SolarisConfig.MINIMAP_SHAPE.get();
        int cx = x + screenSize / 2;
        int cy = y + screenSize / 2;

        if (shape == MinimapShape.SQUARE) {
            ModernPanel.draw(g, x - 5, y - 5, screenSize + 10, screenSize + 10, C_BORDER);
        }

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
        float wrappedU = TextureAddressing.properMod(originX + u, tex.getSizePixels());
        float wrappedV = TextureAddressing.properMod(originZ + v, tex.getSizePixels());
        if (shape == MinimapShape.SQUARE) {
            g.blit(tex.textureId(), x, y, screenSize, screenSize, wrappedU, wrappedV, viewPixels, viewPixels,
                    tex.getSizePixels(), tex.getSizePixels());
        } else {
            drawClippedTerrain(g, shape, tex, x, y, screenSize, wrappedU, wrappedV, scale);
        }

        int radius = screenSize / 2;
        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            double wPixelX = radiusPixels + (w.x - (chunkX << 4));
            double wPixelZ = radiusPixels + (w.z - (chunkZ << 4));
            if (wPixelX < u || wPixelX > u + viewPixels || wPixelZ < v || wPixelZ > v + viewPixels) continue;
            int wx = x + (int) ((wPixelX - u) * scale);
            int wy = y + (int) ((wPixelZ - v) * scale);

            if (shape != MinimapShape.SQUARE && !containsPoint(shape, wx - x, wy - y, screenSize)) continue;
            g.fill(wx - 3, wy - 3, wx + 3, wy + 3, 0xFF000000);
            g.fill(wx - 2, wy - 2, wx + 2, wy + 2, w.colorArgb());
        }

        g.pose().popPose();
        g.disableScissor();

        if (shape == MinimapShape.CIRCLE) {
            SmoothShapes.drawRing(g, cx, cy, radius + 3, C_BORDER);
        } else if (shape.isPolygon()) {
            drawPolygonOutline(g, shape, cx, cy, screenSize);
        }

        PlayerArrow.draw(g, cx, cy, 6, rotate ? 180f : mc.player.getYRot(), C_ACCENT,
                mc.player.getSkinTextureLocation());

        drawInfoText(g, mc, x, y, screenSize);
    }

    private static void drawInfoText(GuiGraphics g, Minecraft mc, int x, int y, int screenSize) {
        boolean showTime = SolarisConfig.MINIMAP_SHOW_TIME.get();
        boolean showCoords = SolarisConfig.MINIMAP_SHOW_COORDS.get();
        if (!showTime && !showCoords) return;

        int textY = y + screenSize + 7;
        int cx = x + screenSize / 2;
        if (showTime) {
            g.drawCenteredString(mc.font, formatTime(mc.level.getDayTime()), cx, textY, C_ACCENT);
            textY += mc.font.lineHeight + 1;
        }
        if (showCoords) {
            BlockPos pos = mc.player.blockPosition();
            String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            g.drawCenteredString(mc.font, coords, cx, textY, C_ACCENT);
        }
    }

    private static String formatTime(long dayTime) {
        long ticks = ((dayTime % 24000) + 24000) % 24000;
        int hour = (int) ((ticks / 1000 + 6) % 24);
        int minute = (int) (ticks % 1000 * 60 / 1000);
        return String.format("%02d:%02d", hour, minute);
    }

    private static void drawClippedTerrain(GuiGraphics g, MinimapShape shape, SolarisTexture tex, int x, int y,
                                           int screenSize, float u, float v, float scale) {
        int stripH = Math.max(1, screenSize / 64);

        for (int i = 0; i < screenSize; i += stripH) {
            int destH = Math.min(stripH, screenSize - i);
            int rowCenter = i + destH / 2;

            float[] span = shape.rowSpan(rowCenter / (float) screenSize);
            if (span == null) continue;
            int destX = x + Math.round(span[0] * screenSize);
            int destW = Math.round((span[1] - span[0]) * screenSize);
            if (destW <= 0) continue;

            int destY = y + i;
            float srcX = u + (destX - x) / scale;
            float srcY = v + i / scale;
            int srcW = Math.max(1, Math.round(destW / scale));
            int srcH = Math.max(1, Math.round(destH / scale));

            g.blit(tex.textureId(), destX, destY, destW, destH, srcX, srcY, srcW, srcH,
                    tex.getSizePixels(), tex.getSizePixels());
        }
    }

    private static boolean containsPoint(MinimapShape shape, int lx, int ly, int screenSize) {
        return shape.containsPoint(lx, ly, screenSize);
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