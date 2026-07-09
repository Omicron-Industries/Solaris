package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.solaris.client.render.VanillaPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

/**
 * Lets a waypoint use ANY item or block's real icon as its marker, searched by name, instead
 * of only the fixed handful of hand-picked items ({@code WaypointIcon} used to hardcode iron
 * tools directly). Every item registered with Forge ({@link ForgeRegistries#ITEMS}) is a
 * candidate — scanned once and cached, since it's a few thousand entries and doesn't change
 * mid-session.
 */
@OnlyIn(Dist.CLIENT)
public class WaypointItemPickerScreen extends Screen {

    private static final int CELL = 24;
    private static final int COLS = 10;

    private static List<ItemStack> allItems;

    private final Screen parent;
    private final String current;
    private final Consumer<String> onPick;

    private EditBox searchBox;
    private int scrollOffset = 0;
    private int gridX;
    private int gridY;
    private int gridBottom;

    public WaypointItemPickerScreen(Screen parent, String current, Consumer<String> onPick) {
        super(Component.literal("Choose Item/Block"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
    }

    private static List<ItemStack> allItems() {
        if (allItems == null) {
            List<ItemStack> out = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item == net.minecraft.world.item.Items.AIR) continue;
                out.add(new ItemStack(item));
            }
            out.sort((a, b) -> a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString()));
            allItems = out;
        }
        return allItems;
    }

    @Override
    protected void init() {
        int gridW = COLS * CELL;
        gridX = (width - gridW) / 2;
        gridY = 46;
        gridBottom = height - 34;

        String previousSearch = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditBox(font, width / 2 - 100, 22, 200, 16, Component.literal("Search"));
        searchBox.setMaxLength(48);
        searchBox.setValue(previousSearch);
        searchBox.setHint(Component.literal("Search items/blocks…"));
        addWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 40, height - 26, 80, 18).build());
    }

    private List<ItemStack> filtered() {
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        if (query.isEmpty()) return allItems();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : allItems()) {
            if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) out.add(stack);
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(0, 0, width, height, C_PANEL);
        VanillaPanel.draw(g, -8, -8, width + 16, height + 16, C_PANEL);
        g.drawCenteredString(font, "Choose Item/Block", width / 2, 8, C_ACCENT);

        List<ItemStack> items = filtered();
        int cols = COLS;
        int visibleRows = Math.max(1, (gridBottom - gridY) / CELL);
        int maxScroll = Math.max(0, (items.size() + cols - 1) / cols - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int startIndex = scrollOffset * cols;
        for (int i = startIndex; i < items.size(); i++) {
            int idx = i - startIndex;
            int col = idx % cols;
            int row = idx / cols;
            int cellY = gridY + row * CELL;
            if (cellY >= gridBottom) break;

            ItemStack stack = items.get(i);
            int cellX = gridX + col * CELL;
            boolean hov = mx >= cellX && mx < cellX + CELL && my >= cellY && my < cellY + CELL;
            boolean sel = current != null && current.equals(WaypointIconManager.itemIconId(stack.getItem()));
            if (sel || hov) {
                g.fill(cellX, cellY, cellX + CELL, cellY + CELL, sel ? 0xFF2A2A44 : C_HEADER);
            }
            g.renderItem(stack, cellX + CELL / 2 - 8, cellY + CELL / 2 - 8);
        }

        if (searchBox != null) searchBox.render(g, mx, my, pt);
        super.render(g, mx, my, pt);

        int hoverIdx = hoveredIndex(mx, my, startIndex, cols, items.size());
        if (hoverIdx >= 0) {
            g.renderTooltip(font, items.get(hoverIdx).getHoverName(), mx, my);
        }
    }

    private int hoveredIndex(double mx, double my, int startIndex, int cols, int total) {
        if (mx < gridX || mx >= gridX + cols * CELL || my < gridY || my >= gridBottom) return -1;
        int col = (int) ((mx - gridX) / CELL);
        int row = (int) ((my - gridY) / CELL);
        int idx = startIndex + row * cols + col;
        return idx >= startIndex && idx < total ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            List<ItemStack> items = filtered();
            int startIndex = scrollOffset * COLS;
            int idx = hoveredIndex(mx, my, startIndex, COLS, items.size());
            if (idx >= 0) {
                onPick.accept(WaypointIconManager.itemIconId(items.get(idx).getItem()));
                onClose();
                return true;
            }
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
