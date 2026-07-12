package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.config.SolarisConfig;
import net.phoenixvine.solaris.integration.gtceu.GtceuIntegration;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

/**
 * The map's settings popup — reachable from the fullscreen map's right-click menu, so
 * there's one obvious place for the tunables that don't fit the theme editor's color-field
 * model. Split into JourneyMap-style tabs (a row of buttons at the bottom of the panel, not
 * the top — this settings list had grown to 10 rows in one flat column before this) rather
 * than one long scrolling list: Display (terrain/water rendering, icons, tooltip), Waypoints
 * (beams, compass, death markers), and Integrations (GT ore veins — only shown when GTCEu is
 * actually installed). All backed by {@link SolarisConfig} ({@code ForgeConfigSpec} values
 * previously only reachable by hand-editing {@code config/phoenix_solaris-client.toml}).
 * Every water/saturation control applies immediately via {@link SolarisTexture#invalidateAll()}
 * so previewing a change doesn't require reopening the map. Dims/blurs the world behind it
 * despite not pausing — an earlier version skipped that to read as a floating widget, but it
 * left the panel with too little contrast against a bright/busy world to read comfortably.
 */
@OnlyIn(Dist.CLIENT)
public class SolarisDisplaySettingsScreen extends Screen {

    private static final int BOX_W = 260;
    private static final int ROW_H = 24;
    // Fixed regardless of which tab is active (the Display tab, the largest, needs this many) —
    // keeps the panel a constant size across tab switches instead of resizing/jumping around.
    private static final int MAX_ROWS = 9;

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
        // Keeps the backgrounded map screen's own width/height in sync — vanilla only calls
        // resize()/init() on the ACTIVE screen when the window resizes, so the parent would
        // otherwise silently go stale (e.g. after toggling fullscreen) until it becomes active
        // again. Same fix as QuickWaypointScreen's.
        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        boolean showIntegrationsTab = GtceuIntegration.isAvailable();
        // Top padding + fixed content area + tab bar row + preset row + close row + bottom padding.
        boxH = 24 + MAX_ROWS * ROW_H + 22 + 22 + 22 + 8;

        int x = boxX();
        int y = boxY();
        int contentY = y + 24;

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

        int tabBarY = y + 24 + MAX_ROWS * ROW_H + 6;
        int tabW = (BOX_W - 20) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            addRenderableWidget(Button.builder(Component.literal(tab.label), b -> {
                activeTab = tab;
                init();
            }).bounds(x + 10 + i * tabW, tabBarY, tabW, 18).build());
        }

        int presetHalfW = (BOX_W - 20 - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Save Preset"),
                b -> Minecraft.getInstance().setScreen(new SolarisPresetSaveScreen(this, () -> {})))
                .bounds(x + 10, tabBarY + 24, presetHalfW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Load Preset"),
                // Widgets refresh automatically — Minecraft#setScreen re-inits whatever screen
                // becomes active, including navigating back to this same one. Only the terrain
                // texture itself needs an explicit nudge to pick up the newly-loaded values.
                b -> Minecraft.getInstance()
                        .setScreen(new SolarisPresetLoadScreen(this, SolarisTexture::invalidateAll)))
                .bounds(x + 10 + presetHalfW + 6, tabBarY + 24, presetHalfW, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + 10, tabBarY + 48, BOX_W - 20, 18).build());
    }

    private void initDisplayTab(int x, int y) {
        addRenderableWidget(new SaturationSlider(x + 10, y, BOX_W - 20, 20));
        y += ROW_H;
        addRenderableWidget(new WaterOpacitySlider(x + 10, y, BOX_W - 20, 20));
        y += ROW_H;
        addRenderableWidget(new BiomeBlendSlider(x + 10, y, BOX_W - 20, 20));
        y += ROW_H;

        addRenderableWidget(Button.builder(deepOnlyLabel(), b -> {
            SolarisConfig.WATER_DEEP_ONLY.set(!SolarisConfig.WATER_DEEP_ONLY.get());
            b.setMessage(deepOnlyLabel());
            SolarisTexture.invalidateAll();
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(new IconScaleSlider(x + 10, y, BOX_W - 20, 20));
        y += ROW_H;

        int halfW = (BOX_W - 20 - 6) / 2;
        addRenderableWidget(Button.builder(tooltipLabel(), b -> {
            SolarisConfig.SHOW_BLOCK_TOOLTIP.set(!SolarisConfig.SHOW_BLOCK_TOOLTIP.get());
            b.setMessage(tooltipLabel());
        }).bounds(x + 10, y, halfW, 18).build());
        addRenderableWidget(Button.builder(mobsLabel(), b -> {
            SolarisConfig.SHOW_MOBS.set(!SolarisConfig.SHOW_MOBS.get());
            b.setMessage(mobsLabel());
        }).bounds(x + 10 + halfW + 6, y, halfW, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(labelSideLabel(), b -> {
            SolarisConfig.LABEL_SIDE.set(SolarisConfig.LABEL_SIDE.get().next());
            b.setMessage(labelSideLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(hillshadingLabel(), b -> {
            SolarisConfig.HILLSHADING.set(!SolarisConfig.HILLSHADING.get());
            b.setMessage(hillshadingLabel());
            SolarisTexture.invalidateAll();
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(new HillshadingStrengthSlider(x + 10, y, BOX_W - 20, 20));
    }

    private void initWaypointsTab(int x, int y) {
        addRenderableWidget(Button.builder(beamsLabel(), b -> {
            SolarisConfig.WAYPOINT_BEAMS.set(!SolarisConfig.WAYPOINT_BEAMS.get());
            b.setMessage(beamsLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(compassLabel(), b -> {
            SolarisConfig.WAYPOINT_COMPASS.set(!SolarisConfig.WAYPOINT_COMPASS.get());
            b.setMessage(compassLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(deathMarkersLabel(), b -> {
            SolarisConfig.DEATH_MARKERS.set(!SolarisConfig.DEATH_MARKERS.get());
            b.setMessage(deathMarkersLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(planShapesLabel(), b -> {
            SolarisConfig.SHOW_PLAN_SHAPES.set(!SolarisConfig.SHOW_PLAN_SHAPES.get());
            b.setMessage(planShapesLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
    }

    private void initIntegrationsTab(int x, int y) {
        addRenderableWidget(Button.builder(gtVeinsLabel(), b -> {
            SolarisConfig.SHOW_GT_ORE_VEINS.set(!SolarisConfig.SHOW_GT_ORE_VEINS.get());
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

    private Component mobsLabel() {
        boolean on = SolarisConfig.SHOW_MOBS.get();
        return Component.literal("Mobs: " + (on ? "ON" : "OFF"));
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

    private Component hillshadingLabel() {
        boolean on = SolarisConfig.HILLSHADING.get();
        return Component.literal("Hillshading: " + (on ? "ON (heavier)" : "OFF"));
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
        g.fill(x, y, x + BOX_W, y + boxH, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, boxH + 16, C_PANEL);

        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        SolarisConfig.SATURATION.save();
        SolarisConfig.WATER_OPACITY.save();
        SolarisConfig.WATER_BLEND_RADIUS.save();
        SolarisConfig.WATER_DEEP_ONLY.save();
        SolarisConfig.WAYPOINT_ICON_SCALE.save();
        SolarisConfig.SHOW_BLOCK_TOOLTIP.save();
        SolarisConfig.SHOW_MOBS.save();
        SolarisConfig.SHOW_GT_ORE_VEINS.save();
        SolarisConfig.LABEL_SIDE.save();
        SolarisConfig.WAYPOINT_BEAMS.save();
        SolarisConfig.WAYPOINT_COMPASS.save();
        SolarisConfig.DEATH_MARKERS.save();
        SolarisConfig.SHOW_PLAN_SHAPES.save();
        SolarisConfig.HILLSHADING.save();
        SolarisConfig.HILLSHADING_STRENGTH.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class SaturationSlider extends AbstractSliderButton {

        SaturationSlider(int x, int y, int w, int h) {
            // AbstractSliderButton's value is normalized 0..1; saturation's real range is 0..2.
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
        }
    }

    /**
     * Ocean sub-biome water-color blending — steps of 4 across 5 discrete positions
     * (0/4/8/12/16, see {@link SolarisConfig#WATER_BLEND_RADIUS}'s comment), not a continuous
     * slider, since intermediate values in between don't mean anything different from the
     * nearest step (the sampler snaps to a multiple of 4 regardless). 0 = OFF reproduces the
     * sharper, chunk-grid-visible look some players specifically asked to keep as an option;
     * the default (8) is unchanged from before this was configurable at all.
     */
    private static class BiomeBlendSlider extends AbstractSliderButton {

        private static final int STEP = 4;
        private static final int MAX_STEPS = 4; // 0, 4, 8, 12, 16

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
        }
    }

    private static class IconScaleSlider extends AbstractSliderButton {

        IconScaleSlider(int x, int y, int w, int h) {
            // Real range is 0.5..3.0 — normalize into the slider's required 0..1.
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
            // No SolarisTexture.invalidateAll() — icon size is applied at render time in
            // SolarisMapScreen, not baked into the terrain texture, so nothing to rebuild.
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
        }
    }
}
