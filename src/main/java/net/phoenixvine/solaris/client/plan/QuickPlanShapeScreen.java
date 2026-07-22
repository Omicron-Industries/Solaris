package net.phoenixvine.solaris.client.plan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.SolarisMapScreen;
import net.phoenixvine.solaris.client.render.VanillaPanel;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

@OnlyIn(Dist.CLIENT)
public class QuickPlanShapeScreen extends Screen {

    private static final int BOX_W = 216;
    private static final int BOX_H = 172;

    private static final String[] SWATCHES = {
            "FFFFFFFF", "FFFF5555", "FFFFAA00", "FFFFFF55", "FF55FF55", "FF55FFFF", "FF5599FF", "FFFF55FF"
    };

    private final Screen parent;
    private final ResourceLocation dimension;
    private final PlanShape.Type type;
    private final List<int[]> points;
    private final int radius;
    private final int initialBaseY;

    private EditBox nameBox;
    private EditBox colorBox;
    private EditBox baseYBox;
    private EditBox heightBox;

    public QuickPlanShapeScreen(Screen parent, ResourceLocation dimension, PlanShape.Type type, List<int[]> points,
                                int radius, int baseY) {
        super(Component.literal("New " + label(type)));
        this.parent = parent;
        this.dimension = dimension;
        this.type = type;
        this.points = points;
        this.radius = radius;
        this.initialBaseY = baseY;
    }

    private static String label(PlanShape.Type type) {
        return switch (type) {
            case RECTANGLE -> "Rectangle";
            case CIRCLE -> "Circle";
            case LINE -> "Line";
        };
    }

    private int boxX() {
        return (width - BOX_W) / 2;
    }

    private int boxY() {
        return (height - BOX_H) / 2;
    }

    @Override
    protected void init() {
        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        int x = boxX();
        int y = boxY();

        nameBox = new EditBox(font, x + 10, y + 24, BOX_W - 20, 16, Component.literal("Name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(label(type) + " Plan");
        addWidget(nameBox);
        setInitialFocus(nameBox);

        colorBox = new EditBox(font, x + 10, y + 66, BOX_W - 20, 16, Component.literal("Color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(SWATCHES[0]);
        addWidget(colorBox);

        int halfW = (BOX_W - 20 - 8) / 2;
        baseYBox = new EditBox(font, x + 10, y + 108, halfW, 16, Component.literal("Base Y"));
        baseYBox.setMaxLength(6);
        baseYBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        baseYBox.setValue(String.valueOf(initialBaseY));
        addWidget(baseYBox);

        heightBox = new EditBox(font, x + 10 + halfW + 8, y + 108, halfW, 16, Component.literal("Height"));
        heightBox.setMaxLength(4);
        heightBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        heightBox.setValue("4");
        addWidget(heightBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x + 10, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 14 + (BOX_W - 24) / 2, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
    }

    private int swatchY() {
        return boxY() + 90;
    }

    private void save() {
        String name = nameBox.getValue().isBlank() ? label(type) + " Plan" : nameBox.getValue();
        String color = colorBox.getValue().isBlank() ? "FFFFFFFF" : colorBox.getValue();

        PlanShape shape = new PlanShape(name, dimension, type, color);
        shape.points = points;
        shape.radius = radius;
        shape.baseY = parseInt(baseYBox.getValue(), initialBaseY);
        shape.height = Math.max(1, parseInt(heightBox.getValue(), 4));
        PlanShapeManager.add(shape);

        onClose();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
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
        g.fill(x, y, x + BOX_W, y + BOX_H, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, BOX_H + 16, C_PANEL);

        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);
        g.drawString(font, "Name", x + 10, y + 15, C_DIM, false);
        g.drawString(font, "Color (hex)", x + 10, y + 57, C_DIM, false);
        g.drawString(font, "Base Y / Height", x + 10, y + 99, C_DIM, false);

        List<String> swatches = List.of(SWATCHES);
        int sy = swatchY();
        for (int i = 0; i < swatches.size(); i++) {
            int sx = x + 10 + i * 20;
            int argb = 0xFF000000 | (Integer.parseUnsignedInt(swatches.get(i).substring(2), 16));
            boolean hov = mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
            if (hov) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, C_ACCENT);
            g.fill(sx, sy, sx + 16, sy + 16, argb);
        }

        if (nameBox != null) nameBox.render(g, mx, my, pt);
        if (colorBox != null) colorBox.render(g, mx, my, pt);
        if (baseYBox != null) baseYBox.render(g, mx, my, pt);
        if (heightBox != null) heightBox.render(g, mx, my, pt);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = boxX();
            int sy = swatchY();
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = x + 10 + i * 20;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    colorBox.setValue(SWATCHES[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
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
