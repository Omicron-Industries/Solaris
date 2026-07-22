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
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.ChunkRailCache;
import net.phoenixvine.solaris.client.export.SolarisWebExporter;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.plan.PlanShape;
import net.phoenixvine.solaris.client.plan.PlanShapeListScreen;
import net.phoenixvine.solaris.client.plan.PlanShapeManager;
import net.phoenixvine.solaris.client.plan.QuickPlanShapeScreen;
import net.phoenixvine.solaris.client.render.LabelSide;
import net.phoenixvine.solaris.client.render.LineRenderer;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.MinimapShape;
import net.phoenixvine.solaris.client.render.MobFaceIcons;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SmoothShapes;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.TextureAddressing;
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

@OnlyIn(Dist.CLIENT)
public class SolarisMapScreen extends Screen {

    private static final int MARGIN = 20;
    private static final float MIN_ZOOM_OVERSCAN = 1.2f;
    private static final int BUTTON_R = 9;
    private static final int BUTTON_MARGIN = 14;
    private static final int BUTTON_GAP = 22;

    private static SolarisTexture texture;

    private enum ViewMode {
        FLAT,
        GLOBE
    }

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
    private long lastDragRebuildMillis = 0L;
    private ToolMode toolMode = ToolMode.NAVIGATE;

    private boolean shapeDragging = false;

    private int[] drawAnchor;

    private int[] drawCurrent;

    private final List<int[]> lineDrawPoints = new ArrayList<>();
    private boolean hasPlayerMarker = false;
    private int anchorChunkX;
    private int anchorChunkZ;
    private int radiusPixels;
    private ChunkKey centerKey;
    private String hoveredVeinName;
    private String hoveredPlayerName;
    private String hoveredMobName;

    private static boolean gtceuBroken = false;

    public SolarisMapScreen() {
        super(Component.translatable("solaris.map.title"));
    }

    private static SolarisTexture texture() {
        if (texture == null) texture = new SolarisTexture("map", SolarisConfig.MAP_RADIUS_CHUNKS.get());
        return texture;
    }

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

        int viewportSpan = Math.min(width, height) - 2 * MARGIN;
        if (viewportSpan > 0) {
            viewport.raiseZoomMin((float) viewportSpan * MIN_ZOOM_OVERSCAN / tex.getSizePixels());
        }

        float frameFitScale = viewportSpan > 0 ? viewportSpan / 2f : 100f;
        globeCamera = new GlobeCamera(frameFitScale * 0.4f, frameFitScale * 5f);

        radiusPixels = tex.getRadiusChunks() * 16;

        if (mc.player != null && mc.level != null) {
            BlockPos blockPos = mc.player.blockPosition();
            anchorChunkX = blockPos.getX() >> 4;
            anchorChunkZ = blockPos.getZ() >> 4;
            centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));
            tex.maybeRebuild(centerKey);

            hasPlayerMarker = true;

            double initialPixelX = selfPlayerPixelX(mc.player);
            double initialPixelZ = selfPlayerPixelZ(mc.player);
            viewport.setOffset(width / 2.0 - initialPixelX * viewport.getZoom(),
                    height / 2.0 - initialPixelZ * viewport.getZoom());
        }

        clampViewport();
        buildIconButtons();
    }

    private static final ItemStack WAYPOINTS_ICON = new ItemStack(Items.FILLED_MAP);
    private static final ItemStack THEME_ICON = new ItemStack(Items.PAINTING);
    private static final ItemStack SETTINGS_ICON = new ItemStack(Items.COMPARATOR);
    private static final ItemStack PLAN_ICON = new ItemStack(Items.SCAFFOLDING);
    private static final ItemStack SHAPES_ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack EXPORT_ICON = new ItemStack(Items.PAPER);
    private static final ItemStack WEB_EXPORT_ICON = new ItemStack(Items.MAP);
    private static final ItemStack GOTO_ICON = new ItemStack(Items.COMPASS);
    private static final ItemStack MOBS_ICON = new ItemStack(Items.ZOMBIE_SPAWN_EGG);

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
                () -> runIfEnabled(SolarisAPI.FEATURE_PNG_EXPORT,
                        () -> texture().exportToPng(SolarisMapScreen::sendMapMessage))));

        int webExportX = exportX + BUTTON_GAP;
        iconButtons.add(new IconButton(webExportX, bottomY, "Export for Web Map",
                (g, hover) -> drawItemIcon(g, webExportX, bottomY, WEB_EXPORT_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_WEB_EXPORT,
                        () -> SolarisWebExporter.exportCurrentDimension(SolarisMapScreen::sendMapMessage))));

        int gotoX = webExportX + BUTTON_GAP;
        iconButtons.add(new IconButton(gotoX, bottomY, "Go to Coordinate",
                (g, hover) -> drawItemIcon(g, gotoX, bottomY, GOTO_ICON),
                () -> Minecraft.getInstance().setScreen(new QuickGotoScreen(this))));

        int waypointsX = width - BUTTON_MARGIN - BUTTON_R;
        iconButtons.add(new IconButton(waypointsX, bottomY, "Waypoints",
                (g, hover) -> drawItemIcon(g, waypointsX, bottomY, WAYPOINTS_ICON),
                () -> runIfStateAtLeast(SolarisAPI.FEATURE_WAYPOINTS, SolarisFeatureState.VISIBLE,
                        () -> Minecraft.getInstance().setScreen(new WaypointListScreen(this)))));

        int themeX = waypointsX - BUTTON_GAP;
        iconButtons.add(new IconButton(themeX, bottomY, "Theme",
                (g, hover) -> drawItemIcon(g, themeX, bottomY, THEME_ICON),
                () -> Minecraft.getInstance().setScreen(new SolarisThemeEditorScreen(this))));

        int lastIconX = themeX;
        if (SolarisConfig.GLOBE_VIEW_ENABLED.get()) {
            int globeX = lastIconX - BUTTON_GAP;
            iconButtons.add(new IconButton(globeX, bottomY, "Globe View",
                    (g, hover) -> drawGlobeIcon(g, globeX, bottomY),
                    () -> runIfEnabled(SolarisAPI.FEATURE_GLOBE_VIEW, () -> {

                        if (mode == ViewMode.FLAT) cancelDrawing();
                        mode = mode == ViewMode.FLAT ? ViewMode.GLOBE : ViewMode.FLAT;
                    })));
            lastIconX = globeX;
        }

        int hillshadingX = lastIconX - BUTTON_GAP;
        iconButtons.add(new IconButton(hillshadingX, bottomY, "Hillshading",
                (g, hover) -> drawHillshadingIcon(g, hillshadingX, bottomY, SolarisConfig.HILLSHADING.get()),
                () -> {
                    boolean on = !SolarisConfig.HILLSHADING.get();
                    SolarisConfig.HILLSHADING.set(on);
                    SolarisConfig.HILLSHADING.save();
                    SolarisTexture.invalidateAll();
                }));
        lastIconX = hillshadingX;

        int mobsX = lastIconX - BUTTON_GAP;
        iconButtons.add(new IconButton(mobsX, bottomY, "Show Mobs",
                (g, hover) -> drawItemIcon(g, mobsX, bottomY, MOBS_ICON),
                () -> {
                    boolean on = !SolarisConfig.SHOW_MOBS.get();
                    SolarisConfig.SHOW_MOBS.set(on);
                    SolarisConfig.SHOW_MOBS.save();
                }));
        lastIconX = mobsX;

        int chunkGridX = lastIconX - BUTTON_GAP;
        iconButtons.add(new IconButton(chunkGridX, bottomY, "Show Chunk Grid",
                (g, hover) -> drawChunkGridIcon(g, chunkGridX, bottomY, SolarisConfig.SHOW_CHUNK_GRID.get()),
                () -> {
                    boolean on = !SolarisConfig.SHOW_CHUNK_GRID.get();
                    SolarisConfig.SHOW_CHUNK_GRID.set(on);
                    SolarisConfig.SHOW_CHUNK_GRID.save();
                }));
        lastIconX = chunkGridX;

        int vignetteX = lastIconX - BUTTON_GAP;
        iconButtons.add(new IconButton(vignetteX, bottomY, "Vignette",
                (g, hover) -> drawVignetteIcon(g, vignetteX, bottomY, SolarisConfig.VIGNETTE.get()),
                () -> {
                    boolean on = !SolarisConfig.VIGNETTE.get();
                    SolarisConfig.VIGNETTE.set(on);
                    SolarisConfig.VIGNETTE.save();
                    SolarisTexture.invalidateAll();
                }));
        lastIconX = vignetteX;

        int blackAndWhiteX = lastIconX - BUTTON_GAP;
        iconButtons.add(new IconButton(blackAndWhiteX, bottomY, "Black & White",
                (g, hover) -> drawBlackAndWhiteIcon(g, blackAndWhiteX, bottomY, SolarisConfig.BLACK_AND_WHITE.get()),
                () -> {
                    boolean on = !SolarisConfig.BLACK_AND_WHITE.get();
                    SolarisConfig.BLACK_AND_WHITE.set(on);
                    SolarisConfig.BLACK_AND_WHITE.save();
                    SolarisTexture.invalidateAll();
                }));
        lastIconX = blackAndWhiteX;

        int shapesX = lastIconX - BUTTON_GAP;
        iconButtons.add(new IconButton(shapesX, bottomY, "Shapes",
                (g, hover) -> drawItemIcon(g, shapesX, bottomY, SHAPES_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_SHAPE_PLANNER,
                        () -> Minecraft.getInstance().setScreen(new PlanShapeListScreen(this)))));

        int planX = shapesX - BUTTON_GAP;
        iconButtons.add(new IconButton(planX, bottomY, "Plan",
                (g, hover) -> drawItemIcon(g, planX, bottomY, PLAN_ICON),
                () -> runIfEnabled(SolarisAPI.FEATURE_SHAPE_PLANNER, () -> {

                    mode = ViewMode.FLAT;
                    switchDrawTool(ToolMode.DRAW_RECTANGLE);
                })));
    }

    private static void runIfEnabled(String featureId, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation dimension = mc.level != null ? mc.level.dimension().location() : null;
        if (dimension != null ? SolarisAPI.isFeatureEnabled(featureId, dimension) :
                SolarisAPI.isFeatureEnabled(featureId)) {
            action.run();
        } else {
            sendMapMessage(Component.literal("This feature isn't available right now."));
        }
    }

    private static void runIfStateAtLeast(String featureId, SolarisFeatureState minimum, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (SolarisAPI.getFeatureState(featureId, mc.level.dimension().location()).atLeast(minimum)) {
            action.run();
        } else {
            sendMapMessage(Component.literal("This feature isn't available right now."));
        }
    }

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

    private static void drawMobIcon(GuiGraphics g, Mob mob, int x, int y, int r) {
        int color = mob instanceof Enemy ? MOB_ICON_COLOR_HOSTILE : MOB_ICON_COLOR_PASSIVE;
        if (MobFaceIcons.isSupported(mob.getType())) {
            PlayerArrow.drawMob(g, mob, x, y, r, color);
        } else {
            PlayerArrow.drawFlatSquare(g, x, y, r, color);
        }
    }

    private static void drawGlobeIcon(GuiGraphics g, int cx, int cy) {
        int r = BUTTON_R - 2;
        SmoothShapes.drawRing(g, cx, cy, r, C_TEXT);
        g.fill(cx - r, cy, cx + r, cy + 1, C_TEXT);
        g.fill(cx, cy - r, cx + 1, cy + r, C_TEXT);
    }

    private static void drawHillshadingIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = BUTTON_R - 2;
        g.fill(cx - r, cy - r, cx, cy + r, on ? 0xFFF0F0F0 : 0xFF808080);
        g.fill(cx, cy - r, cx + r, cy + r, on ? 0xFF202020 : 0xFF606060);
    }

    private static void drawChunkGridIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = BUTTON_R - 2;
        int color = on ? C_ACCENT : C_TEXT;
        SmoothShapes.drawRing(g, cx, cy, r, color);
        int inset = Math.max(1, r * 2 / 3);
        g.fill(cx - inset, cy, cx + inset, cy + 1, color);
        g.fill(cx, cy - inset, cx + 1, cy + inset, color);
    }

    private static void drawVignetteIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = BUTTON_R - 2;
        int ringColor = on ? 0xFF000000 : C_TEXT;
        int centerColor = on ? C_ACCENT : C_TEXT;
        SmoothShapes.drawRing(g, cx, cy, r, ringColor);
        SmoothShapes.drawCircle(g, cx, cy, Math.max(1, r / 2), centerColor);
    }

    private static void drawBlackAndWhiteIcon(GuiGraphics g, int cx, int cy, boolean on) {
        int r = BUTTON_R - 2;
        g.fill(cx - r, cy - r, cx, cy + r, 0xFFFFFFFF);
        g.fill(cx, cy - r, cx + r, cy + r, 0xFF000000);
    }

    private void clampViewport() {
        recenterIfNeeded();

        if (dragging) return;
        viewport.clampOffsetToCover(texture().getSizePixels(), MARGIN, width - 2 * MARGIN, MARGIN, height - 2 * MARGIN);
    }

    private void recenterIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mode != ViewMode.FLAT) return;

        double frameCenterX = MARGIN + (width - 2 * MARGIN) / 2.0;
        double frameCenterY = MARGIN + (height - 2 * MARGIN) / 2.0;
        double texLocalX = viewport.toWorldX(frameCenterX, 0);
        double texLocalZ = viewport.toWorldZ(frameCenterY, 0);
        int viewWorldX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int viewWorldZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));
        int viewChunkX = viewWorldX >> 4;
        int viewChunkZ = viewWorldZ >> 4;

        if (viewChunkX == anchorChunkX && viewChunkZ == anchorChunkZ) return;

        if (!dragRebuildGateOpen()) return;

        int deltaChunkX = viewChunkX - anchorChunkX;
        int deltaChunkZ = viewChunkZ - anchorChunkZ;
        viewport.pan(deltaChunkX * 16.0 * viewport.getZoom(), deltaChunkZ * 16.0 * viewport.getZoom());

        anchorChunkX = viewChunkX;
        anchorChunkZ = viewChunkZ;
        centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));
        texture().maybeRebuild(centerKey);
    }

    private void recenterGlobeIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mode != ViewMode.GLOBE) return;

        GlobeCamera.SpherePoint sp = globeCamera.screenToSpherePoint(width / 2.0, height / 2.0, width / 2, height / 2);
        if (sp == null) return;

        int size = texture().getSizePixels();
        int viewWorldX = (int) Math.floor(sp.u * size - radiusPixels + (anchorChunkX << 4));
        int viewWorldZ = (int) Math.floor(sp.v * size - radiusPixels + (anchorChunkZ << 4));
        int viewChunkX = viewWorldX >> 4;
        int viewChunkZ = viewWorldZ >> 4;

        if (viewChunkX == anchorChunkX && viewChunkZ == anchorChunkZ) return;
        if (!dragRebuildGateOpen()) return;

        anchorChunkX = viewChunkX;
        anchorChunkZ = viewChunkZ;
        centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));
        texture().maybeRebuild(centerKey);
    }

    public void goToCoordinate(int worldX, int worldZ) {
        if (mode == ViewMode.GLOBE) {
            cancelDrawing();
            mode = ViewMode.FLAT;
        }

        double pixelX = shapePixelX(worldX);
        double pixelZ = shapePixelZ(worldZ);
        double frameCenterX = MARGIN + (width - 2 * MARGIN) / 2.0;
        double frameCenterY = MARGIN + (height - 2 * MARGIN) / 2.0;
        viewport.setOffset(frameCenterX - pixelX * viewport.getZoom(), frameCenterY - pixelZ * viewport.getZoom());
        clampViewport();
    }

    private double waypointPixelX(Waypoint w) {
        return radiusPixels + (w.x - (anchorChunkX << 4));
    }

    private double waypointPixelZ(Waypoint w) {
        return radiusPixels + (w.z - (anchorChunkZ << 4));
    }

    private double selfPlayerPixelX(AbstractClientPlayer player) {
        return radiusPixels + (player.getX() - (anchorChunkX << 4));
    }

    private double selfPlayerPixelZ(AbstractClientPlayer player) {
        return radiusPixels + (player.getZ() - (anchorChunkZ << 4));
    }

    private int wrappedOriginX(SolarisTexture tex) {
        ChunkKey lastCenter = tex.getLastCenter();
        int centerX = lastCenter != null ? lastCenter.x() : anchorChunkX;
        return TextureAddressing.properMod(centerX - tex.getRadiusChunks(), tex.getRadiusChunks() * 2 + 1) * 16;
    }

    private int wrappedOriginZ(SolarisTexture tex) {
        ChunkKey lastCenter = tex.getLastCenter();
        int centerZ = lastCenter != null ? lastCenter.z() : anchorChunkZ;
        return TextureAddressing.properMod(centerZ - tex.getRadiusChunks(), tex.getRadiusChunks() * 2 + 1) * 16;
    }

    private static final long DRAG_REBUILD_THROTTLE_MS = 200L;

    private boolean dragRebuildGateOpen() {
        if (!dragging) return true;
        long now = System.currentTimeMillis();
        if (now - lastDragRebuildMillis < DRAG_REBUILD_THROTTLE_MS) return false;
        lastDragRebuildMillis = now;
        return true;
    }

    private void maybeRebuildThrottled(SolarisTexture tex) {
        if (centerKey == null) return;
        if (!dragRebuildGateOpen()) return;
        tex.maybeRebuild(centerKey);
    }

    public void renderMapBackground(GuiGraphics g) {
        renderBackground(g);
        SolarisTexture tex = texture();
        maybeRebuildThrottled(tex);
        int size = tex.getSizePixels();

        ModernPanel.draw(g, MARGIN - 4, MARGIN - 4, width - 2 * (MARGIN - 4), height - 2 * (MARGIN - 4), C_BORDER);
        g.enableScissor(MARGIN, MARGIN, width - MARGIN, height - MARGIN);
        g.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, C_BG);
        int destX = (int) viewport.toScreenX(0, 0);
        int destY = (int) viewport.toScreenY(0, 0);
        int destSize = (int) (size * viewport.getZoom());
        g.blit(tex.textureId(), destX, destY, destSize, destSize, wrappedOriginX(tex), wrappedOriginZ(tex), size, size,
                size, size);
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

        if (mode == ViewMode.FLAT) drawScaleBar(g);

        super.render(g, mx, my, partialTick);

        g.drawCenteredString(font, title, width / 2, 6, C_ACCENT);
        g.drawCenteredString(font, footerHint(), width / 2, height - MARGIN + 10, C_TEXT);

        String hoveredButton = renderIconButtons(g, mx, my);

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

                    tooltip = hoveredBlockName(mx, my);
                } else {
                    tooltip = null;
                }
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mx, my);
        }
    }

    private void drawScaleBar(GuiGraphics g) {
        float zoom = viewport.getZoom();
        if (zoom <= 0) return;

        int targetPx = 60;
        double blocks = niceScaleNumber(targetPx / zoom);
        int barPx = Math.max(1, (int) Math.round(blocks * zoom));

        int x = MARGIN + 10;
        int y = MARGIN + 12;
        g.fill(x, y, x + barPx, y + 2, C_TEXT);
        g.fill(x, y - 2, x + 1, y + 5, C_TEXT);
        g.fill(x + barPx - 1, y - 2, x + barPx, y + 5, C_TEXT);

        String label = blocks >= 1000 ? Math.round(blocks / 1000) + "k blocks" : Math.round(blocks) + " blocks";
        g.drawString(font, label, x, y + 7, C_TEXT, true);
    }

    private void drawChunkGrid(GuiGraphics g, int destX, int destY, int size) {
        int frameLeft = MARGIN;
        int frameRight = width - MARGIN;
        int frameTop = MARGIN;
        int frameBottom = height - MARGIN;
        int gridColor = 0x30FFFFFF;
        float zoom = viewport.getZoom();

        for (int i = 0; i <= size; i += 16) {
            int sx = destX + Math.round(i * zoom);
            if (sx < frameLeft || sx > frameRight) continue;
            g.fill(sx, Math.max(destY, frameTop), sx + 1, Math.min(destY + Math.round(size * zoom), frameBottom),
                    gridColor);
        }
        for (int i = 0; i <= size; i += 16) {
            int sy = destY + Math.round(i * zoom);
            if (sy < frameTop || sy > frameBottom) continue;
            g.fill(Math.max(destX, frameLeft), sy, Math.min(destX + Math.round(size * zoom), frameRight), sy + 1,
                    gridColor);
        }
    }

    private void drawClippedFlatTerrain(GuiGraphics g, SolarisTexture tex, MinimapShape shape, int destX, int destY,
                                        int destSize, int originU, int originV, int texSize) {
        int panelX = MARGIN;
        int panelY = MARGIN;
        int panelW = width - 2 * MARGIN;
        int panelH = height - 2 * MARGIN;
        float zoomFactor = destSize / (float) texSize;
        int stripH = Math.max(1, panelH / 128);

        for (int i = 0; i < panelH; i += stripH) {
            int rowStripH = Math.min(stripH, panelH - i);
            int rowCenter = i + rowStripH / 2;
            float[] span = shape.rowSpan(rowCenter / (float) panelH);
            if (span == null) continue;

            int stripX0 = panelX + Math.round(span[0] * panelW);
            int stripX1 = panelX + Math.round(span[1] * panelW);
            int stripY0 = panelY + i;
            int stripY1 = stripY0 + rowStripH;

            int clipX0 = Math.max(stripX0, destX);
            int clipX1 = Math.min(stripX1, destX + destSize);
            int clipY0 = Math.max(stripY0, destY);
            int clipY1 = Math.min(stripY1, destY + destSize);
            if (clipX1 <= clipX0 || clipY1 <= clipY0) continue;

            float srcX = originU + (clipX0 - destX) / zoomFactor;
            float srcY = originV + (clipY0 - destY) / zoomFactor;
            int srcW = Math.max(1, Math.round((clipX1 - clipX0) / zoomFactor));
            int srcH = Math.max(1, Math.round((clipY1 - clipY0) / zoomFactor));

            g.blit(tex.textureId(), clipX0, clipY0, clipX1 - clipX0, clipY1 - clipY0, srcX, srcY, srcW, srcH, texSize,
                    texSize);
        }
    }

    private boolean insideMapShape(int screenX, int screenY) {
        MinimapShape shape = SolarisConfig.MAP_SHAPE.get();
        if (shape == MinimapShape.SQUARE) return true;
        float nx = (screenX - MARGIN) / (float) (width - 2 * MARGIN);
        float ny = (screenY - MARGIN) / (float) (height - 2 * MARGIN);
        return shape.containsPoint(nx, ny);
    }

    private static double niceScaleNumber(double raw) {
        if (raw <= 0) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double fraction = raw / magnitude;
        double niceFraction;
        if (fraction < 1.5) niceFraction = 1;
        else if (fraction < 3.5) niceFraction = 2;
        else if (fraction < 7.5) niceFraction = 5;
        else niceFraction = 10;
        return niceFraction * magnitude;
    }

    private String footerHint() {
        return switch (toolMode) {
            case NAVIGATE -> "Right-click: new waypoint (or delete one)";
            case DRAW_RECTANGLE -> "Rectangle — drag to draw · R/C/L: switch tool · Esc: cancel";
            case DRAW_CIRCLE -> "Circle — drag to draw · R/C/L: switch tool · Esc: cancel";
            case DRAW_LINE -> "Line — click to add points · Enter/right-click: finish · Esc: cancel";
        };
    }

    private void renderFlatMap(GuiGraphics g, int mx, int my) {
        SolarisTexture tex = texture();

        maybeRebuildThrottled(tex);
        int size = tex.getSizePixels();

        int destX = (int) viewport.toScreenX(0, 0);
        int destY = (int) viewport.toScreenY(0, 0);
        int destSize = (int) (size * viewport.getZoom());
        MinimapShape mapShape = SolarisConfig.MAP_SHAPE.get();
        if (mapShape == MinimapShape.SQUARE) {
            g.blit(tex.textureId(), destX, destY, destSize, destSize, wrappedOriginX(tex), wrappedOriginZ(tex), size,
                    size, size, size);
        } else {
            drawClippedFlatTerrain(g, tex, mapShape, destX, destY, destSize, wrappedOriginX(tex), wrappedOriginZ(tex),
                    size);
        }

        if (SolarisConfig.SHOW_CHUNK_GRID.get()) {
            drawChunkGrid(g, destX, destY, size);
        }

        int texScreenMinX = Math.max(MARGIN, destX);
        int texScreenMaxX = Math.min(width - MARGIN, destX + destSize);
        int texScreenMinY = Math.max(MARGIN, destY);
        int texScreenMaxY = Math.min(height - MARGIN, destY + destSize);

        Minecraft mc = Minecraft.getInstance();
        if (SolarisConfig.SHOW_RAIL_NETWORK.get() && mc.level != null) {
            ResourceLocation dimension = mc.level.dimension().location();
            SolarisProfiler.time("railNetworkRender", () -> drawRailNetwork(g, dimension));
        }

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(Mth.clamp(3f * viewport.getZoom(), 2f, 10f) * iconScale);
        int playerR = Math.max(4, Math.min(12, Math.round(3.5f * viewport.getZoom())));

        int mobR = Math.max(2, Math.round(iconR * 0.65f));

        LabelSide labelSide = SolarisConfig.LABEL_SIDE.get();

        if (mc.level != null) {
            List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
            for (Waypoint w : waypoints) {
                int wx = (int) viewport.toScreenX(waypointPixelX(w), 0);
                int wy = (int) viewport.toScreenY(waypointPixelZ(w), 0);
                if (wx < texScreenMinX || wx > texScreenMaxX || wy < texScreenMinY || wy > texScreenMaxY ||
                        !insideMapShape(wx, wy)) {
                    continue;
                }
                WaypointIconManager.draw(g, w.icon, wx, wy, iconR, w.colorArgb());
                int lx = labelSide.drawX(wx, iconR, font.width(w.name));
                int ly = labelSide.drawY(wy, iconR, font.lineHeight);
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
                    int vx = (int) viewport.toScreenX(radiusPixels + (vein.center().getX() - (anchorChunkX << 4)), 0);
                    int vy = (int) viewport.toScreenY(radiusPixels + (vein.center().getZ() - (anchorChunkZ << 4)), 0);
                    if (vx < texScreenMinX || vx > texScreenMaxX || vy < texScreenMinY || vy > texScreenMaxY ||
                            !insideMapShape(vx, vy)) {
                        continue;
                    }
                    if (vein.icon().isEmpty()) {

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

        hoveredPlayerName = null;
        if (mc.level != null) {
            for (AbstractClientPlayer other : mc.level.players()) {
                if (other == mc.player) continue;
                double opx = radiusPixels + (other.getX() - (anchorChunkX << 4));
                double opz = radiusPixels + (other.getZ() - (anchorChunkZ << 4));
                int ox = (int) viewport.toScreenX(opx, 0);
                int oy = (int) viewport.toScreenY(opz, 0);
                if (ox < texScreenMinX || ox > texScreenMaxX || oy < texScreenMinY || oy > texScreenMaxY ||
                        !insideMapShape(ox, oy)) {
                    continue;
                }
                PlayerArrow.draw(g, ox, oy, playerR, other.getYRot(), 0xFFAAAAAA, other.getSkinTextureLocation());
                if (mx >= ox - playerR && mx <= ox + playerR && my >= oy - playerR && my <= oy + playerR) {
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
                int mobX = (int) viewport.toScreenX(mpx, 0);
                int mobY = (int) viewport.toScreenY(mpz, 0);
                if (mobX < texScreenMinX || mobX > texScreenMaxX || mobY < texScreenMinY || mobY > texScreenMaxY ||
                        !insideMapShape(mobX, mobY)) {
                    continue;
                }
                drawMobIcon(g, mob, mobX, mobY, mobR);
                if (mx >= mobX - mobR && mx <= mobX + mobR && my >= mobY - mobR && my <= mobY + mobR) {
                    hoveredMobName = mob.getName().getString();
                }
            }
        }

        if (hasPlayerMarker && mc.player != null) {
            int cx = (int) viewport.toScreenX(selfPlayerPixelX(mc.player), 0);
            int cy = (int) viewport.toScreenY(selfPlayerPixelZ(mc.player), 0);
            if (cx >= texScreenMinX && cx <= texScreenMaxX && cy >= texScreenMinY && cy <= texScreenMaxY &&
                    insideMapShape(cx, cy)) {
                PlayerArrow.draw(g, cx, cy, playerR, mc.player.getYRot(), C_ACCENT, mc.player.getSkinTextureLocation());
            }
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

    private static final int RAIL_LINE_COLOR = 0xFFB6B6B6;

    private void drawRailNetwork(GuiGraphics g, ResourceLocation dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int range = SolarisConfig.RAIL_NETWORK_RANGE.get();
        int centerChunkX = mc.player.getBlockX() >> 4;
        int centerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (range >> 4) + 1;
        int thickness = Math.max(1, Math.round(1.5f * viewport.getZoom()));

        for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
            for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
                boolean[] rails = ChunkRailCache.get(new ChunkKey(dimension, cx, cz));
                if (rails == null) continue;
                boolean[] eastRails = ChunkRailCache.get(new ChunkKey(dimension, cx + 1, cz));
                boolean[] southRails = ChunkRailCache.get(new ChunkKey(dimension, cx, cz + 1));

                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!rails[lz * 16 + lx]) continue;
                        int wx = (cx << 4) + lx;
                        int wz = (cz << 4) + lz;

                        boolean eastRail = lx < 15 ? rails[lz * 16 + lx + 1] : eastRails != null && eastRails[lz * 16];
                        if (eastRail) drawRailSegment(g, wx, wz, wx + 1, wz, thickness);

                        boolean southRail = lz < 15 ? rails[(lz + 1) * 16 + lx] : southRails != null && southRails[lx];
                        if (southRail) drawRailSegment(g, wx, wz, wx, wz + 1, thickness);
                    }
                }
            }
        }
    }

    private void drawRailSegment(GuiGraphics g, int wx1, int wz1, int wx2, int wz2, int thickness) {
        int x1 = (int) viewport.toScreenX(shapePixelX(wx1), 0);
        int y1 = (int) viewport.toScreenY(shapePixelZ(wz1), 0);
        int x2 = (int) viewport.toScreenX(shapePixelX(wx2), 0);
        int y2 = (int) viewport.toScreenY(shapePixelZ(wz2), 0);
        if ((x1 < MARGIN && x2 < MARGIN) || (x1 > width - MARGIN && x2 > width - MARGIN)) return;
        if ((y1 < MARGIN && y2 < MARGIN) || (y1 > height - MARGIN && y2 > height - MARGIN)) return;
        LineRenderer.drawLine(g, x1, y1, x2, y2, thickness, RAIL_LINE_COLOR);
    }

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

    private void renderGlobeMap(GuiGraphics g, int mx, int my) {
        recenterGlobeIfNeeded();
        SolarisTexture tex = texture();
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        int cx = width / 2;
        int cy = height / 2;
        float screenRadius = globeCamera.getScale();
        Minecraft mc = Minecraft.getInstance();
        int seaLevel = mc.level != null ? mc.level.getSeaLevel() : 63;

        ResourceLocation globeTextureId = tex.ensureGlobeTexture();
        globeMesh.rebuild(tex.getGlobeHeights(), size, seaLevel);
        SolarisGlobeRenderer.renderSphere(g, cx, cy, screenRadius, globeCamera.rotationQuaternion(),
                globeTextureId, globeMesh);

        double iconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        int iconR = (int) Math.round(6f * iconScale);
        int playerR = 7;
        int mobR = Math.max(2, Math.round(iconR * 0.65f));

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
                drawMobIcon(g, mob, p.screenX, p.screenY, mobR);
                if (mx >= p.screenX - mobR && mx <= p.screenX + mobR && my >= p.screenY - mobR &&
                        my <= p.screenY + mobR) {
                    hoveredMobName = mob.getName().getString();
                }
            }
        }

        if (hasPlayerMarker && mc.player != null) {
            GlobeCamera.Projection p = globeCamera.sphereToScreen(
                    (float) (selfPlayerPixelX(mc.player) / size), (float) (selfPlayerPixelZ(mc.player) / size), cx,
                    cy);
            if (p.frontFacing) {
                PlayerArrow.draw(g, p.screenX, p.screenY, playerR, mc.player.getYRot(), C_ACCENT,
                        mc.player.getSkinTextureLocation());
            }
        }
    }

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
        Component name = mc.level.getBlockState(pos).getBlock().getName();

        ResourceLocation dimension = mc.level.dimension().location();
        if (!SolarisAPI.isFeatureEnabled(SolarisAPI.FEATURE_SHOW_COORDINATES, dimension)) return name;
        return name.copy().append(Component.literal(" (" + blockX + ", " + blockY + ", " + blockZ + ")")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
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

    private void finishRectOrCircle() {
        if (drawAnchor == null || drawCurrent == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            drawAnchor = null;
            drawCurrent = null;
            return;
        }

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

        int blockY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;

        Minecraft.getInstance().setScreen(
                new QuickWaypointScreen(this, mc.level.dimension().location(), blockX, blockY, blockZ));
    }

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
            boolean wasDragging = dragging;
            dragging = false;
            if (shapeDragging) {
                shapeDragging = false;
                finishRectOrCircle();
            }

            if (wasDragging && mode == ViewMode.FLAT) clampViewport();
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
                double panSpeed = Math.max(1.0, Math.sqrt(viewport.getZoom()));
                viewport.pan(dx * panSpeed, dy * panSpeed);

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
