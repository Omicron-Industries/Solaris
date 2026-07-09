package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;

/**
 * Small corner minimap. The backing {@link SolarisTexture} only rebuilds when the player
 * crosses a chunk boundary; the crop window inside it shifts every frame off the player's
 * exact sub-block position, so the minimap still pans smoothly between rebuilds.
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MinimapHudOverlay {

    private static final int MARGIN = 6;

    private static SolarisTexture texture;

    private static SolarisTexture texture() {
        if (texture == null) texture = new SolarisTexture("minimap", SolarisConfig.MINIMAP_RADIUS_CHUNKS.get());
        return texture;
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        SolarisTexture tex = texture();
        int screenSize = SolarisConfig.MINIMAP_SIZE.get();
        // Half of the radius's max-safe span — keeps the crop window in bounds even at the
        // smallest configurable radius (see SolarisTexture's bounds note on this formula).
        int viewPixels = tex.getRadiusChunks() * 16;

        BlockPos blockPos = mc.player.blockPosition();
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        ChunkKey center = ChunkKey.of(mc.level, new ChunkPos(chunkX, chunkZ));
        tex.maybeRebuild(center);

        double fracX = mc.player.getX() - (chunkX << 4);
        double fracZ = mc.player.getZ() - (chunkZ << 4);
        int radiusPixels = tex.getRadiusChunks() * 16;
        double playerPixelX = radiusPixels + fracX;
        double playerPixelZ = radiusPixels + fracZ;

        float u = (float) (playerPixelX - viewPixels / 2.0);
        float v = (float) (playerPixelZ - viewPixels / 2.0);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW - screenSize - MARGIN;
        int y = MARGIN;

        GuiGraphics g = event.getGuiGraphics();
        VanillaPanel.draw(g, x - 5, y - 5, screenSize + 10, screenSize + 10, C_BORDER);
        g.blit(tex.textureId(), x, y, screenSize, screenSize, u, v, viewPixels, viewPixels,
                tex.getSizePixels(), tex.getSizePixels());

        float scale = screenSize / (float) viewPixels;
        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        for (Waypoint w : waypoints) {
            double wPixelX = radiusPixels + (w.x - (chunkX << 4));
            double wPixelZ = radiusPixels + (w.z - (chunkZ << 4));
            if (wPixelX < u || wPixelX > u + viewPixels || wPixelZ < v || wPixelZ > v + viewPixels) continue;
            int wx = x + (int) ((wPixelX - u) * scale);
            int wy = y + (int) ((wPixelZ - v) * scale);
            g.fill(wx - 2, wy - 2, wx + 2, wy + 2, 0xFF000000);
            g.fill(wx - 1, wy - 1, wx + 1, wy + 1, w.colorArgb());
        }

        int cx = x + screenSize / 2;
        int cy = y + screenSize / 2;
        PlayerArrow.draw(g, cx, cy, 4, mc.player.getYRot(), C_ACCENT, mc.player.getSkinTextureLocation());
    }
}
