package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WaypointBeamRenderer {

    private static final double HALF_WIDTH = 0.15;
    private static final int BEAM_HEIGHT = 192;
    private static final double ICON_HEIGHT = 1.4;
    private static final double BOB_AMPLITUDE = 0.12;
    private static final double BOB_SPEED = 0.05;
    private static final float SPIN_DEGREES_PER_TICK = 1.5f;
    private static final float ICON_SCALE = 0.7f;

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
        float time = mc.level.getGameTime() + event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        List<Waypoint> inRange = new ArrayList<>();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (Waypoint w : waypoints) {
            double dx = (w.x + 0.5) - mc.player.getX();
            double dz = (w.z + 0.5) - mc.player.getZ();
            if (dx * dx + dz * dz > rangeSq) continue;
            inRange.add(w);

            int argb = w.colorArgb();
            float r = (argb >> 16 & 255) / 255f;
            float g = (argb >> 8 & 255) / 255f;
            float b = (argb & 255) / 255f;

            LevelRenderer.renderLineBox(poseStack, lines, w.x + 0.5 - HALF_WIDTH, w.y, w.z + 0.5 - HALF_WIDTH,
                    w.x + 0.5 + HALF_WIDTH, w.y + BEAM_HEIGHT, w.z + 0.5 + HALF_WIDTH, r, g, b, 0.6f);
        }
        bufferSource.endBatch(RenderType.lines());

        for (Waypoint w : inRange) {
            if (WaypointIconManager.isItem(w.icon)) {
                renderFloatingIcon(mc, poseStack, bufferSource, w, time);
            }
        }
        bufferSource.endBatch();

        poseStack.popPose();
    }

    private static void renderFloatingIcon(Minecraft mc, PoseStack poseStack, MultiBufferSource bufferSource,
                                           Waypoint w, float time) {
        ItemStack stack = WaypointIconManager.resolveItem(w.icon);
        if (stack.isEmpty()) return;

        double bob = Math.sin(time * BOB_SPEED) * BOB_AMPLITUDE;
        float spin = (time * SPIN_DEGREES_PER_TICK) % 360f;

        poseStack.pushPose();
        poseStack.translate(w.x + 0.5, w.y + ICON_HEIGHT + bob, w.z + 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, 15728880, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource, mc.level, 0);
        poseStack.popPose();
    }
}
