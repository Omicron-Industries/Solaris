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

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

/**
 * The map's settings popup — reachable from the fullscreen map's right-click menu, so
 * there's one obvious place for the tunables that don't fit the theme editor's color-field
 * model: map color saturation, water tint strength (both the continuous depth-based curve
 * and the Y-threshold "Deep Water Only" alternative — kept as two separate options rather
 * than one replacing the other, since different water bodies read better under each),
 * waypoint icon size, the block-hover "cheat" tooltip toggle, and (only shown when GTCEu is
 * actually installed) the GT ore vein marker toggle. All backed by {@link SolarisConfig}
 * ({@code ForgeConfigSpec} values previously only reachable by hand-editing
 * {@code config/phoenix_solaris-client.toml}). Every water/saturation control applies
 * immediately via {@link SolarisTexture#invalidateAll()} so previewing a change doesn't
 * require reopening the map. Dims/blurs the world behind it despite not pausing — an earlier
 * version skipped that to read as a floating widget, but it left the panel with too little
 * contrast against a bright/busy world to read comfortably.
 */
@OnlyIn(Dist.CLIENT)
public class SolarisDisplaySettingsScreen extends Screen {

    private static final int BOX_W = 200;

    private final Screen parent;
    private int boxH = 172;

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
        boolean showGtToggle = GtceuIntegration.isAvailable();
        // Top padding + 9 rows (saturation, water opacity, deep-only, icon size, tooltip,
        // label side, beams, compass, death markers) [+1 more if the GT toggle is shown]
        // + close button + bottom padding.
        boxH = 24 + 9 * 24 + (showGtToggle ? 24 : 0) + 28;

        int x = boxX();
        int y = boxY() + 24;

        addRenderableWidget(new SaturationSlider(x + 10, y, BOX_W - 20, 20));
        y += 24;
        addRenderableWidget(new WaterOpacitySlider(x + 10, y, BOX_W - 20, 20));
        y += 24;

        addRenderableWidget(Button.builder(deepOnlyLabel(), b -> {
            SolarisConfig.WATER_DEEP_ONLY.set(!SolarisConfig.WATER_DEEP_ONLY.get());
            b.setMessage(deepOnlyLabel());
            SolarisTexture.invalidateAll();
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += 24;

        addRenderableWidget(new IconScaleSlider(x + 10, y, BOX_W - 20, 20));
        y += 24;

        addRenderableWidget(Button.builder(tooltipLabel(), b -> {
            SolarisConfig.SHOW_BLOCK_TOOLTIP.set(!SolarisConfig.SHOW_BLOCK_TOOLTIP.get());
            b.setMessage(tooltipLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += 24;

        if (showGtToggle) {
            addRenderableWidget(Button.builder(gtVeinsLabel(), b -> {
                SolarisConfig.SHOW_GT_ORE_VEINS.set(!SolarisConfig.SHOW_GT_ORE_VEINS.get());
                b.setMessage(gtVeinsLabel());
            }).bounds(x + 10, y, BOX_W - 20, 18).build());
            y += 24;
        }

        addRenderableWidget(Button.builder(labelSideLabel(), b -> {
            SolarisConfig.LABEL_SIDE.set(SolarisConfig.LABEL_SIDE.get().next());
            b.setMessage(labelSideLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += 24;

        addRenderableWidget(Button.builder(beamsLabel(), b -> {
            SolarisConfig.WAYPOINT_BEAMS.set(!SolarisConfig.WAYPOINT_BEAMS.get());
            b.setMessage(beamsLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += 24;

        addRenderableWidget(Button.builder(compassLabel(), b -> {
            SolarisConfig.WAYPOINT_COMPASS.set(!SolarisConfig.WAYPOINT_COMPASS.get());
            b.setMessage(compassLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += 24;

        addRenderableWidget(Button.builder(deathMarkersLabel(), b -> {
            SolarisConfig.DEATH_MARKERS.set(!SolarisConfig.DEATH_MARKERS.get());
            b.setMessage(deathMarkersLabel());
        }).bounds(x + 10, y, BOX_W - 20, 18).build());
        y += 24;

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + 10, y, BOX_W - 20, 18).build());
    }

    private Component tooltipLabel() {
        boolean on = SolarisConfig.SHOW_BLOCK_TOOLTIP.get();
        return Component.literal("Block Tooltip: " + (on ? "ON (cheat)" : "OFF"));
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
        renderBackground(g);
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
        SolarisConfig.WATER_DEEP_ONLY.save();
        SolarisConfig.WAYPOINT_ICON_SCALE.save();
        SolarisConfig.SHOW_BLOCK_TOOLTIP.save();
        SolarisConfig.SHOW_GT_ORE_VEINS.save();
        SolarisConfig.LABEL_SIDE.save();
        SolarisConfig.WAYPOINT_BEAMS.save();
        SolarisConfig.WAYPOINT_COMPASS.save();
        SolarisConfig.DEATH_MARKERS.save();
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
}
