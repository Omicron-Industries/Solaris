package net.phoenixvine.solaris.client.render;

/**
 * Outline shape of the corner minimap — see {@link MinimapStyle} for the size+shape presets
 * cycled together. {@code SQUARE} and {@code CIRCLE} are special-cased in {@link
 * MinimapHudOverlay} (a plain rect needs no clipping at all; a circle has cheap exact math);
 * every other shape is a convex polygon defined here by {@link #vertices()}, clipped/outlined
 * generically via a per-row scanline against those vertices — adding a new polygon shape is
 * just adding a new vertex set, no new drawing code needed.
 */
public enum MinimapShape {

    SQUARE(null),
    CIRCLE(null),
    // Point-up equilateral-ish triangle, apex near the top edge, base near the bottom.
    TRIANGLE(new float[][] { { 0.5f, 0.03f }, { 0.97f, 0.90f }, { 0.03f, 0.90f } }),
    // A square rotated 45 degrees, touching all four edge midpoints.
    DIAMOND(new float[][] { { 0.5f, 0.02f }, { 0.98f, 0.5f }, { 0.5f, 0.98f }, { 0.02f, 0.5f } }),
    // Regular pointy-top hexagon inscribed just inside the box.
    HEXAGON(hexagonVertices());

    /**
     * Normalized (0..1) vertex loop within the minimap's square bounds, in winding order. {@code null} for
     * SQUARE/CIRCLE.
     */
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
