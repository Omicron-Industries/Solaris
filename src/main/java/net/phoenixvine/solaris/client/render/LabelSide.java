package net.phoenixvine.solaris.client.render;

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
