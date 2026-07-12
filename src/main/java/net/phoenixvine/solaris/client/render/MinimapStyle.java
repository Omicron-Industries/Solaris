package net.phoenixvine.solaris.client.render;

/**
 * A curated size+shape preset for the corner minimap, cycled through with a single keybind
 * (mirrors JourneyMap's minimap-type cycle key) rather than exposing size and shape as two
 * separate settings a player has to dig through a menu for. Each preset sets both {@code
 * SolarisConfig.MINIMAP_SIZE} and {@code MINIMAP_SHAPE} together.
 */
public enum MinimapStyle {

    SMALL_SQUARE(96, MinimapShape.SQUARE, "Small Square"),
    MEDIUM_SQUARE(128, MinimapShape.SQUARE, "Medium Square"),
    LARGE_SQUARE(176, MinimapShape.SQUARE, "Large Square"),
    SMALL_CIRCLE(96, MinimapShape.CIRCLE, "Small Circle"),
    MEDIUM_CIRCLE(128, MinimapShape.CIRCLE, "Medium Circle"),
    LARGE_CIRCLE(176, MinimapShape.CIRCLE, "Large Circle"),
    // One size each for the novel shapes — a full small/medium/large spread per shape would make
    // a single cycle key step through 15 presets; these are for variety, not fine size tuning
    // (Square/Circle already cover that), so one representative size keeps the cycle short.
    TRIANGLE(128, MinimapShape.TRIANGLE, "Triangle"),
    DIAMOND(128, MinimapShape.DIAMOND, "Diamond"),
    HEXAGON(128, MinimapShape.HEXAGON, "Hexagon");

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

    /**
     * Closest matching preset to a size/shape pair loaded from config, for the first cycle after those settings change.
     */
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
