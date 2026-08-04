package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class UnexploredImageStyle {

    private UnexploredImageStyle() {}

    private static DynamicTexture texture;
    private static NativeImage image;
    private static ResourceLocation textureId;
    private static String loadedPath = null;
    private static boolean warnedMissing = false;

    private static void ensureLoaded() {
        String path = SolarisConfig.UNEXPLORED_IMAGE_PATH.get();
        if (path.equals(loadedPath)) return;
        loadedPath = path;

        if (texture != null) {
            texture.close();
            texture = null;
            image = null;
            textureId = null;
        }
        if (path.isBlank()) return;

        Path file = Paths.get("config", "solaris", path);
        try {
            NativeImage loaded = NativeImage.read(Files.newInputStream(file));
            ResourceLocation id = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/unexplored_image");
            DynamicTexture tex = new DynamicTexture(loaded);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            tex.setFilter(false, false);

            texture = tex;
            image = loaded;
            textureId = id;
            warnedMissing = false;
        } catch (IOException e) {
            if (!warnedMissing) {
                warnedMissing = true;
                PhoenixSolaris.LOGGER.warn(
                        "[Solaris] Couldn't load unexplored-style image at {} — falling back to Fog until this " +
                                "resolves (path is relative to config/solaris/).",
                        file, e);
            }
        }
    }

    public static boolean isAvailable() {
        ensureLoaded();
        return image != null;
    }

    public static int getPixel(int worldX, int worldZ) {
        ensureLoaded();
        if (image == null) return 0;
        int x = Math.floorMod(worldX, image.getWidth());
        int z = Math.floorMod(worldZ, image.getHeight());
        return image.getPixelRGBA(x, z);
    }

    public static ResourceLocation textureId() {
        ensureLoaded();
        return textureId;
    }

    public static int width() {
        ensureLoaded();
        return image != null ? image.getWidth() : 0;
    }

    public static int height() {
        ensureLoaded();
        return image != null ? image.getHeight() : 0;
    }
}
