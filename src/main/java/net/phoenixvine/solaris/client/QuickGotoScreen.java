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

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

/**
 * A small "jump to coordinate" popup — same dimmed-map-background style as
 * {@link net.phoenixvine.solaris.client.waypoint.QuickWaypointScreen}, just two fields instead of
 * a full waypoint form. Only pans the flat map (see {@link SolarisMapScreen#goToCoordinate}) —
 * jumping the globe's rotation to a specific coordinate is a meaningfully different problem
 * (inverse of {@code GlobeCamera.sphereToScreen}, not a viewport offset) and out of scope here.
 */
@OnlyIn(Dist.CLIENT)
public class QuickGotoScreen extends Screen {

    private static final int BOX_W = 180;
    private static final int BOX_H = 96;

    private final SolarisMapScreen parent;

    private EditBox xBox;
    private EditBox zBox;

    public QuickGotoScreen(SolarisMapScreen parent) {
        super(Component.literal("Go to Coordinate"));
        this.parent = parent;
    }

    private int boxX() {
        return (width - BOX_W) / 2;
    }

    private int boxY() {
        return (height - BOX_H) / 2;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());

        int x = boxX();
        int y = boxY();

        int coordW = (BOX_W - 20 - 8) / 2;
        xBox = new EditBox(font, x + 10, y + 26, coordW, 16, Component.literal("X"));
        xBox.setMaxLength(8);
        xBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        addWidget(xBox);
        setInitialFocus(xBox);

        zBox = new EditBox(font, x + 10 + coordW + 8, y + 26, coordW, 16, Component.literal("Z"));
        zBox.setMaxLength(8);
        zBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        addWidget(zBox);

        addRenderableWidget(Button.builder(Component.literal("Go"), b -> go())
                .bounds(x + 10, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 14 + (BOX_W - 24) / 2, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
    }

    private void go() {
        try {
            int worldX = Integer.parseInt(xBox.getValue());
            int worldZ = Integer.parseInt(zBox.getValue());
            parent.goToCoordinate(worldX, worldZ);
            onClose();
        } catch (NumberFormatException ignored) {
            // Empty/incomplete input — leave the popup open rather than jumping to a fallback
            // coordinate the player never actually asked for.
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        parent.renderMapBackground(g);
        g.fill(0, 0, width, height, 0xE0101014);

        int x = boxX();
        int y = boxY();
        g.fill(x, y, x + BOX_W, y + BOX_H, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, BOX_H + 16, C_PANEL);

        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);
        g.drawString(font, "X / Z", x + 10, y + 17, C_DIM, false);

        if (xBox != null) xBox.render(g, mx, my, pt);
        if (zBox != null) zBox.render(g, mx, my, pt);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            go();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
