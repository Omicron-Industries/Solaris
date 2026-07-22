package net.phoenixvine.solaris.client.plan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.VanillaPanel;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

@OnlyIn(Dist.CLIENT)
public class PlanShapeListScreen extends Screen {

    private static final int ROW_H = 16;

    private final Screen parent;
    private PlanShape selected;
    private EditBox nameBox;
    private EditBox colorBox;
    private EditBox baseYBox;
    private EditBox heightBox;
    private int scrollOffset = 0;
    private int listTop;
    private int listBottom;

    public PlanShapeListScreen(Screen parent) {
        super(Component.literal("Plan Shapes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int sideW = Math.max(180, width / 3);
        int leftW = width - sideW - 10;
        listTop = 40;
        listBottom = height - 12;

        if (selected != null) {
            int editX = width - sideW + 10;
            int y = 40;

            nameBox = new EditBox(font, editX, y, sideW - 20, 16, Component.literal("Name"));
            nameBox.setMaxLength(48);
            nameBox.setValue(selected.name);
            addWidget(nameBox);
            y += ROW_H + 10;

            colorBox = new EditBox(font, editX, y, sideW - 20, 16, Component.literal("Color"));
            colorBox.setMaxLength(8);
            colorBox.setValue(selected.color);
            addWidget(colorBox);
            y += ROW_H + 10;

            int coordW = (sideW - 20 - 8) / 2;
            baseYBox = new EditBox(font, editX, y, coordW, 16, Component.literal("Base Y"));
            baseYBox.setMaxLength(6);
            baseYBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
            baseYBox.setValue(String.valueOf(selected.baseY));
            addWidget(baseYBox);

            heightBox = new EditBox(font, editX + coordW + 8, y, coordW, 16, Component.literal("Height"));
            heightBox.setMaxLength(4);
            heightBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            heightBox.setValue(String.valueOf(selected.height));
            addWidget(heightBox);
            y += ROW_H + 14;

            addRenderableWidget(Button.builder(Component.literal(selected.visible ? "Visible: ON" : "Visible: OFF"),
                    b -> {
                        selected.visible = !selected.visible;
                        PlanShapeManager.save();
                        init();
                    }).bounds(editX, y, sideW - 20, 18).build());
            y += ROW_H + 8;

            addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveSelected())
                    .bounds(editX, y, sideW - 20, 18).build());
            y += ROW_H + 4;

            addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
                PlanShapeManager.remove(selected.id);
                selected = null;
                init();
            }).bounds(editX, y, sideW - 20, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 66, 6, 56, 18).build());
    }

    private void saveSelected() {
        if (selected == null) return;
        selected.name = nameBox.getValue().isBlank() ? selected.name : nameBox.getValue();
        selected.color = colorBox.getValue();
        selected.baseY = parseInt(baseYBox.getValue(), selected.baseY);
        selected.height = Math.max(1, parseInt(heightBox.getValue(), selected.height));
        PlanShapeManager.save();
        init();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String typeLabel(PlanShape.Type type) {
        return switch (type) {
            case RECTANGLE -> "Rect";
            case CIRCLE -> "Circle";
            case LINE -> "Line";
        };
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int sideW = Math.max(180, width / 3);
        int leftW = width - sideW - 10;

        renderBackground(g);
        g.fill(0, 0, width, height, C_PANEL);
        VanillaPanel.draw(g, -8, -8, width + 16, height + 16, C_PANEL);
        g.fill(0, 0, width, 28, C_HEADER);
        g.drawCenteredString(font, "Plan Shapes", width / 2, 10, C_ACCENT);

        List<PlanShape> shapes = PlanShapeManager.getAll();
        int rowY = listTop;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, shapes.size() - maxVisible);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = scrollOffset; i < shapes.size() && rowY + ROW_H <= listBottom; i++) {
            PlanShape s = shapes.get(i);
            boolean sel = s == selected;
            boolean hov = mx >= 8 && mx < leftW && my >= rowY && my < rowY + ROW_H;
            g.fill(8, rowY, leftW, rowY + ROW_H, sel ? 0xFF2A2A44 : hov ? C_HEADER : C_PANEL);
            g.fill(12, rowY + 4, 20, rowY + 12, s.colorArgb());
            String label = "[" + typeLabel(s.type) + "] " + s.name + (s.visible ? "" : " §8[hidden]");
            g.drawString(font, truncate(label, leftW - 32), 26, rowY + 4, sel ? C_ACCENT : C_TEXT, false);
            rowY += ROW_H;
        }

        if (shapes.isEmpty()) {
            g.drawString(font, "No plan shapes yet — use the Plan tool on the map to draw one.", 12, listTop + 4,
                    C_DIM, false);
        }

        if (selected != null) {
            int editX = width - sideW + 10;
            g.drawString(font, "Name", editX, 30, C_DIM, false);
            g.drawString(font, "Color (hex)", editX, 30 + ROW_H + 10, C_DIM, false);
            g.drawString(font, "Base Y / Height", editX, 30 + 2 * (ROW_H + 10), C_DIM, false);
            if (nameBox != null) nameBox.render(g, mx, my, pt);
            if (colorBox != null) colorBox.render(g, mx, my, pt);
            if (baseYBox != null) baseYBox.render(g, mx, my, pt);
            if (heightBox != null) heightBox.render(g, mx, my, pt);
        }

        g.fill(width - sideW, 28, width - sideW + 1, height, C_BORDER);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int sideW = Math.max(180, width / 3);
        int leftW = width - sideW - 10;
        List<PlanShape> shapes = PlanShapeManager.getAll();
        int rowY = listTop;
        for (int i = scrollOffset; i < shapes.size() && rowY + ROW_H <= listBottom; i++) {
            if (mx >= 8 && mx < leftW && my >= rowY && my < rowY + ROW_H) {
                selected = shapes.get(i);
                init();
                return true;
            }
            rowY += ROW_H;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollOffset -= (int) delta;
        return true;
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
