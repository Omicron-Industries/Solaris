package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.C2SShareWaypointPacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

@OnlyIn(Dist.CLIENT)
public class WaypointListScreen extends Screen {

    private static final int ROW_H = 16;

    private final Screen parent;
    private Waypoint selected;
    private EditBox nameBox;
    private EditBox colorBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox searchBox;
    private boolean sortByDistance = false;
    private int scrollOffset = 0;
    private int listTop;
    private int listBottom;

    private String categoryFilter = null;
    private EditBox categoryBox;
    private EditBox labelColorBox;

    public WaypointListScreen(Screen parent) {
        super(Component.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int sideW = Math.max(180, width / 3);
        int leftW = width - sideW - 10;
        listTop = 96;
        listBottom = height - 12;

        addRenderableWidget(Button.builder(Component.literal("+ Add here"), b -> addHere())
                .bounds(8, 32, leftW - 90, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal(sortByDistance ? "Sort: Nearest" : "Sort: Name"),
                b -> {
                    sortByDistance = !sortByDistance;
                    init();
                }).bounds(8 + leftW - 86, 32, 86, 18).build());

        String previousSearch = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditBox(font, 8, 52, leftW - 8, 16, Component.literal("Search"));
        searchBox.setMaxLength(48);
        searchBox.setValue(previousSearch);
        searchBox.setHint(Component.literal("Search…"));
        addWidget(searchBox);

        List<String> categories = WaypointManager.getCategories();
        addRenderableWidget(Button.builder(Component.literal("Category: " + categoryFilterLabel()), b -> {
            categoryFilter = nextCategory(categories, categoryFilter);
            init();
        }).bounds(8, 72, leftW - 90, 18).build());

        if (categoryFilter != null) {
            boolean hidden = WaypointManager.isCategoryHidden(categoryFilter);
            addRenderableWidget(Button.builder(Component.literal(hidden ? "Hidden" : "Shown"), b -> {
                WaypointManager.setCategoryHidden(categoryFilter, !hidden);
                init();
            }).bounds(8 + leftW - 86, 72, 86, 18).build());
        }

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

            int coordW = (sideW - 20 - 8) / 3;
            xBox = new EditBox(font, editX, y, coordW, 16, Component.literal("X"));
            xBox.setMaxLength(8);
            xBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
            xBox.setValue(String.valueOf(selected.x));
            addWidget(xBox);

            yBox = new EditBox(font, editX + coordW + 4, y, coordW, 16, Component.literal("Y"));
            yBox.setMaxLength(8);
            yBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
            yBox.setValue(String.valueOf(selected.y));
            addWidget(yBox);

            zBox = new EditBox(font, editX + 2 * (coordW + 4), y, coordW, 16, Component.literal("Z"));
            zBox.setMaxLength(8);
            zBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
            zBox.setValue(String.valueOf(selected.z));
            addWidget(zBox);
            y += ROW_H + 10;

            categoryBox = new EditBox(font, editX, y, sideW - 20, 16, Component.literal("Category"));
            categoryBox.setMaxLength(32);
            categoryBox.setValue(selected.categoryOrEmpty());
            categoryBox.setHint(Component.literal("Uncategorized"));
            addWidget(categoryBox);
            y += ROW_H + 10;

            labelColorBox = new EditBox(font, editX, y, sideW - 20, 16, Component.literal("Label Color"));
            labelColorBox.setMaxLength(8);
            labelColorBox.setValue(selected.labelColor);
            labelColorBox.setHint(Component.literal("Same as theme text"));
            addWidget(labelColorBox);
            y += ROW_H + 14;

            addRenderableWidget(Button.builder(
                    Component.literal("Icon: " + WaypointIconManager.label(selected.icon)),
                    b -> Minecraft.getInstance().setScreen(new WaypointIconPickerScreen(this, selected.icon,
                            selected.colorArgb(), id -> {
                                selected.icon = id;
                                WaypointManager.save();
                            })))
                    .bounds(editX, y, sideW - 20, 18).build());
            y += ROW_H + 8;

            addRenderableWidget(Button.builder(
                    Component.literal(selected.visible ? "Visible: ON" : "Visible: OFF"),
                    b -> {
                        selected.visible = !selected.visible;
                        WaypointManager.save();
                        init();
                    }).bounds(editX, y, sideW - 20, 18).build());
            y += ROW_H + 8;

            boolean tracked = selected.id.equals(trackedId());
            addRenderableWidget(Button.builder(
                    Component.literal(tracked ? "Tracked (compass) ✓" : "Track (compass)"),
                    b -> {
                        WaypointManager.setTracked(tracked ? null : selected.id);
                        init();
                    }).bounds(editX, y, sideW - 20, 18).build());
            y += ROW_H + 8;

            if (GuildsIntegration.isAvailable()) {
                addRenderableWidget(Button.builder(Component.literal("Share to Guild"), b -> shareSelected())
                        .bounds(editX, y, sideW - 20, 18).build());
                y += ROW_H + 8;
            }

            addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveSelected())
                    .bounds(editX, y, sideW - 20, 18).build());
            y += ROW_H + 4;

            Button deleteButton = Button.builder(
                    Component.literal(selected.locked ? "Delete (locked)" : "Delete"), b -> {
                        if (selected.locked) return;
                        WaypointManager.remove(selected.id);
                        selected = null;
                        init();
                    }).bounds(editX, y, sideW - 20, 18).build();
            deleteButton.active = !selected.locked;
            addRenderableWidget(deleteButton);
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 66, 6, 56, 18).build());
    }

    private void addHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!WaypointManager.canPlace(mc.level.dimension().location())) {
            mc.player.displayClientMessage(Component.literal("Waypoints aren't available here."), true);
            return;
        }
        List<Waypoint> existing = WaypointManager.getAll();
        String name = "Waypoint " + (existing.size() + 1);
        Waypoint w = new Waypoint(name, mc.level.dimension().location(),
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), "FFFFFFFF");
        WaypointManager.add(w);
        selected = w;
        init();
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    private void shareSelected() {
        if (selected == null) return;
        Minecraft mc = Minecraft.getInstance();

        boolean allowed = mc.level != null ?
                SolarisAPI.isFeatureEnabled(SolarisAPI.FEATURE_GUILD_SHARE, mc.level.dimension().location()) :
                SolarisAPI.isFeatureEnabled(SolarisAPI.FEATURE_GUILD_SHARE);
        if (!allowed) {
            mc.player.displayClientMessage(Component.literal("Sharing isn't available right now."), true);
            return;
        }
        SolarisNetwork.CHANNEL.sendToServer(new C2SShareWaypointPacket(selected.name, selected.dimension,
                selected.x, selected.y, selected.z, selected.color, selected.icon));
    }

    private void saveSelected() {
        if (selected == null) return;
        selected.name = nameBox.getValue().isBlank() ? selected.name : nameBox.getValue();
        selected.color = colorBox.getValue();
        selected.category = categoryBox != null ? categoryBox.getValue().trim() : selected.category;
        selected.labelColor = labelColorBox != null ? labelColorBox.getValue().trim() : selected.labelColor;
        selected.x = parseCoord(xBox, selected.x);
        selected.y = parseCoord(yBox, selected.y);
        selected.z = parseCoord(zBox, selected.z);
        WaypointManager.save();
        init();
    }

    private int parseCoord(EditBox box, int fallback) {
        if (box == null) return fallback;
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String trackedId() {
        Waypoint tracked = WaypointManager.getTracked();
        return tracked != null ? tracked.id : null;
    }

    private String categoryFilterLabel() {
        if (categoryFilter == null) return "All";
        return categoryFilter.isEmpty() ? "Uncategorized" : categoryFilter;
    }

    private String nextCategory(List<String> categories, String current) {
        if (current == null) return categories.isEmpty() ? null : categories.get(0);
        int idx = categories.indexOf(current);
        if (idx < 0 || idx == categories.size() - 1) return null;
        return categories.get(idx + 1);
    }

    private List<Waypoint> visibleList() {
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : WaypointManager.getAll()) {
            if (categoryFilter != null && !w.categoryOrEmpty().equals(categoryFilter)) continue;
            if (query.isEmpty() || w.name.toLowerCase(Locale.ROOT).contains(query)) out.add(w);
        }

        Minecraft mc = Minecraft.getInstance();
        if (sortByDistance && mc.player != null && mc.level != null) {
            String currentDim = mc.level.dimension().location().toString();
            double px = mc.player.getX();
            double py = mc.player.getY();
            double pz = mc.player.getZ();
            out.sort((a, b) -> {
                boolean aHere = a.dimension.equals(currentDim);
                boolean bHere = b.dimension.equals(currentDim);
                if (aHere != bHere) return aHere ? -1 : 1;
                if (aHere) return Double.compare(a.distanceSq(px, py, pz), b.distanceSq(px, py, pz));
                return a.name.compareToIgnoreCase(b.name);
            });
        } else {
            out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int sideW = Math.max(180, width / 3);
        int leftW = width - sideW - 10;

        renderBackground(g);
        g.fill(0, 0, width, height, C_PANEL);
        VanillaPanel.draw(g, -8, -8, width + 16, height + 16, C_PANEL);
        g.fill(0, 0, width, 28, C_HEADER);
        g.drawCenteredString(font, "Waypoints", width / 2, 10, C_ACCENT);

        List<Waypoint> visible = visibleList();
        int rowY = listTop;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, visible.size() - maxVisible);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = scrollOffset; i < visible.size() && rowY + ROW_H <= listBottom; i++) {
            Waypoint w = visible.get(i);
            boolean sel = w == selected;
            boolean hov = mx >= 8 && mx < leftW && my >= rowY && my < rowY + ROW_H;
            g.fill(8, rowY, leftW, rowY + ROW_H, sel ? 0xFF2A2A44 : hov ? C_HEADER : C_PANEL);
            WaypointIconManager.draw(g, w.icon, 16, rowY + 8, 4, w.colorArgb());
            String label = w.name + "  §7(" + w.x + ", " + w.y + ", " + w.z + ")" + (w.visible ? "" : " §8[hidden]");
            g.drawString(font, truncate(label, leftW - 32), 28, rowY + 4, sel ? C_ACCENT : C_TEXT, false);
            rowY += ROW_H;
        }

        if (visible.isEmpty()) {
            g.drawString(font,
                    WaypointManager.getAll().isEmpty() ?
                            "No waypoints yet — click \"+ Add here\", or right-click the map." :
                            "No waypoints match your search.",
                    12, listTop + 4, C_DIM, false);
        }

        if (searchBox != null) searchBox.render(g, mx, my, pt);

        if (selected != null) {
            int editX = width - sideW + 10;
            g.drawString(font, "Name", editX, 30, C_DIM, false);
            g.drawString(font, "Color (hex)", editX, 30 + ROW_H + 10, C_DIM, false);
            g.drawString(font, "X / Y / Z", editX, 30 + 2 * (ROW_H + 10), C_DIM, false);
            g.drawString(font, "Category", editX, 30 + 3 * (ROW_H + 10), C_DIM, false);
            g.drawString(font, "Label Color (hex)", editX, 30 + 4 * (ROW_H + 10), C_DIM, false);
            if (nameBox != null) nameBox.render(g, mx, my, pt);
            if (colorBox != null) colorBox.render(g, mx, my, pt);
            if (xBox != null) xBox.render(g, mx, my, pt);
            if (yBox != null) yBox.render(g, mx, my, pt);
            if (zBox != null) zBox.render(g, mx, my, pt);
            if (categoryBox != null) categoryBox.render(g, mx, my, pt);
            if (labelColorBox != null) labelColorBox.render(g, mx, my, pt);
        }

        g.fill(width - sideW, 28, width - sideW + 1, height, C_BORDER);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int sideW = Math.max(180, width / 3);
        int leftW = width - sideW - 10;
        List<Waypoint> visible = visibleList();
        int rowY = listTop;
        for (int i = scrollOffset; i < visible.size() && rowY + ROW_H <= listBottom; i++) {
            if (mx >= 8 && mx < leftW && my >= rowY && my < rowY + ROW_H) {
                selected = visible.get(i);
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
