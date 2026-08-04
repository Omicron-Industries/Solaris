package net.phoenixvine.solaris.client.render;

public enum MinimapShape {

    SQUARE(null),
    CIRCLE(null),

    TRIANGLE(new float[][] { { 0.5f, 0.03f }, { 0.97f, 0.90f }, { 0.03f, 0.90f } }),

    DIAMOND(new float[][] { { 0.5f, 0.02f }, { 0.98f, 0.5f }, { 0.5f, 0.98f }, { 0.02f, 0.5f } }),

    HEXAGON(hexagonVertices());

    private final float[][] vertices;

    MinimapShape(float[][] vertices) {
        this.vertices = vertices;
    }

    public float[][] vertices() {
        return vertices;
    }

    public boolean isPolygon() {
        return vertices != null;
    }

    public MinimapShape next() {
        MinimapShape[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String label() {
        return switch (this) {
            case SQUARE -> "Square";
            case CIRCLE -> "Circle";
            case TRIANGLE -> "Triangle";
            case DIAMOND -> "Diamond";
            case HEXAGON -> "Hexagon";
        };
    }

    public float[] rowSpan(float ny) {
        if (this == SQUARE) return new float[] { 0f, 1f };
        if (this == CIRCLE) {
            float dy = ny - 0.5f;
            float r = 0.5f;
            if (Math.abs(dy) > r) return null;
            float halfW = (float) Math.sqrt(r * r - dy * dy);
            return new float[] { 0.5f - halfW, 0.5f + halfW };
        }

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        int n = vertices.length;
        for (int i = 0; i < n; i++) {
            float[] a = vertices[i];
            float[] b = vertices[(i + 1) % n];
            float ay = a[1];
            float by = b[1];
            if ((ay <= ny && by > ny) || (by <= ny && ay > ny)) {
                float t = (ny - ay) / (by - ay);
                float px = a[0] + t * (b[0] - a[0]);
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
            }
        }
        return minX <= maxX ? new float[] { minX, maxX } : null;
    }

    public boolean containsPoint(float nx, float ny) {
        if (this == SQUARE) return true;
        if (this == CIRCLE) {
            float dx = nx - 0.5f;
            float dy = ny - 0.5f;
            return (dx * dx + dy * dy) <= (0.5f * 0.5f);
        }
        float[] span = rowSpan(ny);
        return span != null && nx >= span[0] && nx <= span[1];
    }

    public boolean containsPoint(int lx, int ly, int size) {
        if (this == SQUARE) return true;

        float nx = (lx + 0.5f) / size;
        float ny = (ly + 0.5f) / size;

        if (this == CIRCLE) {
            float dx = nx - 0.5f;
            float dy = ny - 0.5f;
            return (dx * dx + dy * dy) <= (0.5f * 0.5f);
        }

        float[] span = rowSpan(ny);
        if (span == null) return false;

        float inset = 0.5f / size;
        return nx >= (span[0] + inset) && nx <= (span[1] - inset);
    }

    private static float[][] hexagonVertices() {
        float[][] verts = new float[6][2];
        float radius = 0.48f;
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(-90 + i * 60);
            verts[i][0] = 0.5f + radius * (float) Math.cos(angle);
            verts[i][1] = 0.5f + radius * (float) Math.sin(angle);
        }
        return verts;
    }
}
