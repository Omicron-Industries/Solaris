package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.config.SolarisPresets;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

/**
 * Preset picker opened from the Display tab's "Load Preset" button — one row per saved preset, with a delete button.
 */
@OnlyIn(Dist.CLIENT)
public class SolarisPresetLoadScreen extends Screen {

    private static final int BOX_W = 220;
    private static final int ROW_H = 20;
    private static final int MAX_VISIBLE = 6;

    private final Screen parent;
    private final Runnable onLoaded;
    private int boxH;

    public SolarisPresetLoadScreen(Screen parent, Runnable onLoaded) {
        super(Component.literal("Load Preset"));
        this.parent = parent;
        this.onLoaded = onLoaded;
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
        List<String> names = SolarisPresets.names();
        int rows = Math.max(1, Math.min(MAX_VISIBLE, names.size()));
        boxH = 24 + rows * ROW_H + 26;

        int x = boxX();
        int y = boxY() + 24;

        for (String name : names) {
            addRenderableWidget(Button.builder(Component.literal(name), b -> {
                SolarisPresets.load(name);
                onLoaded.run();
                onClose();
            }).bounds(x + 10, y, BOX_W - 20 - 40, 18).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                SolarisPresets.delete(name);
                init();
            }).bounds(x + 10 + BOX_W - 20 - 40 + 6, y, 34, 18).build());
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + 10, boxY() + boxH - 22, BOX_W - 20, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xC0101014);

        int x = boxX();
        int y = boxY();
        g.fill(x, y, x + BOX_W, y + boxH, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, boxH + 16, C_PANEL);
        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);

        if (SolarisPresets.names().isEmpty()) {
            g.drawString(font, "No presets saved yet.", x + 10, y + 24, C_DIM, false);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
