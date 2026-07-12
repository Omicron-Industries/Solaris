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

/**
 * Draws the terrain sphere for one frame, mirroring the exact push/translate/scale(-Z)/rotate/
 * flush/pop pattern vanilla uses to render the rotatable 3D player model inside the otherwise-2D
 * inventory screen ({@code InventoryScreen.renderEntityInInventory}). No manual projection-matrix
 * or scissor manipulation is needed — the screen's existing orthographic GUI projection is
 * reused as-is; the "3D-ness" comes entirely from these {@link PoseStack} transforms. Marker
 * icons are drawn separately, in ordinary 2D, by the caller after this returns (see
 * {@code SolarisMapScreen}) — {@link GuiGraphics#flush()} below is the safe compositing point
 * between the two.
 */
public final class SolarisGlobeRenderer {

    /** Arbitrary Z-depth to translate to before scaling, same role as vanilla's fixed 50.0 in InventoryScreen. */
    private static final float DEPTH = 200f;
    /** Full-bright packed light — same constant already used for the floating waypoint item icons. */
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
