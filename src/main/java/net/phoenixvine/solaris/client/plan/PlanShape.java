package net.phoenixvine.solaris.client.plan;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A planned build shape — draw a footprint on the map, see it as an in-world wireframe outline
 * before placing a single real block, instead of test-building with wool. Plain public fields
 * (matching {@link net.phoenixvine.solaris.client.waypoint.Waypoint}'s persistence style) so
 * Gson round-trips it with zero custom adapters.
 *
 * {@link #points} holds different things per {@link #type}: exactly 2 (opposite corners) for
 * {@code RECTANGLE}, exactly 1 (center — {@link #radius} holds the size) for {@code CIRCLE}, 2+
 * (in drawing order) for {@code LINE}. {@link #baseY}/{@link #height} give every shape a
 * vertical extent — a box/cylinder/extruded wall from {@code baseY} to {@code baseY + height} —
 * rather than a flat outline with no sense of how tall the planned structure actually is.
 */
public class PlanShape {

    public enum Type {
        RECTANGLE,
        CIRCLE,
        LINE
    }

    public String id;
    public String name;
    public String dimension;
    public String color;
    public boolean visible = true;
    public Type type;
    public List<int[]> points = new ArrayList<>();
    public int radius;
    public int baseY;
    public int height = 4;

    public PlanShape() {} // Gson

    public PlanShape(String name, ResourceLocation dimension, Type type, String colorHex) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.dimension = dimension.toString();
        this.type = type;
        this.color = colorHex;
    }

    public ResourceLocation dimensionLocation() {
        return new ResourceLocation(dimension);
    }

    public int colorArgb() {
        return parseHex(color, 0xFFFFFFFF);
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
