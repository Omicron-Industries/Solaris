package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.UUID;

public class Waypoint {

    public String id;
    public String name;
    public String dimension;
    public int x;
    public int y;
    public int z;
    public String color;
    public boolean visible = true;
    public String icon = WaypointIcon.DOT.name();

    public String category = "";

    public String labelColor = "";

    public boolean locked = false;

    public Waypoint() {}

    public Waypoint(String name, ResourceLocation dimension, int x, int y, int z, String colorHex) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.dimension = dimension.toString();
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = colorHex;
    }

    public ResourceLocation dimensionLocation() {
        return new ResourceLocation(dimension);
    }

    public String categoryOrEmpty() {
        return category == null ? "" : category;
    }

    public double distanceSq(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public int colorArgb() {
        return parseHex(color, 0xFFFFFFFF);
    }

    public int labelColorArgb(int fallback) {
        return labelColor == null || labelColor.isBlank() ? fallback : parseHex(labelColor, fallback);
    }

    private static int parseHex(String hex, int fallback) {
        try {
            String clean = hex.trim().toUpperCase(Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);
            return 0xFF000000 | ((int) Long.parseLong(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return fallback;
        }
    }
}
