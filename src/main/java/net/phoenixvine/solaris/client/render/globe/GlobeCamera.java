package net.phoenixvine.solaris.client.render.globe;

import net.minecraft.util.Mth;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rotation (accumulated from drag deltas, not vanilla's mouse-follow behavior — a globe needs
 * to hold still between frames like {@link net.phoenixvine.solaris.client.render.MapViewport}'s
 * offset does, not continuously track the live cursor position) plus a zoom/scale value (the
 * sphere's on-screen pixel radius, clamped the same way {@code MapViewport.raiseZoomMin} clamps
 * flat zoom).
 *
 * The render pass is orthographic (no perspective divide — see {@code SolarisGlobeRenderer}),
 * so projecting a rotated unit-sphere point to screen space is just a scale-and-offset, and
 * inverse-projecting a click is a ray-sphere intersection with a ray direction fixed along the
 * view axis rather than converging from a camera point.
 */
public final class GlobeCamera {

    private static final float ROTATE_SPEED = 0.5f;
    private static final float ZOOM_STEP_FACTOR = 1.1f;
    // Was 89 — a leftover FPS-camera-style habit that doesn't actually apply here: this is a
    // yaw-then-pitch Euler pair with no third (roll) axis, so there's no gimbal lock to guard
    // against by capping short of vertical. 180 lets the globe tip all the way over (view it
    // from directly above a pole, keep going past it) instead of hard-stopping just shy of 90.
    private static final float MAX_PITCH = 180f;

    private final float minScale;
    private final float maxScale;

    private float yawDegrees;
    private float pitchDegrees;
    private float scale;

    public GlobeCamera(float minScale, float maxScale) {
        this.minScale = minScale;
        this.maxScale = maxScale;
        this.scale = (minScale + maxScale) / 2f;
    }

    public void rotate(double dx, double dy) {
        yawDegrees += (float) dx * ROTATE_SPEED;
        pitchDegrees = Mth.clamp(pitchDegrees - (float) dy * ROTATE_SPEED, -MAX_PITCH, MAX_PITCH);
    }

    public void adjustZoom(double scrollDelta) {
        float factor = scrollDelta > 0 ? ZOOM_STEP_FACTOR : 1f / ZOOM_STEP_FACTOR;
        scale = Mth.clamp(scale * factor, minScale, maxScale);
    }

    public float getScale() {
        return scale;
    }

    public Quaternionf rotationQuaternion() {
        return new Quaternionf().rotateY((float) Math.toRadians(yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees));
    }

    /** Longitude/latitude (u in [0,1) wraps 0..2*PI, v in [0,1] spans pole to pole) to a unit-sphere point. */
    private static Vector3f unitSpherePoint(float u, float v) {
        float theta = v * (float) Math.PI;
        float phi = u * (float) Math.PI * 2f;
        float sinTheta = (float) Math.sin(theta);
        return new Vector3f(sinTheta * (float) Math.cos(phi), (float) Math.cos(theta),
                sinTheta * (float) Math.sin(phi));
    }

    public static final class Projection {

        public final int screenX;
        public final int screenY;
        public final boolean frontFacing;

        Projection(int screenX, int screenY, boolean frontFacing) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.frontFacing = frontFacing;
        }
    }

    /**
     * Forward-projects a texture UV to screen space plus a front/back-facing flag for occlusion
     * culling. Ortho projection means no perspective divide — a rotated unit-sphere point's own
     * X/Y (scaled) IS the screen offset from the sphere's center.
     *
     * A point's local Z sign after rotation determines visibility: the render pass scales world
     * Z by {@code -scale} (see {@code SolarisGlobeRenderer}), so a local point with positive Z
     * ends up closer to the camera (smaller depth) than one with negative Z — positive rotated Z
     * is therefore the near, visible hemisphere.
     */
    public Projection sphereToScreen(float u, float v, int cx, int cy) {
        Vector3f p = unitSpherePoint(u, v);
        rotationQuaternion().transform(p);
        int sx = cx + Math.round(p.x() * scale);
        int sy = cy - Math.round(p.y() * scale);
        return new Projection(sx, sy, p.z() > 0f);
    }

    public static final class SpherePoint {

        public final float u;
        public final float v;

        SpherePoint(float u, float v) {
            this.u = u;
            this.v = v;
        }
    }

    /**
     * Inverse of {@link #sphereToScreen}: given a clicked screen position, finds the UV of the
     * nearest point on the visible sphere surface under the cursor, or {@code null} if the click
     * missed the sphere entirely.
     *
     * The click ray in ortho space is parallel to the view axis (not converging from a camera
     * point): local ray origin is the clicked point pulled back along +Z, direction is -Z, both
     * expressed in sphere-local (pre-rotation) space by applying the INVERSE of the current
     * rotation to the ray built in view space.
     */
    public SpherePoint screenToSpherePoint(double mx, double my, int cx, int cy) {
        float localX = (float) ((mx - cx) / scale);
        float localY = -(float) ((my - cy) / scale);
        if (localX * localX + localY * localY > 1f) return null;

        Vector3f rayOrigin = new Vector3f(localX, localY, 2f);
        Vector3f rayDir = new Vector3f(0f, 0f, -1f);

        Quaternionf inverse = rotationQuaternion().conjugate();
        inverse.transform(rayOrigin);
        inverse.transform(rayDir);

        // Ray-sphere intersection against the unit sphere at the origin: |rayOrigin + t*rayDir| = 1.
        float b = 2f * rayOrigin.dot(rayDir);
        float c = rayOrigin.lengthSquared() - 1f;
        float discriminant = b * b - 4f * c;
        if (discriminant < 0f) return null;

        float t = (-b - (float) Math.sqrt(discriminant)) / 2f;
        Vector3f hit = new Vector3f(rayDir).mul(t).add(rayOrigin);

        float v = (float) (Math.acos(Mth.clamp(hit.y(), -1f, 1f)) / Math.PI);
        float phi = (float) Math.atan2(hit.z(), hit.x());
        float u = (float) (phi / (Math.PI * 2f));
        if (u < 0f) u += 1f;
        return new SpherePoint(u, v);
    }
}
