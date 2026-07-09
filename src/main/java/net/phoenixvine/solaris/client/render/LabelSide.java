package net.phoenixvine.solaris.client.render;

/**
 * Where a marker's name label draws relative to its icon on the fullscreen map. Applies to
 * both waypoint and GT ore vein labels — with enough markers on screen at once, a label
 * fixed to one side can run off the map's edge or overlap a neighboring icon, so letting the
 * player pick a side that fits their layout beats guessing one automatically.
 */
public enum LabelSide {

    RIGHT,
    LEFT,
    TOP,
    BOTTOM;

    public LabelSide next() {
        LabelSide[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String label() {
        return switch (this) {
            case RIGHT -> "Right";
            case LEFT -> "Left";
            case TOP -> "Top";
            case BOTTOM -> "Bottom";
        };
    }

    public int drawX(int cx, int iconR, int textWidth) {
        return switch (this) {
            case RIGHT -> cx + iconR + 3;
            case LEFT -> cx - iconR - 3 - textWidth;
            case TOP, BOTTOM -> cx - textWidth / 2;
        };
    }

    public int drawY(int cy, int iconR, int textHeight) {
        return switch (this) {
            case RIGHT, LEFT -> cy - textHeight / 2;
            case TOP -> cy - iconR - 3 - textHeight;
            case BOTTOM -> cy + iconR + 3;
        };
    }
}
