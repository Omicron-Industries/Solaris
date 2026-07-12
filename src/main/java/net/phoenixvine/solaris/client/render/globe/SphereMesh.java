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

/**
 * A lat/long UV-sphere, rebuilt (not necessarily every frame — see {@link #rebuild}) whenever
 * the backing terrain height data changes, since vertex radius is displaced by real world Y —
 * without that, the flat-tinted terrain texture reads as a sticker pasted on a ball rather than
 * actual landmass. Displacement is normalized against a fixed height range and clamped, rather
 * than a raw linear scale off actual Y: an extreme-terrain worldgen mod (very tall mountains)
 * would otherwise turn the globe into a spiky, unstable-looking mess — clamping means even a
 * mountain far taller than vanilla's just maxes out at the same bump as vanilla's tallest peak,
 * instead of scaling further.
 *
 * Texture U wraps longitude (0 at u=0, full turn at u=1), V spans latitude (0 = "north pole" at
 * v=0, 1 = "south pole" at v=1) — matches {@link GlobeCamera}'s inverse mapping so a marker
 * placed via {@code screenToSpherePoint} projects back to the exact screen position {@code
 * sphereToScreen} would draw it at. Note this mapping is independent of displacement: markers
 * still project off the undisplaced unit sphere, since a marker's exact position mattering more
 * than it visually sitting flush with a nearby mountain's bump is the right tradeoff here.
 */
public final class SphereMesh {

    private static final int SEGMENTS = 48;
    private static final int RINGS = 24;

    /** Height delta (blocks) from sea level that reaches full displacement — clamped beyond this either way. */
    private static final float RELIEF_RANGE = 96f;
    /** Max radius perturbation, as a fraction of the unit sphere's radius, at full displacement. */
    private static final float RELIEF_SCALE = 0.12f;

    /** Vertex color darkens down to this fraction of full brightness right at the silhouette edge. */
    private static final float RIM_DARKEST = 0.4f;

    /** Flat list of {px, py, pz, nx, ny, nz, u, v} vertices, three per triangle, already fully expanded. */
    private float[][] vertices = buildFlat();

    /**
     * Rebuilds the mesh with per-vertex radius displaced by {@code heights} (row-major,
     * {@code z*sizePixels+x}, same grid as the terrain texture's pixels) relative to
     * {@code seaLevel}. Cheap enough (a few hundred vertices) to call every frame the globe is
     * visible rather than tracking a separate "did the texture actually change" dirty flag.
     */
    public void rebuild(int[] heights, int sizePixels, int seaLevel) {
        vertices = build(heights, sizePixels, seaLevel);
    }

    /**
     * Emits every precomputed vertex through {@code consumer}, transformed by {@code pose}'s
     * current matrices — mirrors how {@code LevelRenderer.renderLineBox} feeds a live
     * {@link VertexConsumer} from static geometry rather than rebuilding per frame.
     *
     * Also applies a per-vertex rim/fresnel darkening: the vertex normal rotated into the
     * current view by {@code pose}'s normal matrix gives exactly the "facing the camera" vs.
     * "curving away at the edge" distinction a flat-shaded sphere otherwise lacks — its Z
     * component is 1 dead-center and drops to 0 at the silhouette.
     */
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
        // (RINGS+1) x (SEGMENTS+1) grid; each entry is {px, py, pz, nx, ny, nz, u, v}.
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

    /** Sea-level-relative, normalized-and-clamped radius offset for the given UV, or 0 if no height data yet. */
    private static float relief(int[] heights, int sizePixels, int seaLevel, float u, float v) {
        if (heights == null) return 0f;
        int px = Mth.clamp(Math.round(u * sizePixels), 0, sizePixels - 1);
        int pz = Mth.clamp(Math.round(v * sizePixels), 0, sizePixels - 1);
        int height = heights[pz * sizePixels + px];
        float normalized = Mth.clamp((height - seaLevel) / RELIEF_RANGE, -1f, 1f);
        return normalized * RELIEF_SCALE;
    }

    /**
     * Adds a triangle, flipping its winding if needed so the face normal (edge cross product)
     * points outward — away from the sphere's center, same direction as the vertices' own
     * outward normal. A naive UV-space winding choice doesn't reliably stay outward-facing once
     * curved onto a sphere, and {@code RenderType.entityCutout} backface-culls anything that
     * comes out backward, which showed up as an alternating black/textured triangle checkerboard
     * across the globe instead of a solid sphere.
     */
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
