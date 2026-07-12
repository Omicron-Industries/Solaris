package net.phoenixvine.solaris.client.waypoint;

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

/**
 * A "new waypoint here" popup — like JourneyMap's waypoint-creation dialog — rather than the
 * full {@link WaypointListScreen}. Covers everything you'd otherwise have to jump into the
 * list screen to set afterward: an editable X/Y/Z (seeded from where you clicked/stood, but
 * fully editable — the click position is just a starting snapshot, not a locked-in value),
 * one-click color swatches alongside the hex box, an icon picker (built-ins and custom), and
 * initial visibility.
 *
 * Renders the map's terrain (dimmed) behind itself instead of the vanilla dirt-background —
 * {@code Minecraft} only ever renders one active {@code Screen}, so replacing the map screen
 * with this one would otherwise show raw gameplay behind the popup instead of the map it was
 * opened from. Uses {@link SolarisMapScreen#renderMapBackground} rather than that screen's full
 * {@code render} — re-rendering the FULL map (waypoint labels, context menu, title/footer text
 * and all) a second time as a "background" caused that text to visibly overlap this popup's own
 * text instead of sitting cleanly behind it.
 */
@OnlyIn(Dist.CLIENT)
public class QuickWaypointScreen extends Screen {

    private static final int BOX_W = 216;
    private static final int BOX_H = 262;

    private static final String[] SWATCHES = {
            "FFFFFFFF", "FFFF5555", "FFFFAA00", "FFFFFF55", "FF55FF55", "FF55FFFF", "FF5599FF", "FFFF55FF"
    };

    private final Screen parent;
    private final ResourceLocation dimension;
    // Seed the X/Y/Z boxes' initial values; the boxes themselves are the source of truth for what saves.
    private final int initialX;
    private final int initialY;
    private final int initialZ;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox colorBox;
    private EditBox labelColorBox;
    private String icon = WaypointIcon.DOT.name();
    private boolean visible = true;

    public QuickWaypointScreen(Screen parent, ResourceLocation dimension, int wx, int wy, int wz) {
        super(Component.literal("New Waypoint"));
        this.parent = parent;
        this.dimension = dimension;
        this.initialX = wx;
        this.initialY = wy;
        this.initialZ = wz;
    }

    private int boxX() {
        return (width - BOX_W) / 2;
    }

    private int boxY() {
        return (height - BOX_H) / 2;
    }

    @Override
    protected void init() {
        // Vanilla only calls resize()/init() on the ACTIVE screen when the window resizes (e.g.
        // toggling fullscreen) — the backgrounded `parent` isn't the active screen, so its own
        // width/height silently go stale until it becomes active again. Keeping it in sync here,
        // every time we ourselves are (re)initialized, fixes "resize doesn't take effect until
        // a click" for the map background we render behind this popup.
        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        int x = boxX();
        int y = boxY();

        nameBox = new EditBox(font, x + 10, y + 24, BOX_W - 20, 16, Component.literal("Name"));
        nameBox.setMaxLength(48);
        nameBox.setValue("Waypoint " + (WaypointManager.getAll().size() + 1));
        addWidget(nameBox);
        setInitialFocus(nameBox);

        int coordW = (BOX_W - 20 - 8) / 3;
        xBox = new EditBox(font, x + 10, y + 50, coordW, 16, Component.literal("X"));
        xBox.setMaxLength(8);
        xBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        xBox.setValue(String.valueOf(initialX));
        addWidget(xBox);

        yBox = new EditBox(font, x + 10 + coordW + 4, y + 50, coordW, 16, Component.literal("Y"));
        yBox.setMaxLength(8);
        yBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        yBox.setValue(String.valueOf(initialY));
        addWidget(yBox);

        zBox = new EditBox(font, x + 10 + 2 * (coordW + 4), y + 50, coordW, 16, Component.literal("Z"));
        zBox.setMaxLength(8);
        zBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        zBox.setValue(String.valueOf(initialZ));
        addWidget(zBox);

        colorBox = new EditBox(font, x + 10, y + 92, BOX_W - 20, 16, Component.literal("Color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(SWATCHES[0]);
        addWidget(colorBox);

        labelColorBox = new EditBox(font, x + 10, y + 134, BOX_W - 20, 16, Component.literal("Label Color"));
        labelColorBox.setMaxLength(8);
        labelColorBox.setHint(Component.literal("Same as theme text"));
        addWidget(labelColorBox);

        addRenderableWidget(Button.builder(Component.literal("Icon: " + WaypointIconManager.label(icon)),
                b -> Minecraft.getInstance().setScreen(new WaypointIconPickerScreen(this, icon, colorArgb(), id -> {
                    icon = id;
                }))).bounds(x + 10, y + 160, BOX_W - 20, 18).build());

        addRenderableWidget(Button.builder(Component.literal(visible ? "Visible: ON" : "Visible: OFF"), b -> {
            visible = !visible;
            b.setMessage(Component.literal(visible ? "Visible: ON" : "Visible: OFF"));
        }).bounds(x + 10, y + 182, BOX_W - 20, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x + 10, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 14 + (BOX_W - 24) / 2, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
    }

    private int colorArgb() {
        try {
            String clean = colorBox.getValue().trim().toUpperCase(java.util.Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);
            return 0xFF000000 | ((int) Long.parseLong(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private int swatchY() {
        return boxY() + 112;
    }

    private int coord(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        if (!WaypointManager.canPlace(dimension)) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("Waypoints aren't available here."), true);
            onClose();
            return;
        }
        String name = nameBox.getValue().isBlank() ? "Waypoint" : nameBox.getValue();
        String color = colorBox.getValue().isBlank() ? "FFFFFFFF" : colorBox.getValue();
        int wx = coord(xBox, initialX);
        int wy = coord(yBox, initialY);
        int wz = coord(zBox, initialZ);
        Waypoint w = new Waypoint(name, dimension, wx, wy, wz, color);
        w.icon = icon;
        w.visible = visible;
        w.labelColor = labelColorBox.getValue().trim();
        WaypointManager.add(w);
        onClose();
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

        g.drawCenteredString(font, "New Waypoint", x + BOX_W / 2, y + 6, C_ACCENT);
        g.drawString(font, "Name", x + 10, y + 15, C_DIM, false);
        g.drawString(font, "X / Y / Z  (" + dimension.getPath() + ")", x + 10, y + 41, C_DIM, false);
        g.drawString(font, "Color (hex)", x + 10, y + 83, C_DIM, false);
        g.drawString(font, "Label Color (hex)", x + 10, y + 125, C_DIM, false);

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
        if (xBox != null) xBox.render(g, mx, my, pt);
        if (yBox != null) yBox.render(g, mx, my, pt);
        if (zBox != null) zBox.render(g, mx, my, pt);
        if (colorBox != null) colorBox.render(g, mx, my, pt);
        if (labelColorBox != null) labelColorBox.render(g, mx, my, pt);

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
