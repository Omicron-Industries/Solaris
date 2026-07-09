package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * A small right-click popup menu — colors are passed in by the caller rather than hardcoded, so it stays
 * theme-agnostic.
 */
public class ContextMenu {

    public record Item(String label, Runnable action) {}

    private static final int ROW_H = 14;
    private static final int PADDING = 4;

    private boolean open = false;
    private int x;
    private int y;
    private List<Item> items = List.of();

    public void open(int x, int y, List<Item> items) {
        this.open = true;
        this.x = x;
        this.y = y;
        this.items = items;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    private int menuWidth(Font font) {
        int w = 70;
        for (Item item : items) w = Math.max(w, font.width(item.label()) + PADDING * 2);
        return w;
    }

    public void render(GuiGraphics g, Font font, int mx, int my, int bg, int border, int text, int accent) {
        if (!open) return;

        int w = menuWidth(font);
        int h = items.size() * ROW_H + PADDING * 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        g.fill(x, y, x + w, y + h, bg);

        int rowY = y + PADDING;
        for (Item item : items) {
            boolean hov = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_H;
            g.drawString(font, item.label(), x + PADDING, rowY + 3, hov ? accent : text, false);
            rowY += ROW_H;
        }
    }

    /** Call from the owning screen's mouseClicked while {@link #isOpen()} — always consumes the click. */
    public boolean mouseClicked(double mx, double my, Font font) {
        if (!open) return false;

        int w = menuWidth(font);
        int rowY = y + PADDING;
        for (Item item : items) {
            if (mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_H) {
                item.action().run();
                close();
                return true;
            }
            rowY += ROW_H;
        }
        close(); // clicked outside the menu — just dismiss it
        return true;
    }
}
