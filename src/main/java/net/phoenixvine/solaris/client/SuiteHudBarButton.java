package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;

import org.lwjgl.glfw.GLFW;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;

/**
 * Draws this mod's slot in the cross-mod "suite HUD bar" — a small row of icon buttons, one per
 * installed Phoenix-suite mod, lined up along the top-left of the screen during normal gameplay.
 * Every sibling mod implements this identically (same {@link #SUITE_ORDER}, same layout constants)
 * so that whichever subset of the suite happens to be installed still lines up with no gaps or
 * overlaps. There is intentionally no shared library between the mods — this class is fully
 * standalone.
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SuiteHudBarButton {

    private static final String SELF_ID = "solaris";

    private static final String[] SUITE_ORDER = {
            "solaris", "phoenix_essentials", "phoenix_domains", "phoenix_chronicles", "phoenix_guilds",
            "phoenix_excavate"
    };

    private static final int BTN_SIZE = 20;
    private static final int GAP = 2;
    private static final int MARGIN = 4;

    // Icons wrap into a compact grid (3 wide) instead of one long single-row strip, so the whole
    // suite bar stays a tight square-ish cluster near the corner rather than sprawling across the
    // top of the screen.
    private static final int GRID_COLUMNS = 3;

    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(PhoenixSolaris.MOD_ID,
            "textures/gui/suite_bar_icon.png");

    private SuiteHudBarButton() {}

    private static int iconCountFor(String modId) {
        if (!modId.equals("phoenix_essentials")) return 1;
        Minecraft mc = Minecraft.getInstance();
        return (mc.player != null && mc.player.hasPermissions(2)) ? 5 : 2;
    }

    private static int mySlotIndex() {
        int idx = 0;
        for (String id : SUITE_ORDER) {
            if (id.equals(SELF_ID)) return idx;
            if (ModList.get().isLoaded(id)) idx += iconCountFor(id);
        }
        return idx;
    }

    private static int totalLoadedIconCount() {
        int count = 0;
        for (String id : SUITE_ORDER) if (ModList.get().isLoaded(id)) count += iconCountFor(id);
        return count;
    }

    private static int buttonX() {
        int col = mySlotIndex() % GRID_COLUMNS;
        return MARGIN + col * (BTN_SIZE + GAP);
    }

    private static int buttonY() {
        int row = mySlotIndex() / GRID_COLUMNS;
        return MARGIN + row * (BTN_SIZE + GAP);
    }

    /**
     * Total footprint of the whole suite bar (every loaded suite mod's slot, not just this one).
     * Kept for parity with sibling suite mods that expose it for their own overlay/exclusion-area
     * logic; unused within this mod.
     */
    public static int barWidth() {
        int cols = Math.min(GRID_COLUMNS, Math.max(1, totalLoadedIconCount()));
        return MARGIN * 2 + cols * BTN_SIZE + (cols - 1) * GAP;
    }

    public static int barHeight() {
        int rows = (int) Math.ceil(totalLoadedIconCount() / (double) GRID_COLUMNS);
        rows = Math.max(1, rows);
        return MARGIN * 2 + rows * BTN_SIZE + (rows - 1) * GAP;
    }

    /**
     * The bar only ever renders on screens that opt in: any container screen that binds a slot to
     * the local player's inventory (covers the vanilla inventory/creative screens without
     * hardcoding them, plus crafting table, furnace, anvil, chest+player-inv, etc.), or any screen
     * that explicitly asks for it via {@link SuiteHudBarAware}. It never draws during plain
     * gameplay with no screen open, and never silently draws on top of some other mod's or
     * vanilla's unrelated GUI.
     */
    private static boolean screenWantsBar(Screen screen) {
        if (screen instanceof SuiteHudBarAware) return true;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        Inventory playerInv = mc.player.getInventory();
        for (Slot slot : containerScreen.getMenu().slots) {
            if (slot.container == playerInv) return true;
        }
        return false;
    }

    private static boolean isHovered(double mx, double my) {
        int x = buttonX();
        int y = buttonY();
        return mx >= x && mx <= x + BTN_SIZE && my >= y && my <= y + BTN_SIZE;
    }

    /**
     * Always drawn at the same absolute screen-space coordinates as the plain HUD overlay
     * (top-left of the real viewport) regardless of which render path called it - a screen's own
     * panel layout (e.g. the inventory's centered crafting grid) is never consulted, so the bar
     * reads as "part of the permanent screen furniture", not "part of that panel".
     */
    private static void draw(GuiGraphics g, Minecraft mc, double hoverMx, double hoverMy) {
        int x = buttonX();
        int y = buttonY();
        boolean hovered = isHovered(hoverMx, hoverMy);

        int bg = hovered ? C_ACCENT : C_BORDER;
        g.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, withAlpha(bg, hovered ? 0xE0 : 0xC0));
        g.fill(x, y, x + BTN_SIZE, y + 1, C_BORDER);
        g.fill(x, y + BTN_SIZE - 1, x + BTN_SIZE, y + BTN_SIZE, C_BORDER);
        g.fill(x, y, x + 1, y + BTN_SIZE, C_BORDER);
        g.fill(x + BTN_SIZE - 1, y, x + BTN_SIZE, y + BTN_SIZE, C_BORDER);

        g.blit(ICON_TEXTURE, x + 2, y + 2, 0, 0, 16, 16, 16, 16);

        if (hovered) {
            g.renderTooltip(mc.font, net.minecraft.network.chat.Component.literal("§fOpen Map"),
                    (int) hoverMx, (int) hoverMy);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !screenWantsBar(event.getScreen())) return;

        draw(event.getGuiGraphics(), mc, event.getMouseX(), event.getMouseY());
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    @SubscribeEvent
    public static void onScreenMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (mc.player == null || !screenWantsBar(screen)) return;
        if (!isHovered(event.getMouseX(), event.getMouseY())) return;

        event.setCanceled(true);

        mc.setScreen(null);
        SolarisAPI.openMap(screen);
    }
}
