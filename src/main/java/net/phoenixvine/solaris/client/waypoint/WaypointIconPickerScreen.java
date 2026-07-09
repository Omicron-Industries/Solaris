package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.VanillaPanel;

import java.util.List;
import java.util.function.Consumer;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

/**
 * A grid of every selectable icon — built-in shapes plus anything found in
 * {@code config/phoenix_solaris/waypoint_icons/} — click one to pick it. Replaces blindly
 * cycling through icons one at a time, which doesn't scale once custom icons are involved
 * (you'd have no way to see what's available without clicking through all of them).
 */
@OnlyIn(Dist.CLIENT)
public class WaypointIconPickerScreen extends Screen {

    private static final int CELL = 24;
    private static final int COLS = 6;
    private static final int LINE_H = 10;
    // The "no custom icons yet" hint is much wider than the 6-icon grid — the panel needs to
    // widen to actually fit it, or the text runs straight off the edge of the screen instead
    // of just past the edge of its own panel.
    private static final int HINT_WRAP_W = 240;

    private final Screen parent;
    private final String current;
    private final Consumer<String> onPick;
    private final int color;

    private int gridX;
    private int gridY;
    private int gridW;
    private int panelX;
    private int panelW;
    private List<FormattedCharSequence> hintLines = List.of();

    public WaypointIconPickerScreen(Screen parent, String current, int previewColor, Consumer<String> onPick) {
        super(Component.literal("Choose Icon"));
        this.parent = parent;
        this.current = current;
        this.color = previewColor;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        WaypointIconManager.rescan();
        List<String> ids = WaypointIconManager.allIds();
        int rows = Math.max(1, (ids.size() + COLS - 1) / COLS);

        gridW = COLS * CELL;
        gridY = Math.max(36, (height - rows * CELL) / 2);

        boolean showHint = ids.stream().noneMatch(WaypointIconManager::isCustom);
        hintLines = showHint ? font.split(Component.literal(
                "Drop PNGs in config/phoenix_solaris/waypoint_icons/ for custom icons"),
                Math.min(HINT_WRAP_W, width - 40)) : List.of();

        int hintWidth = 0;
        for (FormattedCharSequence line : hintLines) hintWidth = Math.max(hintWidth, font.width(line));

        panelW = Math.max(gridW, hintWidth);
        panelX = (width - panelW) / 2;
        gridX = (width - gridW) / 2;

        int bottomOfContent = gridY + rowsHeight() + (hintLines.isEmpty() ? 0 : 4 + hintLines.size() * LINE_H);
        int buttonY = Math.min(height - 50, bottomOfContent + 12);

        // Passes our own parent through (not `this`) — picking an item, or canceling out of the
        // item picker, should land you straight back on whichever screen opened this icon
        // picker in the first place, same as clicking a shape in the grid below already does.
        addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.literal("Pick Item/Block..."),
                        b -> Minecraft.getInstance().setScreen(new WaypointItemPickerScreen(parent, current, onPick)))
                .bounds(width / 2 - 70, buttonY, 140, 18).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 40, buttonY + 24, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        int contentBottom = gridY + rowsHeight() + (hintLines.isEmpty() ? 0 : 4 + hintLines.size() * LINE_H);
        g.fill(panelX - 12, gridY - 24, panelX + panelW + 12, contentBottom + 24, C_PANEL);
        VanillaPanel.draw(g, panelX - 20, gridY - 32, panelW + 40, contentBottom - gridY + 56, C_PANEL);
        g.drawCenteredString(font, "Choose Icon", width / 2, gridY - 16, C_ACCENT);

        List<String> ids = WaypointIconManager.allIds();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx = gridX + col * CELL + CELL / 2;
            int cy = gridY + row * CELL + CELL / 2;
            boolean hov = mx >= gridX + col * CELL && mx < gridX + (col + 1) * CELL && my >= gridY + row * CELL &&
                    my < gridY + (row + 1) * CELL;
            boolean sel = id.equals(current);

            if (sel || hov) {
                g.fill(gridX + col * CELL, gridY + row * CELL, gridX + (col + 1) * CELL, gridY + (row + 1) * CELL,
                        sel ? 0xFF2A2A44 : C_HEADER);
            }
            WaypointIconManager.draw(g, id, cx, cy, 6, color);
        }

        int hintY = gridY + rowsHeight() + 4;
        for (FormattedCharSequence line : hintLines) {
            g.drawCenteredString(font, line, width / 2, hintY, C_DIM);
            hintY += LINE_H;
        }

        super.render(g, mx, my, pt);

        int hoverIdx = hoveredIndex(mx, my);
        if (hoverIdx >= 0 && hoverIdx < ids.size()) {
            g.renderTooltip(font, Component.literal(WaypointIconManager.label(ids.get(hoverIdx))), mx, my);
        }
    }

    private int rowsHeight() {
        int count = WaypointIconManager.allIds().size();
        int rows = Math.max(1, (count + COLS - 1) / COLS);
        return rows * CELL;
    }

    private int hoveredIndex(double mx, double my) {
        if (mx < gridX || mx >= gridX + gridW || my < gridY) return -1;
        int col = (int) ((mx - gridX) / CELL);
        int row = (int) ((my - gridY) / CELL);
        if (row * CELL + gridY >= gridY + rowsHeight()) return -1;
        return row * COLS + col;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int idx = hoveredIndex(mx, my);
            List<String> ids = WaypointIconManager.allIds();
            if (idx >= 0 && idx < ids.size()) {
                onPick.accept(ids.get(idx));
                onClose();
                return true;
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
