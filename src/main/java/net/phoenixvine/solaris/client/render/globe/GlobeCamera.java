package net.phoenixvine.solaris.client.render.globe;

import net.minecraft.util.Mth;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GlobeCamera {

    private static final float ROTATE_SPEED = 0.5f;
    private static final float ZOOM_STEP_FACTOR = 1.1f;

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

    public SpherePoint screenToSpherePoint(double mx, double my, int cx, int cy) {
        float localX = (float) ((mx - cx) / scale);
        float localY = -(float) ((my - cy) / scale);
        if (localX * localX + localY * localY > 1f) return null;

        Vector3f rayOrigin = new Vector3f(localX, localY, 2f);
        Vector3f rayDir = new Vector3f(0f, 0f, -1f);

        Quaternionf inverse = rotationQuaternion().conjugate();
        inverse.transform(rayOrigin);
        inverse.transform(rayDir);

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
