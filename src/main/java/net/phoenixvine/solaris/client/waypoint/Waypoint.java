package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.UUID;

/**
 * A named point on the map. Plain public fields (matching {@code SolarisTheme}'s
 * persistence style) so Gson round-trips it with zero custom adapters — dimension and id
 * are stored as strings rather than {@code ResourceLocation}/{@code UUID} for the same
 * reason: predictable, boring JSON.
 */
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
    /**
     * Freeform grouping label — "" means uncategorized. Never null after construction, but
     * Gson can leave it null on an older waypoints file, so treat null the same as "".
     */
    public String category = "";

    public Waypoint() {} // Gson

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

    /** {@link #category}, but null-safe for waypoints saved before that field existed. */
    public String categoryOrEmpty() {
        return category == null ? "" : category;
    }

    /** Straight-line distance from a position — meaningless across dimensions, callers should check that first. */
    public double distanceSq(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public int colorArgb() {
        try {
            String clean = color.trim().toUpperCase(Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);
            return 0xFF000000 | ((int) Long.parseLong(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }
}
