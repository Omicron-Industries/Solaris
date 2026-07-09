package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.render.ContextMenu;
import net.phoenixvine.solaris.client.render.LabelSide;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.client.waypoint.QuickWaypointScreen;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointIconManager;
import net.phoenixvine.solaris.client.waypoint.WaypointListScreen;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BG;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

/** Fullscreen pannable/zoomable terrain map. */
@OnlyIn(Dist.CLIENT)
public class SolarisMapScreen extends Screen {

    private static final int MARGIN = 20;
    private static final float MIN_ZOOM_OVERSCAN = 1.2f;

    private static SolarisTexture texture;

    private final MapViewport viewport = new MapViewport(
            SolarisConfig.ZOOM_MIN.get().floatValue(), SolarisConfig.ZOOM_MAX.get().floatValue());
    private final ContextMenu menu = new ContextMenu();
    private boolean dragging = false;
    private boolean hasPlayerMarker = false;
    private double playerPixelX;
    private double playerPixelZ;
    private int anchorChunkX;
    private int anchorChunkZ;
    private int radiusPixels;
    private ChunkKey centerKey;
    private String hoveredVeinName;
    private String hoveredPlayerName;

    // Set once if a GTCEu call throws, so a stale/mismatched GTCEu jar (ModList says present,
    // but a class we need is actually missing/incompatible) logs once and stops retrying ore
    // vein markers, instead of potentially re-throwing every frame — mirrors DomainHudOverlay's
    // solarisBroken latch over in Phoenix Domains.
    private static boolean gtceuBroken = false;

    public SolarisMapScreen() {
        super(Component.translatable("solaris.map.title"));
    }

    private static SolarisTexture texture() {
        if (texture == null) texture = new SolarisTexture("map", SolarisConfig.MAP_RADIUS_CHUNKS.get());
        return texture;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        SolarisTexture tex = texture();

        // Never let the map render smaller than the frame around it. A small overscan factor
        // is deliberate: at the exact "fits the frame perfectly" zoom, clampOffsetToCover's
        // min/max pan bounds collapse to a single point (content size == frame size), which
        // pins the offset and makes dragging do nothing — "can't pan when fully zoomed out".
        // A little extra zoom leaves room on every edge to actually drag around in.
        int viewportSpan = Math.min(width, height) - 2 * MARGIN;
        if (viewportSpan > 0) {
            viewport.raiseZoomMin((float) viewportSpan * MIN_ZOOM_OVERSCAN / tex.getSizePixels());
        }

        radiusPixels = tex.getRadiusChunks() * 16;

        if (mc.player != null && mc.level != null) {
            BlockPos blockPos = mc.player.blockPosition();
            anchorChunkX = blockPos.getX() >> 4;
            anchorChunkZ = blockPos.getZ() >> 4;
            centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));
            tex.maybeRebuild(centerKey);

            double fracX = mc.player.getX() - (anchorChunkX << 4);
            double fracZ = mc.player.getZ() - (anchorChunkZ << 4);
            playerPixelX = radiusPixels + fracX;
            playerPixelZ = radiusPixels + fracZ;
            hasPlayerMarker = true;

            // Center the viewport on the player's exact position.
            viewport.setOffset(width / 2.0 - playerPixelX * viewport.getZoom(),
                    height / 2.0 - playerPixelZ * viewport.getZoom());
        }

        clampViewport();
    }

    private void clampViewport() {
        viewport.clampOffsetToCover(texture().getSizePixels(), MARGIN, width - 2 * MARGIN, MARGIN, height - 2 * MARGIN);
    }

    private double waypointPixelX(Waypoint w) {
        return radiusPixels + (w.x - (anchorChunkX << 4));
    }

    private double waypointPixelZ(Waypoint w) {
        return radiusPixels + (w.z - (anchorChunkZ << 4));
    }

    /**
     * Just the panel + terrain blit, no waypoint/vein/player markers, tooltips, title, footer,
     * or context menu — a lightweight background for popups opened over this screen (see
     * {@code QuickWaypointScreen}). Rendering the FULL {@link #render} a second time as a
     * "background" caused garbled, overlapping text: this screen's own waypoint labels/menu
     * items and the popup's own text both end up in the same per-frame text batch, and a plain
     * {@code GuiGraphics.fill} dim overlay in between doesn't reliably sit under text draws in
     * that batch's flush order. Skipping every text draw here sidesteps that entirely instead
     * of fighting the batching order.
     */
    public void renderMapBackground(GuiGraphics g) {
        renderBackground(g);
        SolarisTexture tex = texture();
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        VanillaPanel.draw(g, MARGIN - 8, MARGIN - 8, width - 2 * (MARGIN - 8), height - 2 * (MARGIN - 8), C_BORDER);
        g.enableScissor(MARGIN, MARGIN, width - MARGIN, height - MARGIN);
        g.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, C_BG);
        int destX = (int) viewport.toScreenX(0, 0);
        int destY = (int) viewport.toScreenY(0, 0);
        int destSize = (int) (size * viewport.getZoom());
        g.blit(tex.textureId(), destX, destY, destSize, destSize, 0, 0, size, size, size, size);
        g.disableScissor();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);

        SolarisTexture tex = texture();
        // Cheap no-op unless something actually invalidated the texture (e.g. a Settings
        // slider or a claim/waypoint change while this screen is already open) — without this,
        // those changes only took effect the next time the map was reopened, since the only
        // other maybeRebuild call is in init().
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        VanillaPanel.draw(g, MARGIN - 8, MARGIN - 8, width - 2 * (MARGIN - 8), height - 2 * (MARGIN - 8), C_BORDER);
        g.enableScissor(MARGIN, MARGIN, width - MARGIN, height - MARGIN);
        g.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, C_BG);
        int destX = (int) viewport.toScreenX(0, 0);
        int destY = (int) viewport.toScreenY(0, 0);
        int destSize = (int) (size * viewport.getZoom());
        g.blit(tex.textureId(), destX, destY, destSize, destSize, 0, 0, size, size, size, size);

        // Marker size scales with zoom (clamped so they never vanish zoomed-out or balloon zoomed-in),
        // then with the player's configured icon size on top of that — markers are drawn "on"
        // the map, not as fixed-size UI chrome floating over it.
        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(Mth.clamp(3f * viewport.getZoom(), 2f, 10f) * iconScale);
        int playerR = Math.max(2, Math.min(8, Math.round(2 * viewport.getZoom())));

        LabelSide labelSide = SolarisConfig.LABEL_SIDE.get();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
            for (Waypoint w : waypoints) {
                int wx = (int) viewport.toScreenX(waypointPixelX(w), 0);
                int wy = (int) viewport.toScreenY(waypointPixelZ(w), 0);
                if (wx < MARGIN || wx > width - MARGIN || wy < MARGIN || wy > height - MARGIN) continue;
                WaypointIconManager.draw(g, w.icon, wx, wy, iconR, w.colorArgb());
                int lx = labelSide.drawX(wx, iconR, font.width(w.name));
                int ly = labelSide.drawY(wy, iconR, font.lineHeight);
                g.drawString(font, w.name, lx, ly, C_TEXT, true);
            }
        }

        // Hovered vein's name (if any) — shown as a tooltip after the scissor's lifted below,
        // instead of drawing every vein's name on the map at once, which got unreadably busy
        // with more than a handful of veins on screen.
        hoveredVeinName = null;

        if (SolarisConfig.SHOW_GT_ORE_VEINS.get() && !gtceuBroken && mc.level != null &&
                GtceuIntegration.isAvailable()) {
            try {
                int minX = (anchorChunkX << 4) - radiusPixels;
                int minZ = (anchorChunkZ << 4) - radiusPixels;
                int span = radiusPixels * 2;
                List<GtceuIntegration.GtOreVein> veins = GtceuIntegration.getVeinsInArea(
                        mc.level.dimension().location(), minX, minZ, span, span);
                for (GtceuIntegration.GtOreVein vein : veins) {
                    int vx = (int) viewport.toScreenX(radiusPixels + (vein.center().getX() - (anchorChunkX << 4)), 0);
                    int vy = (int) viewport.toScreenY(radiusPixels + (vein.center().getZ() - (anchorChunkZ << 4)), 0);
                    if (vx < MARGIN || vx > width - MARGIN || vy < MARGIN || vy > height - MARGIN) continue;
                    if (vein.icon().isEmpty()) {
                        // No raw ore item registered for this vein's material (exotic/fluid-only
                        // materials) — fall back to a generic shape instead of drawing nothing.
                        WaypointIconManager.draw(g, "STAR", vx, vy, iconR, vein.colorArgb());
                    } else {
                        int itemSize = Math.max(8, iconR * 2);
                        g.pose().pushPose();
                        g.pose().translate(vx - itemSize / 2.0, vy - itemSize / 2.0, 0);
                        g.pose().scale(itemSize / 16f, itemSize / 16f, 1f);
                        g.renderItem(vein.icon(), 0, 0);
                        g.pose().popPose();
                    }
                    if (mx >= vx - iconR && mx <= vx + iconR && my >= vy - iconR && my <= vy + iconR) {
                        hoveredVeinName = vein.name();
                    }
                }
            } catch (Throwable t) {
                gtceuBroken = true;
                PhoenixSolaris.LOGGER.error(
                        "GTCEu is present but its integration failed — disabling ore vein markers for the rest of this session.",
                        t);
            }
        }

        // Other nearby players — same marker style as the local player (face + facing chevron)
        // so it's obvious who's who at a glance, like JourneyMap. mc.level.players() is already
        // scoped to the current dimension, so no dimension filtering is needed.
        hoveredPlayerName = null;
        if (mc.level != null) {
            for (AbstractClientPlayer other : mc.level.players()) {
                if (other == mc.player) continue;
                double opx = radiusPixels + (other.getX() - (anchorChunkX << 4));
                double opz = radiusPixels + (other.getZ() - (anchorChunkZ << 4));
                int ox = (int) viewport.toScreenX(opx, 0);
                int oy = (int) viewport.toScreenY(opz, 0);
                if (ox < MARGIN || ox > width - MARGIN || oy < MARGIN || oy > height - MARGIN) continue;
                PlayerArrow.draw(g, ox, oy, playerR, other.getYRot(), 0xFFAAAAAA, other.getSkinTextureLocation());
                if (mx >= ox - playerR && mx <= ox + playerR && my >= oy - playerR && my <= oy + playerR) {
                    hoveredPlayerName = other.getGameProfile().getName();
                }
            }
        }

        if (hasPlayerMarker) {
            int cx = (int) viewport.toScreenX(playerPixelX, 0);
            int cy = (int) viewport.toScreenY(playerPixelZ, 0);
            float yaw = mc.player != null ? mc.player.getYRot() : 0f;
            ResourceLocation skin = mc.player != null ? mc.player.getSkinTextureLocation() : null;
            PlayerArrow.draw(g, cx, cy, playerR, yaw, C_ACCENT, skin);
        }
        g.disableScissor();

        if (hoveredVeinName != null && !menu.isOpen()) {
            g.renderTooltip(font, Component.literal(hoveredVeinName), mx, my);
        } else if (hoveredPlayerName != null && !menu.isOpen()) {
            g.renderTooltip(font, Component.literal(hoveredPlayerName), mx, my);
        }

        super.render(g, mx, my, partialTick);

        g.drawCenteredString(font, title, width / 2, 6, C_ACCENT);
        g.drawCenteredString(font, "Right-click for options", width / 2, height - MARGIN + 10, C_TEXT);

        menu.render(g, font, mx, my, C_PANEL, C_BORDER, C_TEXT, C_ACCENT);

        if (!menu.isOpen() && !dragging && SolarisConfig.SHOW_BLOCK_TOOLTIP.get()) {
            renderBlockTooltip(g, mx, my);
        }
    }

    /**
     * Explicitly an opt-in "cheat" toggle (off by default) rather than always-on: unlike the
     * rest of the map, this reveals block identity — including in chunks you haven't actually
     * looked at up close — which is real information you wouldn't otherwise have.
     */
    private void renderBlockTooltip(GuiGraphics g, int mx, int my) {
        if (mx < MARGIN || mx > width - MARGIN || my < MARGIN || my > height - MARGIN) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double texLocalX = viewport.toWorldX(mx, 0);
        double texLocalZ = viewport.toWorldZ(my, 0);
        int blockX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));
        int blockY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;

        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        Component name = mc.level.getBlockState(pos).getBlock().getName();
        g.renderTooltip(font, name, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.isOpen()) return menu.mouseClicked(mx, my, font);

        // Widgets (buttons, text fields) get first refusal — this is what the big Theme/
        // Waypoints buttons were missing before, so clicking them never did anything.
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 0) {
            dragging = true;
            return true;
        }
        if (button == 1) {
            openContextMenu(mx, my);
            return true;
        }
        return false;
    }

    private void openContextMenu(double mx, double my) {
        boolean onMap = mx >= MARGIN && mx <= width - MARGIN && my >= MARGIN && my <= height - MARGIN;
        int menuX = (int) mx;
        int menuY = (int) my;

        var items = new java.util.ArrayList<ContextMenu.Item>();
        if (onMap) {
            items.add(new ContextMenu.Item("Add Waypoint Here", () -> openQuickWaypoint(mx, my)));
        }
        items.add(new ContextMenu.Item("Waypoints...",
                () -> Minecraft.getInstance().setScreen(new WaypointListScreen(this))));
        items.add(new ContextMenu.Item("Theme...",
                () -> Minecraft.getInstance().setScreen(new SolarisThemeEditorScreen(this))));
        items.add(new ContextMenu.Item("Settings...",
                () -> Minecraft.getInstance().setScreen(new SolarisDisplaySettingsScreen(this))));

        menu.open(menuX, menuY, items);
    }

    private void openQuickWaypoint(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double texLocalX = viewport.toWorldX(mx, 0);
        double texLocalZ = viewport.toWorldZ(my, 0);
        int blockX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));

        Minecraft.getInstance().setScreen(
                new QuickWaypointScreen(this, mc.level.dimension().location(), blockX, mc.player.getBlockY(), blockZ));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            viewport.pan(dx, dy);
            clampViewport();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (viewport.adjustZoomToAnchor(delta, mx, my, 0, 0)) {
            clampViewport();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
