package net.phoenixvine.solaris.client.plan;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

    public PlanShape() {}

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
