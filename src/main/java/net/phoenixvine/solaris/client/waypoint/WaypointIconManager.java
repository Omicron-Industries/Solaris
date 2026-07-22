package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.solaris.PhoenixSolaris;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WaypointIconManager {

    public static final String CUSTOM_PREFIX = "custom:";
    public static final String ITEM_PREFIX = "item:";
    private static final Path DIR = Paths.get("config", "solaris", "waypoint_icons");

    private record Loaded(ResourceLocation location, int width, int height) {}

    private static final Map<String, Loaded> CUSTOM = new LinkedHashMap<>();
    private static boolean scanned = false;

    private WaypointIconManager() {}

    public static boolean isCustom(String iconId) {
        return iconId != null && iconId.startsWith(CUSTOM_PREFIX);
    }

    public static boolean isItem(String iconId) {
        return iconId != null && iconId.startsWith(ITEM_PREFIX);
    }

    public static String itemIconId(Item item) {
        return ITEM_PREFIX + ForgeRegistries.ITEMS.getKey(item);
    }

    public static ItemStack resolveItem(String iconId) {
        try {
            ResourceLocation id = new ResourceLocation(iconId.substring(ITEM_PREFIX.length()));
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static List<String> allIds() {
        ensureScanned();
        List<String> out = new ArrayList<>();
        for (WaypointIcon icon : WaypointIcon.values()) out.add(icon.name());
        out.addAll(CUSTOM.keySet());
        return out;
    }

    public static void rescan() {
        scanned = false;
        ensureScanned();
    }

    private static void ensureScanned() {
        if (scanned) return;
        scanned = true;
        if (!Files.isDirectory(DIR)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(DIR, "*.png")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String baseName = fileName.substring(0, fileName.length() - 4);
                load(baseName, path);
            }
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.error("Failed to scan waypoint icon folder {}", DIR, e);
        }
    }

    private static void load(String baseName, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(image);
            String safe = baseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            ResourceLocation loc = new ResourceLocation(PhoenixSolaris.MOD_ID, "waypoint_icon/" + safe);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            CUSTOM.put(CUSTOM_PREFIX + baseName, new Loaded(loc, image.getWidth(), image.getHeight()));
        } catch (IOException e) {
            PhoenixSolaris.LOGGER.error("Failed to load custom waypoint icon {}", file, e);
        }
    }

    public static void draw(GuiGraphics g, String iconId, int cx, int cy, int radius, int colorArgb) {
        ensureScanned();
        if (isItem(iconId)) {
            ItemStack stack = resolveItem(iconId);
            if (!stack.isEmpty()) {
                WaypointIcon.drawItem(g, cx, cy, radius, stack);
                return;
            }

        }
        Loaded custom = CUSTOM.get(iconId);
        if (custom != null) {
            int size = radius * 2;
            g.blit(custom.location(), cx - radius, cy - radius, size, size, 0, 0, custom.width(), custom.height(),
                    custom.width(), custom.height());
            return;
        }
        WaypointIcon.fromId(iconId).draw(g, cx, cy, radius, colorArgb);
    }

    public static String label(String iconId) {
        ensureScanned();
        if (isItem(iconId)) {
            ItemStack stack = resolveItem(iconId);
            return stack.isEmpty() ? "Unknown Item" : stack.getHoverName().getString();
        }
        if (isCustom(iconId)) return CUSTOM.containsKey(iconId) ? iconId.substring(CUSTOM_PREFIX.length()) : iconId;
        return WaypointIcon.fromId(iconId).label();
    }

    public static String next(String iconId) {
        List<String> all = allIds();
        int idx = all.indexOf(iconId);
        return all.get((idx + 1) % all.size());
    }
}
