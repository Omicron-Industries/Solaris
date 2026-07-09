package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

/**
 * Draws a thin vertical wireframe beam at each visible waypoint's real position in the 3D
 * world, not just on the map — so a waypoint is findable by looking around, matching what
 * JourneyMap/Xaero do. Uses {@link LevelRenderer#renderLineBox} (the same helper vanilla uses
 * for block-outline highlights) rather than hand-building vertex data, since a thin box
 * outline already reads fine as a "beam" without needing a proper billboard/quad renderer.
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WaypointBeamRenderer {

    private static final double HALF_WIDTH = 0.15;
    private static final int BEAM_HEIGHT = 192;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!SolarisConfig.WAYPOINT_BEAMS.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<Waypoint> waypoints = WaypointManager.getVisibleForDimension(mc.level.dimension().location());
        if (waypoints.isEmpty()) return;

        double rangeSq = (double) SolarisConfig.WAYPOINT_BEAM_RANGE.get() * SolarisConfig.WAYPOINT_BEAM_RANGE.get();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (Waypoint w : waypoints) {
            double dx = (w.x + 0.5) - mc.player.getX();
            double dz = (w.z + 0.5) - mc.player.getZ();
            if (dx * dx + dz * dz > rangeSq) continue;

            int argb = w.colorArgb();
            float r = (argb >> 16 & 255) / 255f;
            float g = (argb >> 8 & 255) / 255f;
            float b = (argb & 255) / 255f;

            LevelRenderer.renderLineBox(poseStack, consumer, w.x + 0.5 - HALF_WIDTH, w.y, w.z + 0.5 - HALF_WIDTH,
                    w.x + 0.5 + HALF_WIDTH, w.y + BEAM_HEIGHT, w.z + 0.5 + HALF_WIDTH, r, g, b, 0.6f);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }
}
