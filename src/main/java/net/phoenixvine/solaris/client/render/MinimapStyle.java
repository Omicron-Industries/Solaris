package net.phoenixvine.solaris.client.render;

public enum MinimapStyle {

    SMALL_SQUARE(96, MinimapShape.SQUARE, "Small Square"),
    MEDIUM_SQUARE(128, MinimapShape.SQUARE, "Medium Square"),
    LARGE_SQUARE(176, MinimapShape.SQUARE, "Large Square"),
    SMALL_CIRCLE(96, MinimapShape.CIRCLE, "Small Circle"),
    MEDIUM_CIRCLE(128, MinimapShape.CIRCLE, "Medium Circle"),
    LARGE_CIRCLE(176, MinimapShape.CIRCLE, "Large Circle"),
    SMALL_TRIANGLE(96, MinimapShape.TRIANGLE, "Small Triangle"),
    MEDIUM_TRIANGLE(128, MinimapShape.TRIANGLE, "Medium Triangle"),
    LARGE_TRIANGLE(176, MinimapShape.TRIANGLE, "Large Triangle"),
    SMALL_DIAMOND(96, MinimapShape.DIAMOND, "Small Diamond"),
    MEDIUM_DIAMOND(128, MinimapShape.DIAMOND, "Medium Diamond"),
    LARGE_DIAMOND(176, MinimapShape.DIAMOND, "Large Diamond"),
    SMALL_HEXAGON(96, MinimapShape.HEXAGON, "Small Hexagon"),
    MEDIUM_HEXAGON(128, MinimapShape.HEXAGON, "Medium Hexagon"),
    LARGE_HEXAGON(176, MinimapShape.HEXAGON, "Large Hexagon");

    public final int size;
    public final MinimapShape shape;
    public final String label;

    MinimapStyle(int size, MinimapShape shape, String label) {
        this.size = size;
        this.shape = shape;
        this.label = label;
    }

    public MinimapStyle next() {
        MinimapStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static MinimapStyle closestTo(int size, MinimapShape shape) {
        MinimapStyle best = SMALL_SQUARE;
        int bestDiff = Integer.MAX_VALUE;
        for (MinimapStyle style : values()) {
            if (style.shape != shape) continue;
            int diff = Math.abs(style.size - size);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = style;
            }
        }
        return best;
    }
}
