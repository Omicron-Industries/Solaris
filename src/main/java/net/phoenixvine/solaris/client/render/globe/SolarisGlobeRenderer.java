package net.phoenixvine.solaris.client.render.globe;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class SolarisGlobeRenderer {

    private static final float DEPTH = 200f;

    private static final int FULL_BRIGHT = 15728880;

    private SolarisGlobeRenderer() {}

    public static void renderSphere(GuiGraphics g, int cx, int cy, float screenRadius, Quaternionf rotation,
                                    ResourceLocation textureId, SphereMesh mesh) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, DEPTH);
        pose.mulPoseMatrix(new Matrix4f().scaling(screenRadius, screenRadius, -screenRadius));
        pose.mulPose(rotation);

        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource bufferSource = g.bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(textureId));
        mesh.render(consumer, pose.last(), FULL_BRIGHT);
        g.flush();

        pose.popPose();
        Lighting.setupFor3DItems();
    }
}
