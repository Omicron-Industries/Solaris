package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.config.SolarisPresets;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

@OnlyIn(Dist.CLIENT)
public class SolarisPresetSaveScreen extends Screen {

    private static final int BOX_W = 200;
    private static final int BOX_H = 76;

    private final Screen parent;
    private final Runnable onSaved;
    private EditBox nameBox;

    public SolarisPresetSaveScreen(Screen parent, Runnable onSaved) {
        super(Component.literal("Save Preset"));
        this.parent = parent;
        this.onSaved = onSaved;
    }

    private int boxX() {
        return (width - BOX_W) / 2;
    }

    private int boxY() {
        return (height - BOX_H) / 2;
    }

    @Override
    protected void init() {
        int x = boxX();
        int y = boxY();

        nameBox = new EditBox(font, x + 10, y + 22, BOX_W - 20, 16, Component.literal("Name"));
        nameBox.setMaxLength(32);
        addWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x + 10, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 14 + (BOX_W - 24) / 2, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        SolarisPresets.save(name);
        onSaved.run();
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xC0101014);

        int x = boxX();
        int y = boxY();
        g.fill(x, y, x + BOX_W, y + BOX_H, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, BOX_H + 16, C_PANEL);
        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);
        g.drawString(font, "Preset name", x + 10, y + 13, C_DIM, false);
        if (nameBox != null) nameBox.render(g, mx, my, pt);

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
