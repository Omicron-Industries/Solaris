package net.phoenixvine.solaris.client.render;

public enum UnexploredStyle {

    FOG,
    STARFIELD,
    PHOENIX,
    CLOUD,
    IMAGE;

    public UnexploredStyle next() {
        UnexploredStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String label() {
        return switch (this) {
            case FOG -> "Fog";
            case STARFIELD -> "Starfield";
            case PHOENIX -> "Phoenix";
            case CLOUD -> "Cloud";
            case IMAGE -> "Image";
        };
    }
}
