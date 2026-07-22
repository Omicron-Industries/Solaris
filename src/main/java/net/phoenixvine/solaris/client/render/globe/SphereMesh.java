package net.phoenixvine.solaris.client.render.globe;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SphereMesh {

    private static final int SEGMENTS = 48;
    private static final int RINGS = 24;

    private static final float RELIEF_RANGE = 96f;

    private static final float RELIEF_SCALE = 0.12f;

    private static final float RIM_DARKEST = 0.4f;

    private float[][] vertices = buildFlat();

    public void rebuild(int[] heights, int sizePixels, int seaLevel) {
        vertices = build(heights, sizePixels, seaLevel);
    }

    public void render(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        Vector3f rotatedNormal = new Vector3f();
        for (float[] v : vertices) {
            rotatedNormal.set(v[3], v[4], v[5]);
            normalMatrix.transform(rotatedNormal);
            float rim = Mth.clamp(rotatedNormal.z(), 0f, 1f);
            int shade = Math.round(255 * (RIM_DARKEST + (1f - RIM_DARKEST) * rim));

            consumer.vertex(positionMatrix, v[0], v[1], v[2])
                    .color(shade, shade, shade, 255)
                    .uv(v[6], v[7])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normalMatrix, v[3], v[4], v[5])
                    .endVertex();
        }
    }

    private static float[][] buildFlat() {
        return build(null, 1, 0);
    }

    private static float[][] build(int[] heights, int sizePixels, int seaLevel) {
        float[][] grid = new float[(RINGS + 1) * (SEGMENTS + 1)][];
        for (int row = 0; row <= RINGS; row++) {
            float v = row / (float) RINGS;
            float theta = v * (float) Math.PI;
            float ny = (float) Math.cos(theta);
            float ringRadius = (float) Math.sin(theta);
            for (int col = 0; col <= SEGMENTS; col++) {
                float u = col / (float) SEGMENTS;
                float phi = u * (float) Math.PI * 2f;
                float nx = ringRadius * (float) Math.cos(phi);
                float nz = ringRadius * (float) Math.sin(phi);

                float radius = 1f + relief(heights, sizePixels, seaLevel, u, v);
                grid[row * (SEGMENTS + 1) + col] = new float[] { nx * radius, ny * radius, nz * radius, nx, ny, nz, u,
                        v };
            }
        }

        List<float[]> triangles = new ArrayList<>();
        for (int row = 0; row < RINGS; row++) {
            for (int col = 0; col < SEGMENTS; col++) {
                float[] topLeft = grid[row * (SEGMENTS + 1) + col];
                float[] topRight = grid[row * (SEGMENTS + 1) + col + 1];
                float[] botLeft = grid[(row + 1) * (SEGMENTS + 1) + col];
                float[] botRight = grid[(row + 1) * (SEGMENTS + 1) + col + 1];

                addTriangle(triangles, topLeft, botLeft, topRight);
                addTriangle(triangles, topRight, botLeft, botRight);
            }
        }
        return triangles.toArray(new float[0][]);
    }

    private static float relief(int[] heights, int sizePixels, int seaLevel, float u, float v) {
        if (heights == null) return 0f;
        int px = Mth.clamp(Math.round(u * sizePixels), 0, sizePixels - 1);
        int pz = Mth.clamp(Math.round(v * sizePixels), 0, sizePixels - 1);
        int height = heights[pz * sizePixels + px];
        float normalized = Mth.clamp((height - seaLevel) / RELIEF_RANGE, -1f, 1f);
        return normalized * RELIEF_SCALE;
    }

    private static void addTriangle(List<float[]> out, float[] a, float[] b, float[] c) {
        float e1x = b[0] - a[0];
        float e1y = b[1] - a[1];
        float e1z = b[2] - a[2];
        float e2x = c[0] - a[0];
        float e2y = c[1] - a[1];
        float e2z = c[2] - a[2];
        float faceX = e1y * e2z - e1z * e2y;
        float faceY = e1z * e2x - e1x * e2z;
        float faceZ = e1x * e2y - e1y * e2x;
        float outX = a[3] + b[3] + c[3];
        float outY = a[4] + b[4] + c[4];
        float outZ = a[5] + b[5] + c[5];
        float dot = faceX * outX + faceY * outY + faceZ * outZ;

        out.add(a);
        if (dot >= 0f) {
            out.add(b);
            out.add(c);
        } else {
            out.add(c);
            out.add(b);
        }
    }
}
