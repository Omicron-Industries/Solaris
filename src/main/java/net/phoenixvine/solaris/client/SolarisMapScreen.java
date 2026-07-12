package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.plan.PlanShape;
import net.phoenixvine.solaris.client.plan.PlanShapeListScreen;
import net.phoenixvine.solaris.client.plan.PlanShapeManager;
import net.phoenixvine.solaris.client.plan.QuickPlanShapeScreen;
import net.phoenixvine.solaris.client.render.LabelSide;
import net.phoenixvine.solaris.client.render.LineRenderer;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.MobFaceIcons;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SmoothShapes;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.globe.GlobeCamera;
import net.phoenixvine.solaris.client.render.globe.SolarisGlobeRenderer;
import net.phoenixvine.solaris.client.render.globe.SphereMesh;
import net.phoenixvine.solaris.client.waypoint.QuickWaypointScreen;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointIconManager;
import net.phoenixvine.solaris.client.waypoint.WaypointListScreen;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BG;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

/** Fullscreen pannable/zoomable terrain map. */
@OnlyIn(Dist.CLIENT)
public class SolarisMapScreen extends Screen {

    private static final int MARGIN = 20;
    private static final float MIN_ZOOM_OVERSCAN = 1.2f;
    private static final int BUTTON_R = 9;
    private static final int BUTTON_MARGIN = 14;
    private static final int BUTTON_GAP = 22;

    private static SolarisTexture texture;

    /** FLAT is the pan/zoom 2D map; GLOBE wraps the same terrain texture onto a rotatable sphere. */
    private enum ViewMode {
        FLAT,
        GLOBE
    }

    /**
     * NAVIGATE is the default pan/waypoint mode; the DRAW_* modes are the shape planner's
     * drawing tools, flat-map only (see {@code PlanShape}'s plan doc on why placing shapes on
     * the globe is out of scope for v1).
     */
    private enum ToolMode {
        NAVIGATE,
        DRAW_RECTANGLE,
        DRAW_CIRCLE,
        DRAW_LINE
    }

    private final MapViewport viewport = new MapViewport(
            SolarisConfig.ZOOM_MIN.get().floatValue(), SolarisConfig.ZOOM_MAX.get().floatValue());
    private final List<IconButton> iconButtons = new ArrayList<>();
    private ViewMode mode = ViewMode.FLAT;
    private GlobeCamera globeCamera;
    private final SphereMesh globeMesh = new SphereMesh();
    private boolean dragging = false;
    private ToolMode toolMode = ToolMode.NAVIGATE;
    /** True only while a RECTANGLE/CIRCLE shape's initial click-drag is in progress. */
    private boolean shapeDragging = false;
    /** World (x, z) block coordinates — RECTANGLE/CIRCLE's fixed first corner/center. */
    private int[] drawAnchor;
    /** World (x, z) block coordinates — RECTANGLE/CIRCLE's live second corner/edge, follows the drag. */
    private int[] drawCurrent;
    /** LINE mode's points so far, in click order. */
    private final List<int[]> lineDrawPoints = new ArrayList<>();
    private boolean hasPlayerMarker = false;
    private double playerPixelX;
    private double playerPixelZ;
    private int anchorChunkX;
    private int anchorChunkZ;
    private int radiusPixels;
    private ChunkKey centerKey;
    private String hoveredVeinName;
    private String hoveredPlayerName;
    private String hoveredMobName;

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

    /**
     * A persistent icon button drawn in a corner of the map — replaced the old right-click
     * dropdown menu (Waypoints/Theme/Settings) with these three always-visible buttons instead,
     * since a dropdown menu invited more precise clicking than a screen you're actively panning
     * and zooming around in really wants. Settings sits alone bottom-left (it's the "deep,
     * grained" one); Waypoints and Theme share the bottom-right corner.
     */
    private record IconButton(int cx, int cy, String label, java.util.function.BiConsumer<GuiGraphics, Boolean> draw,
                              Runnable action) {

        boolean hit(double mx, double my) {
            double dx = mx - cx;
            double dy = my - cy;
            return dx * dx + dy * dy <= (double) BUTTON_R * BUTTON_R;
        }
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

        // Globe screen-radius bounds: sized off the same frame span the flat map's zoom floor
        // uses. Unlike the flat map, the frame's scissor already clips anything that overflows
        // it, so the max is allowed to zoom in well past "sphere exactly fills the frame" —
        // capping it there made zooming in for a close look at terrain cut off too early.
        float frameFitScale = viewportSpan > 0 ? viewportSpan / 2f : 100f;
        globeCamera = new GlobeCamera(frameFitScale * 0.4f, frameFitScale * 5f);

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
        buildIconButtons();
    }

    // Real vanilla item icons instead of hand-drawn glyphs — a map, a painting, and a
    // comparator (the closest thing vanilla has to a "settings dial") all read clearly at
    // small sizes and look sharp already, instead of needing more custom vector-icon work.
    private static final ItemStack WAYPOINTS_ICON = new ItemStack(Items.FILLED_MAP);
    private static final ItemStack THEME_ICON = new ItemStack(Items.PAINTING);
    private static final ItemStack SETTINGS_ICON = new ItemStack(Items.COMPARATOR);
    private static final ItemStack PLAN_ICON = new ItemStack(Items.SCAFFOLDING);
    private static final ItemStack SHAPES_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack EXPORT_ICON = new ItemStack(Items.PAPER);

    private void buildIconButtons() {
        iconButtons.clear();

        int settingsX = BUTTON_MARGIN + BUTTON_R;
        int bottomY = height - BUTTON_MARGIN - BUTTON_R;
        iconButtons.add(new IconButton(settingsX, bottomY, "Settings",
                (g, hover) -> drawItemIcon(g, settingsX, bottomY, SETTINGS_ICON),
                () -> Minecraft.getInstance().setScreen(new SolarisDisplaySettingsScreen(this))));

        int exportX = settingsX + BUTTON_GAP;
        iconButtons.add(new IconButton(exportX, bottomY, "Export as PNG",
                (g, hover) -> drawItemIcon(g, exportX, bottomY, EXPORT_ICON),
                () -> texture().exportToPng(SolarisMapScreen::sendMapMessage)));

        int waypointsX = width - BUTTON_MARGIN - BUTTON_R;
        iconButtons.add(new IconButton(waypointsX, bottomY, "Waypoints",
                (g, hover) -> drawItemIcon(g, waypointsX, bottomY, WAYPOINTS_ICON),
                () -> Minecraft.getInstance().setScreen(new WaypointListScreen(this))));

        int themeX = waypointsX - BUTTON_GAP;
        iconButtons.add(new IconButton(themeX, bottomY, "Theme",
                (g, hover) -> drawItemIcon(g, themeX, bottomY, THEME_ICON),
                () -> Minecraft.getInstance().setScreen(new SolarisThemeEditorScreen(this))));

        // No vanilla item reads as "3D globe" — a hand-drawn ring + equator/meridian cross is
        // the one deliberate exception to the real-item-icon rule above.
        int globeX = themeX - BUTTON_GAP;
        iconButtons.add(new IconButton(globeX, bottomY, "Globe View",
                (g, hover) -> drawGlobeIcon(g, globeX, bottomY),
                () -> {
                    // Drawing is flat-map only — switching to the globe mid-shape would leave
                    // dangling, meaningless draw state, so cancel it on the way out.
                    if (mode == ViewMode.FLAT) cancelDrawing();
                    mode = mode == ViewMode.FLAT ? ViewMode.GLOBE : ViewMode.FLAT;
                }));

        int shapesX = globeX - BUTTON_GAP;
        iconButtons.add(new IconButton(shapesX, bottomY, "Shapes",
                (g, hover) -> drawItemIcon(g, shapesX, bottomY, SHAPES_ICON),
                () -> Minecraft.getInstance().setScreen(new PlanShapeListScreen(this))));

        int planX = shapesX - BUTTON_GAP;
        iconButtons.add(new IconButton(planX, bottomY, "Plan",
                (g, hover) -> drawItemIcon(g, planX, bottomY, PLAN_ICON),
                () -> {
                    // Drawing is flat-map only — jump there automatically instead of a dead click.
                    mode = ViewMode.FLAT;
                    switchDrawTool(ToolMode.DRAW_RECTANGLE);
                }));
    }

    /**
     * {@link SolarisTexture#exportToPng}'s callback fires from {@code Util.ioPool()}'s worker
     * thread (a disk write, off the render thread), so this hops back onto the main thread
     * before touching {@code mc.player} — the same reason vanilla's own {@code Screenshot.grab}
     * callback is always invoked via a main-thread-safe message consumer.
     */
    private static void sendMapMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(message, false);
        });
    }

    private static void drawItemIcon(GuiGraphics g, int cx, int cy, ItemStack stack) {
        int size = BUTTON_R * 2 - 2;
        g.pose().pushPose();
        g.pose().translate(cx - size / 2.0, cy - size / 2.0, 0);
        g.pose().scale(size / 16f, size / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static final int MOB_ICON_COLOR_HOSTILE = 0xFFCC4444;
    private static final int MOB_ICON_COLOR_PASSIVE = 0xFF55AA55;

    /**
     * The mob's own actual face — a real static texture crop ({@link MobFaceIcons}) for the
     * humanoid-model family it covers, matching the player marker's square-badge-plus-face
     * composition (flat color fill, the face on top, a thin inscribed ring), just without the
     * player badge's rotating facing chevron since a flat face crop doesn't rotate along with it.
     * Falls back to {@link PlayerArrow}'s plain flat square (its existing no-skin look) for mob
     * types {@link MobFaceIcons} doesn't cover yet, rather than guessing at an unverified crop.
     * Replaces two earlier attempts (a spawn-egg item, then a live-rendered 3D entity model) that
     * didn't read as "static little squares" the way the player marker itself does.
     */
    private static void drawMobIcon(GuiGraphics g, Mob mob, int x, int y, int r) {
        int color = mob instanceof Enemy ? MOB_ICON_COLOR_HOSTILE : MOB_ICON_COLOR_PASSIVE;
        if (MobFaceIcons.isSupported(mob.getType())) {
            g.fill(x - r, y - r, x + r, y + r, color);
            MobFaceIcons.draw(g, mob, x, y, r * 2);
            SmoothShapes.drawRing(g, x, y, r, color);
        } else {
            PlayerArrow.draw(g, x, y, r, mob.getYRot(), color, null);
        }
    }

    private static void drawGlobeIcon(GuiGraphics g, int cx, int cy) {
        int r = BUTTON_R - 2;
        SmoothShapes.drawRing(g, cx, cy, r, C_TEXT);
        g.fill(cx - r, cy, cx + r, cy + 1, C_TEXT);
        g.fill(cx, cy - r, cx + 1, cy + r, C_TEXT);
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

        ModernPanel.draw(g, MARGIN - 4, MARGIN - 4, width - 2 * (MARGIN - 4), height - 2 * (MARGIN - 4), C_BORDER);
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

        ModernPanel.draw(g, MARGIN - 4, MARGIN - 4, width - 2 * (MARGIN - 4), height - 2 * (MARGIN - 4), C_BORDER);
        g.enableScissor(MARGIN, MARGIN, width - MARGIN, height - MARGIN);
        g.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, C_BG);

        if (mode == ViewMode.FLAT) {
            renderFlatMap(g, mx, my);
        } else {
            renderGlobeMap(g, mx, my);
        }

        g.disableScissor();

        super.render(g, mx, my, partialTick);

        g.drawCenteredString(font, title, width / 2, 6, C_ACCENT);
        g.drawCenteredString(font, footerHint(), width / 2, height - MARGIN + 10, C_TEXT);

        String hoveredButton = renderIconButtons(g, mx, my);

        // Exactly ONE tooltip renders per frame, picked by priority — vein/player name, then a
        // hovered button's label, then (lowest priority, since it's an opt-in "cheat" toggle)
        // the block-identity tooltip. Two of these calling g.renderTooltip in the same frame at
        // the same mouse position is what produced garbled, overlapping tooltip text before —
        // first between the block tooltip and a button's, now between it and a vein's.
        Component tooltip;
        if (hoveredVeinName != null) {
            tooltip = Component.literal(hoveredVeinName);
        } else if (hoveredPlayerName != null) {
            tooltip = Component.literal(hoveredPlayerName);
        } else if (hoveredMobName != null) {
            tooltip = Component.literal(hoveredMobName);
        } else if (hoveredButton != null) {
            tooltip = Component.literal(hoveredButton);
        } else if (mode == ViewMode.FLAT && !dragging && toolMode == ToolMode.NAVIGATE &&
                SolarisConfig.SHOW_BLOCK_TOOLTIP.get()) {
                    // Only meaningful in flat mode — it reads terrain height under the exact clicked
                    // pixel via MapViewport's world-space math, which the globe doesn't use.
                    tooltip = hoveredBlockName(mx, my);
                } else {
                    tooltip = null;
                }
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mx, my);
        }
    }

    private String footerHint() {
        return switch (toolMode) {
            case NAVIGATE -> "Right-click: new waypoint (or delete one)";
            case DRAW_RECTANGLE -> "Rectangle — drag to draw · R/C/L: switch tool · Esc: cancel";
            case DRAW_CIRCLE -> "Circle — drag to draw · R/C/L: switch tool · Esc: cancel";
            case DRAW_LINE -> "Line — click to add points · Enter/right-click: finish · Esc: cancel";
        };
    }

    /** The original flat pan/zoom terrain blit + waypoint/vein/player marker loop, unchanged from before globe mode. */
    private void renderFlatMap(GuiGraphics g, int mx, int my) {
        SolarisTexture tex = texture();
        // Cheap no-op unless something actually invalidated the texture (e.g. a Settings
        // slider or a claim/waypoint change while this screen is already open) — without this,
        // those changes only took effect the next time the map was reopened, since the only
        // other maybeRebuild call is in init().
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        int destX = (int) viewport.toScreenX(0, 0);
        int destY = (int) viewport.toScreenY(0, 0);
        int destSize = (int) (size * viewport.getZoom());
        g.blit(tex.textureId(), destX, destY, destSize, destSize, 0, 0, size, size, size, size);

        // Marker size scales with zoom (clamped so they never vanish zoomed-out or balloon zoomed-in),
        // then with the player's configured icon size on top of that — markers are drawn "on"
        // the map, not as fixed-size UI chrome floating over it.
        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(Mth.clamp(3f * viewport.getZoom(), 2f, 10f) * iconScale);
        int playerR = Math.max(4, Math.min(12, Math.round(3.5f * viewport.getZoom())));

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
                g.drawString(font, w.name, lx, ly, w.labelColorArgb(C_TEXT), true);
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

        // Nearby living mobs — same treatment as other players (no range cap here either,
        // entitiesForRendering() is already scoped to what the client has actually loaded).
        hoveredMobName = null;
        if (SolarisConfig.SHOW_MOBS.get() && mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
                double mpx = radiusPixels + (mob.getX() - (anchorChunkX << 4));
                double mpz = radiusPixels + (mob.getZ() - (anchorChunkZ << 4));
                int mobX = (int) viewport.toScreenX(mpx, 0);
                int mobY = (int) viewport.toScreenY(mpz, 0);
                if (mobX < MARGIN || mobX > width - MARGIN || mobY < MARGIN || mobY > height - MARGIN) continue;
                drawMobIcon(g, mob, mobX, mobY, iconR);
                if (mx >= mobX - iconR && mx <= mobX + iconR && my >= mobY - iconR && my <= mobY + iconR) {
                    hoveredMobName = mob.getName().getString();
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

        if (mc.level != null) {
            for (PlanShape shape : PlanShapeManager.getVisibleForDimension(mc.level.dimension().location())) {
                drawShapeFlat(g, shape, shape.colorArgb(), 2);
            }
        }
        drawShapePreview(g, mx, my);
    }

    private double shapePixelX(int worldX) {
        return radiusPixels + (worldX - (anchorChunkX << 4));
    }

    private double shapePixelZ(int worldZ) {
        return radiusPixels + (worldZ - (anchorChunkZ << 4));
    }

    /** Flat-map outline for one shape, saved or (via {@link #drawShapePreview}) still being drawn. */
    private void drawShapeFlat(GuiGraphics g, PlanShape shape, int color, int thickness) {
        switch (shape.type) {
            case RECTANGLE -> {
                int[] p1 = shape.points.get(0);
                int[] p2 = shape.points.get(1);
                int x1 = (int) viewport.toScreenX(shapePixelX(p1[0]), 0);
                int z1 = (int) viewport.toScreenY(shapePixelZ(p1[1]), 0);
                int x2 = (int) viewport.toScreenX(shapePixelX(p2[0]), 0);
                int z2 = (int) viewport.toScreenY(shapePixelZ(p2[1]), 0);
                LineRenderer.drawLine(g, x1, z1, x2, z1, thickness, color);
                LineRenderer.drawLine(g, x2, z1, x2, z2, thickness, color);
                LineRenderer.drawLine(g, x2, z2, x1, z2, thickness, color);
                LineRenderer.drawLine(g, x1, z2, x1, z1, thickness, color);
            }
            case CIRCLE -> {
                int[] c = shape.points.get(0);
                int cx = (int) viewport.toScreenX(shapePixelX(c[0]), 0);
                int cz = (int) viewport.toScreenY(shapePixelZ(c[1]), 0);
                int screenRadius = Math.max(2, Math.round(shape.radius * viewport.getZoom()));
                SmoothShapes.drawRing(g, cx, cz, screenRadius, color);
            }
            case LINE -> drawPolylineFlat(g, shape.points, color, thickness);
        }
    }

    private void drawPolylineFlat(GuiGraphics g, List<int[]> points, int color, int thickness) {
        int[] prevScreen = null;
        for (int[] p : points) {
            int sx = (int) viewport.toScreenX(shapePixelX(p[0]), 0);
            int sz = (int) viewport.toScreenY(shapePixelZ(p[1]), 0);
            if (prevScreen != null) LineRenderer.drawLine(g, prevScreen[0], prevScreen[1], sx, sz, thickness, color);
            prevScreen = new int[] { sx, sz };
        }
    }

    /** The not-yet-saved shape currently being drawn, if any — same visual family as saved shapes, thinner/white. */
    private void drawShapePreview(GuiGraphics g, int mx, int my) {
        int previewColor = 0xFFFFFFFF;
        if ((toolMode == ToolMode.DRAW_RECTANGLE || toolMode == ToolMode.DRAW_CIRCLE) && drawAnchor != null &&
                drawCurrent != null) {
            if (toolMode == ToolMode.DRAW_RECTANGLE) {
                PlanShape preview = new PlanShape();
                preview.type = PlanShape.Type.RECTANGLE;
                preview.points = List.of(drawAnchor, drawCurrent);
                drawShapeFlat(g, preview, previewColor, 1);
            } else {
                int cx = (int) viewport.toScreenX(shapePixelX(drawAnchor[0]), 0);
                int cz = (int) viewport.toScreenY(shapePixelZ(drawAnchor[1]), 0);
                double dx = drawCurrent[0] - drawAnchor[0];
                double dz = drawCurrent[1] - drawAnchor[1];
                int screenRadius = Math.max(2, (int) Math.round(Math.sqrt(dx * dx + dz * dz) * viewport.getZoom()));
                SmoothShapes.drawRing(g, cx, cz, screenRadius, previewColor);
            }
        } else if (toolMode == ToolMode.DRAW_LINE && !lineDrawPoints.isEmpty()) {
            drawPolylineFlat(g, lineDrawPoints, previewColor, 1);
            int[] last = lineDrawPoints.get(lineDrawPoints.size() - 1);
            int sx = (int) viewport.toScreenX(shapePixelX(last[0]), 0);
            int sz = (int) viewport.toScreenY(shapePixelZ(last[1]), 0);
            LineRenderer.drawLine(g, sx, sz, mx, my, 1, previewColor);
        }
    }

    /**
     * The globe counterpart of {@link #renderFlatMap} — draws the same {@link SolarisTexture}
     * wrapped onto a rotating sphere via {@link SolarisGlobeRenderer}, then projects the same
     * waypoint/vein/player markers onto the sphere's visible (front-facing) surface with
     * {@link GlobeCamera#sphereToScreen}, reusing the exact same 2D icon-drawing calls as flat
     * mode — projection is a plain ortho scale+rotate (no perspective divide), so no separate
     * "3D marker" rendering path is needed. Marker screen size is a fixed constant here rather
     * than zoom-scaled: globe zoom is the sphere's on-screen radius, not a distance metric that
     * maps onto marker size the way flat {@code MapViewport} zoom does.
     */
    private void renderGlobeMap(GuiGraphics g, int mx, int my) {
        SolarisTexture tex = texture();
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        int cx = width / 2;
        int cy = height / 2;
        float screenRadius = globeCamera.getScale();
        Minecraft mc = Minecraft.getInstance();
        int seaLevel = mc.level != null ? mc.level.getSeaLevel() : 63;
        globeMesh.rebuild(tex.getHeights(), size, seaLevel);
        SolarisGlobeRenderer.renderSphere(g, cx, cy, screenRadius, globeCamera.rotationQuaternion(),
                tex.ensureGlobeTexture(), globeMesh);

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(6f * iconScale);
        int playerR = 7;

        LabelSide labelSide = SolarisConfig.LABEL_SIDE.get();

        if (mc.level != null) {
            List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
            for (Waypoint w : waypoints) {
                GlobeCamera.Projection p = globeCamera.sphereToScreen(
                        (float) (waypointPixelX(w) / size), (float) (waypointPixelZ(w) / size), cx, cy);
                if (!p.frontFacing) continue;
                WaypointIconManager.draw(g, w.icon, p.screenX, p.screenY, iconR, w.colorArgb());
                int lx = labelSide.drawX(p.screenX, iconR, font.width(w.name));
                int ly = labelSide.drawY(p.screenY, iconR, font.lineHeight);
                g.drawString(font, w.name, lx, ly, w.labelColorArgb(C_TEXT), true);
            }
        }

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
                    double vpx = radiusPixels + (vein.center().getX() - (anchorChunkX << 4));
                    double vpz = radiusPixels + (vein.center().getZ() - (anchorChunkZ << 4));
                    GlobeCamera.Projection p = globeCamera.sphereToScreen((float) (vpx / size), (float) (vpz / size),
                            cx, cy);
                    if (!p.frontFacing) continue;
                    if (vein.icon().isEmpty()) {
                        WaypointIconManager.draw(g, "STAR", p.screenX, p.screenY, iconR, vein.colorArgb());
                    } else {
                        int itemSize = Math.max(8, iconR * 2);
                        g.pose().pushPose();
                        g.pose().translate(p.screenX - itemSize / 2.0, p.screenY - itemSize / 2.0, 0);
                        g.pose().scale(itemSize / 16f, itemSize / 16f, 1f);
                        g.renderItem(vein.icon(), 0, 0);
                        g.pose().popPose();
                    }
                    if (mx >= p.screenX - iconR && mx <= p.screenX + iconR && my >= p.screenY - iconR &&
                            my <= p.screenY + iconR) {
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

        hoveredPlayerName = null;
        if (mc.level != null) {
            for (AbstractClientPlayer other : mc.level.players()) {
                if (other == mc.player) continue;
                double opx = radiusPixels + (other.getX() - (anchorChunkX << 4));
                double opz = radiusPixels + (other.getZ() - (anchorChunkZ << 4));
                GlobeCamera.Projection p = globeCamera.sphereToScreen((float) (opx / size), (float) (opz / size), cx,
                        cy);
                if (!p.frontFacing) continue;
                PlayerArrow.draw(g, p.screenX, p.screenY, playerR, other.getYRot(), 0xFFAAAAAA,
                        other.getSkinTextureLocation());
                if (mx >= p.screenX - playerR && mx <= p.screenX + playerR && my >= p.screenY - playerR &&
                        my <= p.screenY + playerR) {
                    hoveredPlayerName = other.getGameProfile().getName();
                }
            }
        }

        hoveredMobName = null;
        if (SolarisConfig.SHOW_MOBS.get() && mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
                double mpx = radiusPixels + (mob.getX() - (anchorChunkX << 4));
                double mpz = radiusPixels + (mob.getZ() - (anchorChunkZ << 4));
                GlobeCamera.Projection p = globeCamera.sphereToScreen((float) (mpx / size), (float) (mpz / size), cx,
                        cy);
                if (!p.frontFacing) continue;
                drawMobIcon(g, mob, p.screenX, p.screenY, iconR);
                if (mx >= p.screenX - iconR && mx <= p.screenX + iconR && my >= p.screenY - iconR &&
                        my <= p.screenY + iconR) {
                    hoveredMobName = mob.getName().getString();
                }
            }
        }

        if (hasPlayerMarker) {
            GlobeCamera.Projection p = globeCamera.sphereToScreen(
                    (float) (playerPixelX / size), (float) (playerPixelZ / size), cx, cy);
            if (p.frontFacing) {
                float yaw = mc.player != null ? mc.player.getYRot() : 0f;
                ResourceLocation skin = mc.player != null ? mc.player.getSkinTextureLocation() : null;
                PlayerArrow.draw(g, p.screenX, p.screenY, playerR, yaw, C_ACCENT, skin);
            }
        }
    }

    /** Draws the corner icon buttons (background chrome + glyph); returns the hovered one's label, if any. */
    private String renderIconButtons(GuiGraphics g, int mx, int my) {
        String hoveredLabel = null;
        for (IconButton b : iconButtons) {
            boolean hover = b.hit(mx, my);
            if (hover) hoveredLabel = b.label();
            g.fill(b.cx() - BUTTON_R - 1, b.cy() - BUTTON_R - 1, b.cx() + BUTTON_R + 1, b.cy() + BUTTON_R + 1,
                    C_BORDER);
            g.fill(b.cx() - BUTTON_R, b.cy() - BUTTON_R, b.cx() + BUTTON_R, b.cy() + BUTTON_R,
                    hover ? C_HEADER : C_PANEL);
            b.draw().accept(g, hover);
        }
        return hoveredLabel;
    }

    /**
     * Explicitly an opt-in "cheat" toggle (off by default) rather than always-on: unlike the
     * rest of the map, this reveals block identity — including in chunks you haven't actually
     * looked at up close — which is real information you wouldn't otherwise have.
     */
    private Component hoveredBlockName(int mx, int my) {
        if (mx < MARGIN || mx > width - MARGIN || my < MARGIN || my > height - MARGIN) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        double texLocalX = viewport.toWorldX(mx, 0);
        double texLocalZ = viewport.toWorldZ(my, 0);
        int blockX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));
        int blockY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;

        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        return mc.level.getBlockState(pos).getBlock().getName();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Widgets (buttons, text fields) get first refusal — this is what the big Theme/
        // Waypoints buttons were missing before, so clicking them never did anything.
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 0) {
            for (IconButton b : iconButtons) {
                if (b.hit(mx, my)) {
                    b.action().run();
                    return true;
                }
            }
            if (toolMode == ToolMode.DRAW_LINE) {
                if (onMap(mx, my)) lineDrawPoints.add(worldPointAt(mx, my));
                return true;
            }
            if (toolMode == ToolMode.DRAW_RECTANGLE || toolMode == ToolMode.DRAW_CIRCLE) {
                if (onMap(mx, my)) {
                    drawAnchor = worldPointAt(mx, my);
                    drawCurrent = drawAnchor;
                    shapeDragging = true;
                }
                return true;
            }
            dragging = true;
            return true;
        }
        if (button == 1) {
            if (toolMode == ToolMode.DRAW_LINE) {
                finishLine();
                return true;
            }
            if (toolMode == ToolMode.DRAW_RECTANGLE || toolMode == ToolMode.DRAW_CIRCLE) {
                cancelDrawing();
                return true;
            }
            handleRightClick(mx, my);
            return true;
        }
        return false;
    }

    private boolean onMap(double mx, double my) {
        return mx >= MARGIN && mx <= width - MARGIN && my >= MARGIN && my <= height - MARGIN;
    }

    /** Screen position to world block (x, z) — same conversion {@link #handleRightClick} already used inline. */
    private int[] worldPointAt(double mx, double my) {
        double texLocalX = viewport.toWorldX(mx, 0);
        double texLocalZ = viewport.toWorldZ(my, 0);
        int blockX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));
        return new int[] { blockX, blockZ };
    }

    private void switchDrawTool(ToolMode newTool) {
        toolMode = newTool;
        shapeDragging = false;
        drawAnchor = null;
        drawCurrent = null;
        lineDrawPoints.clear();
    }

    private void cancelDrawing() {
        switchDrawTool(ToolMode.NAVIGATE);
    }

    /**
     * RECTANGLE/CIRCLE finish on mouse release; LINE finishes explicitly (right-click/Enter, see {@link #keyPressed}).
     */
    private void finishRectOrCircle() {
        if (drawAnchor == null || drawCurrent == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            drawAnchor = null;
            drawCurrent = null;
            return;
        }

        // A click without a real drag — nothing to save.
        if (drawAnchor[0] == drawCurrent[0] && drawAnchor[1] == drawCurrent[1]) {
            drawAnchor = null;
            drawCurrent = null;
            return;
        }

        PlanShape.Type type = toolMode == ToolMode.DRAW_RECTANGLE ? PlanShape.Type.RECTANGLE : PlanShape.Type.CIRCLE;
        List<int[]> points = new ArrayList<>();
        int radius = 0;
        int centroidX;
        int centroidZ;
        if (type == PlanShape.Type.RECTANGLE) {
            points.add(drawAnchor);
            points.add(drawCurrent);
            centroidX = (drawAnchor[0] + drawCurrent[0]) / 2;
            centroidZ = (drawAnchor[1] + drawCurrent[1]) / 2;
        } else {
            points.add(drawAnchor);
            double dx = drawCurrent[0] - drawAnchor[0];
            double dz = drawCurrent[1] - drawAnchor[1];
            radius = Math.max(1, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
            centroidX = drawAnchor[0];
            centroidZ = drawAnchor[1];
        }
        int baseY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, centroidX, centroidZ) - 1;

        drawAnchor = null;
        drawCurrent = null;
        Minecraft.getInstance().setScreen(
                new QuickPlanShapeScreen(this, mc.level.dimension().location(), type, points, radius, baseY));
    }

    private void finishLine() {
        if (lineDrawPoints.size() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<int[]> points = new ArrayList<>(lineDrawPoints);
        int sumX = 0;
        int sumZ = 0;
        for (int[] p : points) {
            sumX += p[0];
            sumZ += p[1];
        }
        int centroidX = sumX / points.size();
        int centroidZ = sumZ / points.size();
        int baseY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, centroidX, centroidZ) - 1;

        lineDrawPoints.clear();
        Minecraft.getInstance().setScreen(new QuickPlanShapeScreen(this, mc.level.dimension().location(),
                PlanShape.Type.LINE, points, 0, baseY));
    }

    /**
     * Right-click on a waypoint deletes it; right-click anywhere else on the map opens the
     * "new waypoint" popup directly — no intermediate dropdown menu. A right-click menu asks
     * for careful clicking on a screen you're actively panning/zooming around in, which is
     * exactly the wrong fit here; the persistent corner icon buttons (see {@link #iconButtons})
     * cover the rest of what that menu used to do (Waypoints, Theme, Settings).
     */
    private void handleRightClick(double mx, double my) {
        if (mode == ViewMode.GLOBE) {
            handleRightClickGlobe(mx, my);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Waypoint hit = hitTestWaypoint(mx, my);
        if (hit != null) {
            WaypointManager.remove(hit.id);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Waypoint removed: " + hit.name), true);
            }
            return;
        }

        boolean onMap = mx >= MARGIN && mx <= width - MARGIN && my >= MARGIN && my <= height - MARGIN;
        if (!onMap || mc.player == null || mc.level == null) return;

        double texLocalX = viewport.toWorldX(mx, 0);
        double texLocalZ = viewport.toWorldZ(my, 0);
        int blockX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));
        // The actual terrain height under the clicked spot, not the player's own Y — clicking
        // somewhere far away on the map (the whole point of a wide map radius) previously still
        // used wherever the player happened to be standing, so the waypoint's beam/floating
        // icon ended up floating in midair or buried underground once you actually got there.
        int blockY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;

        Minecraft.getInstance().setScreen(
                new QuickWaypointScreen(this, mc.level.dimension().location(), blockX, blockY, blockZ));
    }

    /** Same icon-radius math {@link #render} uses for markers, kept in sync so hit-testing matches what's drawn. */
    private Waypoint hitTestWaypoint(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(Mth.clamp(3f * viewport.getZoom(), 2f, 10f) * iconScale);

        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            double wx = viewport.toScreenX(waypointPixelX(w), 0);
            double wy = viewport.toScreenY(waypointPixelZ(w), 0);
            if (mx >= wx - iconR && mx <= wx + iconR && my >= wy - iconR && my <= wy + iconR) return w;
        }
        return null;
    }

    /**
     * Globe counterpart of {@link #handleRightClick}: hit-tests waypoints by their projected
     * screen position ({@link GlobeCamera#sphereToScreen}), then falls back to
     * {@link GlobeCamera#screenToSpherePoint}'s ray-sphere intersection to find which world
     * block the click landed on for a new waypoint — same downstream calls as flat mode, just
     * fed sphere-derived coordinates instead of {@code MapViewport}'s.
     */
    private void handleRightClickGlobe(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        Waypoint hit = hitTestWaypointGlobe(mx, my);
        if (hit != null) {
            WaypointManager.remove(hit.id);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Waypoint removed: " + hit.name), true);
            }
            return;
        }

        if (mc.player == null || mc.level == null) return;

        SolarisTexture tex = texture();
        int size = tex.getSizePixels();
        GlobeCamera.SpherePoint sp = globeCamera.screenToSpherePoint(mx, my, width / 2, height / 2);
        if (sp == null) return;

        int blockX = (int) Math.floor(sp.u * size - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(sp.v * size - radiusPixels + (anchorChunkZ << 4));
        int blockY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;

        Minecraft.getInstance().setScreen(
                new QuickWaypointScreen(this, mc.level.dimension().location(), blockX, blockY, blockZ));
    }

    /**
     * Same fixed icon radius {@link #renderGlobeMap} uses for markers, kept in sync so hit-testing matches what's
     * drawn.
     */
    private Waypoint hitTestWaypointGlobe(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        SolarisTexture tex = texture();
        int size = tex.getSizePixels();
        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(6f * iconScale);
        int cx = width / 2;
        int cy = height / 2;

        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            GlobeCamera.Projection p = globeCamera.sphereToScreen(
                    (float) (waypointPixelX(w) / size), (float) (waypointPixelZ(w) / size), cx, cy);
            if (!p.frontFacing) continue;
            if (mx >= p.screenX - iconR && mx <= p.screenX + iconR && my >= p.screenY - iconR &&
                    my <= p.screenY + iconR) {
                return w;
            }
        }
        return null;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            dragging = false;
            if (shapeDragging) {
                shapeDragging = false;
                finishRectOrCircle();
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (shapeDragging) {
            drawCurrent = worldPointAt(mx, my);
            return true;
        }
        if (dragging) {
            if (mode == ViewMode.GLOBE) {
                globeCamera.rotate(dx, dy);
            } else {
                viewport.pan(dx, dy);
                clampViewport();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mode == ViewMode.GLOBE) {
            globeCamera.adjustZoom(delta);
            return true;
        }
        if (viewport.adjustZoomToAnchor(delta, mx, my, 0, 0)) {
            clampViewport();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (toolMode != ToolMode.NAVIGATE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelDrawing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_R) {
                switchDrawTool(ToolMode.DRAW_RECTANGLE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                switchDrawTool(ToolMode.DRAW_CIRCLE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L) {
                switchDrawTool(ToolMode.DRAW_LINE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER && toolMode == ToolMode.DRAW_LINE) {
                finishLine();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
