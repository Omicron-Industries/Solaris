package net.phoenixvine.solaris.client;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class SolarisThemeEditorScreen extends Screen {

    private final Screen parent;

    private final List<FieldEntry> fields = new ArrayList<>();
    private final List<CategoryLabel> categories = new ArrayList<>();
    private EditBox nameInput;

    private record Snapshot(String bg, String panel, String header, String border,
                            String accent, String text, String dim, String faint, String name) {}

    private record UndoEntry(Snapshot snap, String deletedName) {}

    private final Stack<UndoEntry> undoStack = new Stack<>();
    private Snapshot savedSnapshot = null;
    private String lastThemeName = null;
    private boolean isUndoing = false;

    private final List<String> pendingDeletions = new ArrayList<>();

    private boolean confirmActive = false;
    private String pendingAction = null;

    private int scrollOffset = 0;
    private int lastListItemY = 200;

    public SolarisThemeEditorScreen(Screen parent) {
        super(Component.literal("Solaris Theme Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        fields.clear();
        categories.clear();

        SolarisTheme active = SolarisTheme.current();
        String currentName = SolarisTheme.getActiveName();

        if (!currentName.equals(lastThemeName)) {
            lastThemeName = currentName;
            savedSnapshot = snap(active, currentName);
            undoStack.clear();
            pendingDeletions.clear();
            confirmActive = false;
            pendingAction = null;
        }

        int sideW = Math.max(175, width / 4);
        int startX = width - sideW + 5;
        int y = 38;
        int rowH = height > 360 ? 20 : 17;

        cat("■ Structure", startX + 5, y);
        y += 12;
        field("BG", active.bg, startX, y, sideW);
        y += rowH;
        field("Panel", active.panel, startX, y, sideW);
        y += rowH;
        field("Header", active.header, startX, y, sideW);
        y += rowH;
        field("Border", active.border, startX, y, sideW);
        y += rowH + 6;

        cat("■ Typography", startX + 5, y);
        y += 12;
        field("Text", active.text, startX, y, sideW);
        y += rowH;
        field("Dim", active.dim, startX, y, sideW);
        y += rowH + 6;

        cat("■ Highlights", startX + 5, y);
        y += 12;
        field("Accent", active.accent, startX, y, sideW);
        y += rowH;
        field("Faint (fog)", active.faint, startX, y, sideW);
        y += rowH;

        int ctrlY = Math.max(y + 22, height - 65);
        nameInput = new EditBox(font, width - sideW + 10, ctrlY, sideW - 20, 16, Component.literal("Theme ID"));
        nameInput.setValue(currentName);
        nameInput.setResponder(s -> confirmActive = false);
        addWidget(nameInput);

        int btnX = width - sideW + 10;
        int btnW = sideW - 20;
        addRenderableWidget(Button.builder(Component.literal("Save  (Ctrl+S)"), b -> save())
                .bounds(btnX, ctrlY + 20, btnW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Exit"), b -> {
            if (hasChanges()) {
                if (!confirmActive || !"EXIT".equals(pendingAction)) {
                    confirmActive = true;
                    pendingAction = "EXIT";
                    return;
                }
                restore(savedSnapshot);
            }
            onClose();
        }).bounds(btnX, ctrlY + 40, btnW, 18).build());
    }

    private void cat(String title, int x, int y) {
        categories.add(new CategoryLabel(title, x, y));
    }

    private void field(String label, SolarisTheme.ThemeColor target, int x, int y, int sideW) {
        EditBox box = new EditBox(font, x + 65, y, sideW - 75, 16, Component.literal(label));
        box.setMaxLength(16);
        box.setValue(target.getHex());
        box.setResponder(s -> {
            if (!isUndoing) pushSnap();
            target.set(s);
            confirmActive = false;
        });
        addWidget(box);
        fields.add(new FieldEntry(label, box));
    }

    private String fieldVal(String label, String fallback) {
        return fields.stream().filter(f -> f.label.equals(label)).map(f -> f.box.getValue())
                .findFirst().orElse(fallback);
    }

    private void save() {
        String id = nameInput.getValue().trim().toUpperCase(Locale.ROOT);
        if (id.isEmpty()) return;
        SolarisTheme active = SolarisTheme.current();
        SolarisTheme saved = new SolarisTheme(
                fieldVal("BG", active.bg.hex), fieldVal("Panel", active.panel.hex),
                fieldVal("Header", active.header.hex), fieldVal("Border", active.border.hex),
                fieldVal("Accent", active.accent.hex), fieldVal("Text", active.text.hex),
                fieldVal("Dim", active.dim.hex), fieldVal("Faint (fog)", active.faint.hex));
        pendingDeletions.remove(id);
        SolarisTheme.saveCustomTheme(id, saved);
        SolarisTheme.setCurrent(id);
        savedSnapshot = snap(SolarisTheme.current(), id);
        confirmActive = false;
        pendingAction = null;
        init();
    }

    private void pushSnap() {
        Snapshot cur = snap(SolarisTheme.current(),
                nameInput != null ? nameInput.getValue() : SolarisTheme.getActiveName());
        if (undoStack.isEmpty() || !undoStack.peek().snap().equals(cur)) {
            undoStack.push(new UndoEntry(cur, null));
            if (undoStack.size() > 50) undoStack.remove(0);
        }
    }

    private void tryUndo() {
        if (undoStack.isEmpty()) return;
        isUndoing = true;
        UndoEntry entry = undoStack.pop();
        if (entry.deletedName() != null) {
            pendingDeletions.remove(entry.deletedName().toUpperCase(Locale.ROOT));
            init();
        } else {
            Snapshot s = entry.snap();
            restore(s);
            for (FieldEntry f : fields) {
                switch (f.label) {
                    case "BG" -> f.box.setValue(s.bg());
                    case "Panel" -> f.box.setValue(s.panel());
                    case "Header" -> f.box.setValue(s.header());
                    case "Border" -> f.box.setValue(s.border());
                    case "Accent" -> f.box.setValue(s.accent());
                    case "Text" -> f.box.setValue(s.text());
                    case "Dim" -> f.box.setValue(s.dim());
                    case "Faint (fog)" -> f.box.setValue(s.faint());
                }
            }
            if (nameInput != null) nameInput.setValue(s.name());
        }
        confirmActive = false;
        isUndoing = false;
    }

    private boolean hasChanges() {
        if (savedSnapshot == null) return false;
        return !savedSnapshot.equals(
                snap(SolarisTheme.current(), nameInput != null ? nameInput.getValue() : SolarisTheme.getActiveName()));
    }

    private Snapshot snap(SolarisTheme t, String name) {
        return new Snapshot(t.bg.hex, t.panel.hex, t.header.hex, t.border.hex,
                t.accent.hex, t.text.hex, t.dim.hex, t.faint.hex, name);
    }

    private void restore(Snapshot s) {
        SolarisTheme t = SolarisTheme.current();
        t.bg.set(s.bg());
        t.panel.set(s.panel());
        t.header.set(s.header());
        t.border.set(s.border());
        t.accent.set(s.accent());
        t.text.set(s.text());
        t.dim.set(s.dim());
        t.faint.set(s.faint());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int sideW = Math.max(175, width / 4);

        renderBackground(g);
        g.fill(width - sideW, 0, width, height, C_PANEL);
        VanillaPanel.draw(g, width - sideW - 6, -6, sideW + 12, height + 12, C_PANEL);

        g.drawString(font, "Solaris Theme Editor", width - sideW + 10, 10, C_ACCENT, false);
        if (confirmActive)
            g.drawString(font, "Click again to discard changes!", width - sideW + 10, 22, 0xFFFFAA33, false);
        else if (hasChanges())
            g.drawString(font, "Unsaved: " + SolarisTheme.getActiveName(), width - sideW + 10, 22, 0xFFFFAA33, false);
        else
            g.drawString(font, "Active: " + SolarisTheme.getActiveName(), width - sideW + 10, 22, C_TEXT, false);

        for (CategoryLabel cat : categories)
            g.drawString(font, cat.title, cat.x, cat.y, C_ACCENT, false);
        for (FieldEntry f : fields) {
            g.drawString(font, f.label, f.box.getX() - 62, f.box.getY() + 4, C_TEXT, false);
            f.box.render(g, mx, my, pt);
        }
        if (nameInput != null) nameInput.render(g, mx, my, pt);

        renderPreview(g, mx, my);
        super.render(g, mx, my, pt);
    }

    private void renderPreview(GuiGraphics g, int mx, int my) {
        int sideW = Math.max(175, width / 4);
        int leftW = width - sideW - 10;

        int pTop = 12;
        int pH = height > 360 ? 90 : 65;
        g.fill(15, pTop, leftW, pTop + pH, C_PANEL);
        g.fill(15, pTop, leftW, pTop + 16, C_HEADER);
        g.fill(15, pTop, leftW, pTop + 1, C_BORDER);
        g.fill(15, pTop + pH - 1, leftW, pTop + pH, C_BORDER);
        g.fill(15, pTop, 16, pTop + pH, C_BORDER);
        g.fill(leftW - 1, pTop, leftW, pTop + pH, C_BORDER);
        g.drawCenteredString(font, "Solaris", 15 + (leftW - 15) / 2, pTop + 4, C_ACCENT);

        int fogY = pTop + 20;
        g.fill(24, fogY, leftW - 60, pTop + pH - 8, C_FAINT);

        int chipSize = Math.min(48, pH - 26);
        int chipX = leftW - 16 - chipSize;
        int chipY = pTop + 20;
        g.fill(chipX - 1, chipY - 1, chipX + chipSize + 1, chipY + chipSize + 1, C_BORDER);
        g.fill(chipX, chipY, chipX + chipSize, chipY + chipSize, C_FAINT);
        int cx = chipX + chipSize / 2;
        int cy = chipY + chipSize / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, C_ACCENT);

        g.drawString(font, "Dim label text", 22, pTop + pH + 6, C_DIM, false);

        int listTitleY = pTop + pH + 22;
        g.drawString(font, "Available Themes (click to switch):", 15, listTitleY, C_TEXT, false);

        int itemY = listTitleY + 14;
        lastListItemY = itemY;
        List<String> visible = getVisible();
        int maxVis = Math.max(1, (height - itemY - 15) / 16);
        int maxScroll = Math.max(0, visible.size() - maxVis);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = scrollOffset; i < visible.size() && itemY < height - 15; i++) {
            String name = visible.get(i);
            boolean sel = name.equals(SolarisTheme.getActiveName());
            boolean hov = mx >= 15 && mx < leftW && my >= itemY && my <= itemY + 14;
            g.fill(15, itemY, leftW, itemY + 14, sel ? 0xFF1A1A3A : hov ? C_HEADER : C_PANEL);
            g.fill(15, itemY, leftW, itemY + 1, C_BORDER);
            g.drawString(font, (sel ? "● " : "○ ") + name, 22, itemY + 3,
                    sel ? C_ACCENT : hov ? C_TEXT : C_DIM, false);
            if (!SolarisTheme.isBuiltin(name)) {
                int dx = leftW - 14;
                boolean dHov = mx >= dx && mx < dx + 12 && my >= itemY && my <= itemY + 14;
                g.drawString(font, "X", dx + 2, itemY + 3, dHov ? 0xFFFF5555 : 0x88FF5555, false);
            }
            itemY += 16;
        }
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (Screen.hasControlDown()) {
            if (kc == 90) {
                tryUndo();
                return true;
            }
            if (kc == 83) {
                save();
                return true;
            }
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        int sideW = Math.max(175, width / 4);
        int leftW = width - sideW - 10;
        int itemY = lastListItemY;
        List<String> visible = getVisible();

        for (int i = scrollOffset; i < visible.size() && itemY < height - 15; i++) {
            String name = visible.get(i);
            if (my >= itemY && my <= itemY + 14) {
                if (!SolarisTheme.isBuiltin(name)) {
                    int dx = leftW - 14;
                    if (mx >= dx && mx < dx + 12) {
                        pendingDeletions.add(name.toUpperCase(Locale.ROOT));
                        undoStack.push(new UndoEntry(null, name));
                        if (name.equalsIgnoreCase(SolarisTheme.getActiveName())) SolarisTheme.setCurrent("NEBULA");
                        confirmActive = false;
                        pendingAction = null;
                        init();
                        return true;
                    }
                }
                if (mx >= 15 && mx < leftW - 16) {
                    if (hasChanges()) {
                        if (!confirmActive || !name.equals(pendingAction)) {
                            confirmActive = true;
                            pendingAction = name;
                            return true;
                        }
                        restore(savedSnapshot);
                    }
                    SolarisTheme.setCurrent(name);
                    confirmActive = false;
                    pendingAction = null;
                    init();
                    return true;
                }
            }
            itemY += 16;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int sideW = Math.max(175, width / 4);
        if (mx < width - sideW - 5) {
            int maxVis = Math.max(1, (height - lastListItemY - 15) / 16);
            int maxScroll = Math.max(0, getVisible().size() - maxVis);
            scrollOffset = Mth.clamp(scrollOffset - (int) delta, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        for (String name : pendingDeletions) SolarisTheme.deleteCustom(name);
        pendingDeletions.clear();
        Minecraft.getInstance().setScreen(parent);
    }

    private List<String> getVisible() {
        List<String> out = new ArrayList<>();
        for (String name : SolarisTheme.REGISTRY.keySet())
            if (!pendingDeletions.contains(name.toUpperCase(Locale.ROOT))) out.add(name);
        return out;
    }

    private record FieldEntry(String label, EditBox box) {}

    private record CategoryLabel(String title, int x, int y) {}
}
