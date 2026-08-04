package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.CaveTileCache;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BG;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER2;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;

@OnlyIn(Dist.CLIENT)
public class SolarisDisplaySettingsScreen extends Screen {

    private static final int BOX_W = 420;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 24;

    private static final int DISPLAY_GRID_ROWS = 11;

    private static final int HEADING_H = 12;
    private static final int GROUP_COUNT = 4;
    private static final String[] GROUP_HEADINGS = { "TERRAIN & WATER", "ICONS & LABELS", "EFFECTS", "MINIMAP & GRID" };

    private enum Tab {

        DISPLAY("Display"),
        WAYPOINTS("Waypoints"),
        INTEGRATIONS("Integrations");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private Tab activeTab = Tab.DISPLAY;
    private int boxH;

    private final int[] headingY = new int[GROUP_COUNT];

    public SolarisDisplaySettingsScreen(Screen parent) {
        super(Component.literal("Solaris Settings"));
        this.parent = parent;
    }

    private int boxX() {
        return (width - BOX_W) / 2;
    }

    private int boxY() {
        return (height - boxH) / 2;
    }

    @Override
    protected void init() {
        clearWidgets();

        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        boolean showIntegrationsTab = GtceuIntegration.isAvailable();

        boxH = HEADER_H + DISPLAY_GRID_ROWS * ROW_H + GROUP_COUNT * HEADING_H + 22 + 22 + 22 + 8;

        int x = boxX();
        int y = boxY();
        int contentY = y + HEADER_H;

        switch (activeTab) {
            case DISPLAY -> initDisplayTab(x, contentY);
            case WAYPOINTS -> initWaypointsTab(x, contentY);
            case INTEGRATIONS -> initIntegrationsTab(x, contentY);
        }

        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.DISPLAY);
        tabs.add(Tab.WAYPOINTS);
        if (showIntegrationsTab) tabs.add(Tab.INTEGRATIONS);
        else if (activeTab == Tab.INTEGRATIONS) activeTab = Tab.DISPLAY;

        int tabBarY = contentY + DISPLAY_GRID_ROWS * ROW_H + GROUP_COUNT * HEADING_H + 6;
        int tabW = (BOX_W - 20) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);

            String label = (tab == activeTab ? "» " : "") + tab.label;
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                activeTab = tab;
                init();
            }).bounds(x + 10 + i * tabW, tabBarY, tabW, 18).build());
        }

        int presetHalfW = (BOX_W - 20 - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Save Preset"),
                b -> Minecraft.getInstance().setScreen(new SolarisPresetSaveScreen(this, () -> {})))
                .bounds(x + 10, tabBarY + 24, presetHalfW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Load Preset"),

                b -> Minecraft.getInstance()
                        .setScreen(new SolarisPresetLoadScreen(this, SolarisTexture::invalidateAll)))
                .bounds(x + 10 + presetHalfW + 6, tabBarY + 24, presetHalfW, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + 10, tabBarY + 48, BOX_W - 20, 18).build());
    }

    private void initDisplayTab(int x, int y) {
        int colW = (BOX_W - 20 - 20) / 3;
        int col1X = x + 10;
        int col2X = col1X + colW + 10;
        int col3X = col2X + colW + 10;

        headingY[0] = y;
        y += HEADING_H;

        addRenderableWidget(new SaturationSlider(col1X, y, colW, 20));
        addRenderableWidget(new WaterOpacitySlider(col2X, y, colW, 20));
        addRenderableWidget(new BiomeBlendSlider(col3X, y, colW, 20));
        y += ROW_H;

        addRenderableWidget(Button.builder(deepOnlyLabel(), b -> {
            SolarisConfig.WATER_DEEP_ONLY.set(!SolarisConfig.WATER_DEEP_ONLY.get());
            SolarisConfig.WATER_DEEP_ONLY.save();
            b.setMessage(deepOnlyLabel());
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }).bounds(col1X, y, colW, 18).build());
        addRenderableWidget(new ContrastSlider(col2X, y, colW, 20));
        addRenderableWidget(new BrightnessSlider(col3X, y, colW, 20));
        y += ROW_H;

        addRenderableWidget(new FoliageBrightnessSlider(col1X, y, colW, 20));
        y += ROW_H;

        headingY[1] = y;
        y += HEADING_H;

        addRenderableWidget(new IconScaleSlider(col1X, y, colW, 20));
        addRenderableWidget(Button.builder(labelSideLabel(), b -> {
            SolarisConfig.LABEL_SIDE.set(SolarisConfig.LABEL_SIDE.get().next());
            SolarisConfig.LABEL_SIDE.save();
            b.setMessage(labelSideLabel());
        }).bounds(col2X, y, colW, 18).build());
        addRenderableWidget(Button.builder(tooltipLabel(), b -> {
            SolarisConfig.SHOW_BLOCK_TOOLTIP.set(!SolarisConfig.SHOW_BLOCK_TOOLTIP.get());
            SolarisConfig.SHOW_BLOCK_TOOLTIP.save();
            b.setMessage(tooltipLabel());
        }).bounds(col3X, y, colW, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(railNetworkLabel(), b -> {
            SolarisConfig.SHOW_RAIL_NETWORK.set(!SolarisConfig.SHOW_RAIL_NETWORK.get());
            SolarisConfig.SHOW_RAIL_NETWORK.save();
            b.setMessage(railNetworkLabel());
            SolarisTexture.invalidateAll();
        }).bounds(col1X, y, colW, 18).build());
        y += ROW_H;

        headingY[2] = y;
        y += HEADING_H;

        addRenderableWidget(new HillshadingStrengthSlider(col1X, y, colW, 20));
        addRenderableWidget(new VignetteStrengthSlider(col2X, y, colW, 20));
        y += ROW_H;

        addRenderableWidget(new UnexploredDensitySlider(col1X, y, colW, 20));
        addRenderableWidget(new UnexploredBrightnessSlider(col2X, y, colW, 20));
        y += ROW_H;

        headingY[3] = y;
        y += HEADING_H;

        addRenderableWidget(new MinimapZoomSlider(col1X, y, colW, 20));
        addRenderableWidget(Button.builder(mapShapeLabel(), b -> {
            SolarisConfig.MAP_SHAPE.set(SolarisConfig.MAP_SHAPE.get().next());
            SolarisConfig.MAP_SHAPE.save();
            b.setMessage(mapShapeLabel());
        }).bounds(col2X, y, colW, 18).build());
        addRenderableWidget(Button.builder(minimapTimeLabel(), b -> {
            SolarisConfig.MINIMAP_SHOW_TIME.set(!SolarisConfig.MINIMAP_SHOW_TIME.get());
            SolarisConfig.MINIMAP_SHOW_TIME.save();
            b.setMessage(minimapTimeLabel());
        }).bounds(col3X, y, colW, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(minimapCoordsLabel(), b -> {
            SolarisConfig.MINIMAP_SHOW_COORDS.set(!SolarisConfig.MINIMAP_SHOW_COORDS.get());
            SolarisConfig.MINIMAP_SHOW_COORDS.save();
            b.setMessage(minimapCoordsLabel());
        }).bounds(col1X, y, colW, 18).build());
        addRenderableWidget(Button.builder(minimapRotateLabel(), b -> {
            SolarisConfig.MINIMAP_ROTATE.set(!SolarisConfig.MINIMAP_ROTATE.get());
            SolarisConfig.MINIMAP_ROTATE.save();
            b.setMessage(minimapRotateLabel());
        }).bounds(col2X, y, colW, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(claimsMapLabel(), b -> {
            SolarisConfig.SHOW_CLAIMS_MAP.set(!SolarisConfig.SHOW_CLAIMS_MAP.get());
            SolarisConfig.SHOW_CLAIMS_MAP.save();
            b.setMessage(claimsMapLabel());
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
            SolarisTexture.invalidateAll();
        }).bounds(col1X, y, colW, 18).build());
        addRenderableWidget(Button.builder(claimsMinimapLabel(), b -> {
            SolarisConfig.SHOW_CLAIMS_MINIMAP.set(!SolarisConfig.SHOW_CLAIMS_MINIMAP.get());
            SolarisConfig.SHOW_CLAIMS_MINIMAP.save();
            b.setMessage(claimsMinimapLabel());
            SolarisTexture.invalidateAll();
        }).bounds(col2X, y, colW, 18).build());
        y += ROW_H;

        addRenderableWidget(new MapZoomMinSlider(col1X, y, colW, 20));
        addRenderableWidget(new MapZoomMaxSlider(col2X, y, colW, 20));
    }

    private void initWaypointsTab(int x, int y) {
        addRenderableWidget(Button.builder(beamsLabel(), b -> {
            SolarisConfig.WAYPOINT_BEAMS.set(!SolarisConfig.WAYPOINT_BEAMS.get());
            SolarisConfig.WAYPOINT_BEAMS.save();
            b.setMessage(beamsLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(compassLabel(), b -> {
            SolarisConfig.WAYPOINT_COMPASS.set(!SolarisConfig.WAYPOINT_COMPASS.get());
            SolarisConfig.WAYPOINT_COMPASS.save();
            b.setMessage(compassLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(deathMarkersLabel(), b -> {
            SolarisConfig.DEATH_MARKERS.set(!SolarisConfig.DEATH_MARKERS.get());
            SolarisConfig.DEATH_MARKERS.save();
            b.setMessage(deathMarkersLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(planShapesLabel(), b -> {
            SolarisConfig.SHOW_PLAN_SHAPES.set(!SolarisConfig.SHOW_PLAN_SHAPES.get());
            SolarisConfig.SHOW_PLAN_SHAPES.save();
            b.setMessage(planShapesLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
    }

    private void initIntegrationsTab(int x, int y) {
        addRenderableWidget(Button.builder(gtVeinsLabel(), b -> {
            SolarisConfig.SHOW_GT_ORE_VEINS.set(!SolarisConfig.SHOW_GT_ORE_VEINS.get());
            SolarisConfig.SHOW_GT_ORE_VEINS.save();
            b.setMessage(gtVeinsLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
    }

    private Component tooltipLabel() {
        boolean on = SolarisConfig.SHOW_BLOCK_TOOLTIP.get();
        return Component.literal("Tooltip: " + (on ? "ON" : "OFF"));
    }

    private Component planShapesLabel() {
        boolean on = SolarisConfig.SHOW_PLAN_SHAPES.get();
        return Component.literal("Plan Shapes: " + (on ? "ON" : "OFF"));
    }

    private Component gtVeinsLabel() {
        boolean on = SolarisConfig.SHOW_GT_ORE_VEINS.get();
        return Component.literal("GT Ore Veins: " + (on ? "ON" : "OFF"));
    }

    private Component deepOnlyLabel() {
        boolean on = SolarisConfig.WATER_DEEP_ONLY.get();
        return Component.literal(
                "Deep Water Only: " + (on ? "ON (below Y" + SolarisConfig.WATER_DEEP_Y_THRESHOLD.get() + ")" : "OFF"));
    }

    private Component labelSideLabel() {
        return Component.literal("Label Position: " + SolarisConfig.LABEL_SIDE.get().label());
    }

    private Component mapShapeLabel() {
        return Component.literal("Map Shape: " + SolarisConfig.MAP_SHAPE.get().label());
    }

    private Component minimapRotateLabel() {
        boolean on = SolarisConfig.MINIMAP_ROTATE.get();
        return Component.literal("Minimap Rotate: " + (on ? "ON (facing up)" : "OFF (north up)"));
    }

    private Component railNetworkLabel() {
        boolean on = SolarisConfig.SHOW_RAIL_NETWORK.get();
        return Component.literal("Rail Lines: " + (on ? "ON" : "OFF"));
    }

    private Component minimapTimeLabel() {
        boolean on = SolarisConfig.MINIMAP_SHOW_TIME.get();
        return Component.literal("Minimap Time: " + (on ? "ON" : "OFF"));
    }

    private Component minimapCoordsLabel() {
        boolean on = SolarisConfig.MINIMAP_SHOW_COORDS.get();
        return Component.literal("Minimap Coords: " + (on ? "ON" : "OFF"));
    }

    private Component claimsMapLabel() {
        boolean on = SolarisConfig.SHOW_CLAIMS_MAP.get();
        return Component.literal("Claims (Map): " + (on ? "ON" : "OFF"));
    }

    private Component claimsMinimapLabel() {
        boolean on = SolarisConfig.SHOW_CLAIMS_MINIMAP.get();
        return Component.literal("Claims (Minimap): " + (on ? "ON" : "OFF"));
    }

    private Component beamsLabel() {
        boolean on = SolarisConfig.WAYPOINT_BEAMS.get();
        return Component.literal("Waypoint Beams: " + (on ? "ON" : "OFF"));
    }

    private Component compassLabel() {
        boolean on = SolarisConfig.WAYPOINT_COMPASS.get();
        return Component.literal("Waypoint Compass: " + (on ? "ON" : "OFF"));
    }

    private Component deathMarkersLabel() {
        boolean on = SolarisConfig.DEATH_MARKERS.get();
        return Component.literal("Death Markers: " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (parent instanceof SolarisMapScreen mapScreen) {
            mapScreen.renderMapBackground(g);
            g.fill(0, 0, width, height, 0xE0101014);
        } else {
            renderBackground(g);
        }
        int x = boxX();
        int y = boxY();
        g.fill(x, y, x + BOX_W, y + boxH, C_BG);
        ModernPanel.draw(g, x - 8, y - 8, BOX_W + 16, boxH + 16, C_BORDER);

        g.fill(x, y, x + BOX_W, y + HEADER_H, C_HEADER);
        g.fill(x, y + HEADER_H, x + BOX_W, y + HEADER_H + 1, C_BORDER2);
        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);

        if (activeTab == Tab.DISPLAY) {
            for (int i = 0; i < GROUP_COUNT; i++) {
                int hy = headingY[i];
                g.drawString(font, GROUP_HEADINGS[i], x + 10, hy + 2, C_DIM, false);
                g.fill(x + 10 + font.width(GROUP_HEADINGS[i]) + 6, hy + 6, x + BOX_W - 10, hy + 7, C_BORDER2);
            }
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        SolarisConfig.SATURATION.save();
        SolarisConfig.CONTRAST.save();
        SolarisConfig.BRIGHTNESS.save();
        SolarisConfig.FOLIAGE_BRIGHTNESS.save();
        SolarisConfig.WATER_OPACITY.save();
        SolarisConfig.WATER_BLEND_RADIUS.save();
        SolarisConfig.WATER_DEEP_ONLY.save();
        SolarisConfig.WAYPOINT_ICON_SCALE.save();
        SolarisConfig.SHOW_BLOCK_TOOLTIP.save();
        SolarisConfig.SHOW_MOBS.save();
        SolarisConfig.SHOW_GT_ORE_VEINS.save();
        SolarisConfig.LABEL_SIDE.save();
        SolarisConfig.MAP_SHAPE.save();
        SolarisConfig.WAYPOINT_BEAMS.save();
        SolarisConfig.WAYPOINT_COMPASS.save();
        SolarisConfig.DEATH_MARKERS.save();
        SolarisConfig.SHOW_PLAN_SHAPES.save();
        SolarisConfig.HILLSHADING.save();
        SolarisConfig.HILLSHADING_STRENGTH.save();
        SolarisConfig.VIGNETTE.save();
        SolarisConfig.VIGNETTE_STRENGTH.save();
        SolarisConfig.MINIMAP_ROTATE.save();
        SolarisConfig.SHOW_RAIL_NETWORK.save();
        SolarisConfig.MINIMAP_ZOOM.save();
        SolarisConfig.SHOW_CHUNK_GRID.save();
        SolarisConfig.MINIMAP_SHOW_TIME.save();
        SolarisConfig.MINIMAP_SHOW_COORDS.save();
        SolarisConfig.UNEXPLORED_STYLE.save();
        SolarisConfig.UNEXPLORED_DENSITY.save();
        SolarisConfig.UNEXPLORED_BRIGHTNESS.save();
        SolarisConfig.SHOW_CLAIMS_MAP.save();
        SolarisConfig.SHOW_CLAIMS_MINIMAP.save();
        SolarisConfig.ZOOM_MIN.save();
        SolarisConfig.ZOOM_MAX.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class SaturationSlider extends AbstractSliderButton {

        SaturationSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.SATURATION.get() / 2.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Saturation: " + Math.round(value * 200) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.SATURATION.set(value * 2.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class ContrastSlider extends AbstractSliderButton {

        ContrastSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.CONTRAST.get() / 3.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Contrast: " + Math.round(value * 300) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.CONTRAST.set(value * 3.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class BrightnessSlider extends AbstractSliderButton {

        BrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.BRIGHTNESS.get() / 2.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Brightness: " + Math.round(value * 200) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.BRIGHTNESS.set(value * 2.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class FoliageBrightnessSlider extends AbstractSliderButton {

        FoliageBrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.FOLIAGE_BRIGHTNESS.get() / 2.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Foliage Brightness: " + Math.round(value * 200) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.FOLIAGE_BRIGHTNESS.set(value * 2.0);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class UnexploredDensitySlider extends AbstractSliderButton {

        UnexploredDensitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.UNEXPLORED_DENSITY.get() - 0.25) / 3.75);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Density: " + Math.round((0.25 + value * 3.75) * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.UNEXPLORED_DENSITY.set(0.25 + value * 3.75);
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class UnexploredBrightnessSlider extends AbstractSliderButton {

        UnexploredBrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.UNEXPLORED_BRIGHTNESS.get() - 0.25) / 2.25);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Brightness: " + Math.round((0.25 + value * 2.25) * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.UNEXPLORED_BRIGHTNESS.set(0.25 + value * 2.25);
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class WaterOpacitySlider extends AbstractSliderButton {

        WaterOpacitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.WATER_OPACITY.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Water Opacity: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.WATER_OPACITY.set(value);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class BiomeBlendSlider extends AbstractSliderButton {

        private static final int STEP = 4;
        private static final int MAX_STEPS = 4;

        BiomeBlendSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.WATER_BLEND_RADIUS.get() / STEP) / (double) MAX_STEPS);
            updateMessage();
        }

        private int radius() {
            return (int) Math.round(value * MAX_STEPS) * STEP;
        }

        @Override
        protected void updateMessage() {
            int radius = radius();
            setMessage(Component.literal("Biome Blending: " + (radius == 0 ? "OFF (sharper)" : radius + " blocks")));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.WATER_BLEND_RADIUS.set(radius());
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class IconScaleSlider extends AbstractSliderButton {

        IconScaleSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.WAYPOINT_ICON_SCALE.get() - 0.5) / 2.5);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double scale = 0.5 + value * 2.5;
            setMessage(Component.literal("Waypoint Icon Size: " + Math.round(scale * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.WAYPOINT_ICON_SCALE.set(0.5 + value * 2.5);
        }
    }

    private static class MinimapZoomSlider extends AbstractSliderButton {

        MinimapZoomSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.MINIMAP_ZOOM.get() - 1.0) / 7.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double zoom = 1.0 + value * 7.0;
            setMessage(Component.literal("Minimap Zoom: " + String.format("%.1f", zoom) + "x"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.MINIMAP_ZOOM.set(1.0 + value * 7.0);
        }
    }

    private static class MapZoomMinSlider extends AbstractSliderButton {

        MapZoomMinSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.ZOOM_MIN.get() - 0.05) / 0.95);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double zoomMin = 0.05 + value * 0.95;
            setMessage(Component.literal("Map Zoom Out Limit: " + String.format("%.2f", zoomMin) + "x"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.ZOOM_MIN.set(0.05 + value * 0.95);
        }
    }

    private static class MapZoomMaxSlider extends AbstractSliderButton {

        MapZoomMaxSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (SolarisConfig.ZOOM_MAX.get() - 1.0) / 47.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double zoomMax = 1.0 + value * 47.0;
            setMessage(Component.literal("Map Zoom In Limit: " + String.format("%.0f", zoomMax) + "x"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.ZOOM_MAX.set(1.0 + value * 47.0);
        }
    }

    private static class HillshadingStrengthSlider extends AbstractSliderButton {

        HillshadingStrengthSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.HILLSHADING_STRENGTH.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Hillshading Strength: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.HILLSHADING_STRENGTH.set(value);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }

    private static class VignetteStrengthSlider extends AbstractSliderButton {

        VignetteStrengthSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), SolarisConfig.VIGNETTE_STRENGTH.get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Vignette Strength: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            SolarisConfig.VIGNETTE_STRENGTH.set(value);
            SolarisTexture.invalidateAll();
            MapTileCache.clearAll();
            CaveTileCache.clearAll();
        }
    }
}
