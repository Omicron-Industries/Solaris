package net.phoenixvine.solaris.client.render;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.solaris.PhoenixSolaris;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MobIconOverrides {

    public record IconSource(ResourceLocation texture, int u, int v, int width, int height, int texWidth,
                             int texHeight) {}

    private static final class ConfigEntry {

        String texture;
        int u;
        int v;
        int width;
        int height;
        int texWidth;
        int texHeight;
    }

    private static final Map<EntityType<?>, IconSource> OVERRIDES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Paths.get("config", "solaris_mob_icons.json");

    private MobIconOverrides() {}

    public static IconSource get(EntityType<?> type) {
        return OVERRIDES.get(type);
    }

    public static void put(EntityType<?> type, IconSource source) {
        OVERRIDES.put(type, source);
    }

    public static void remove(EntityType<?> type) {
        OVERRIDES.remove(type);
    }

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                writeTemplate();
                return;
            }
            String json = Files.readString(CONFIG_FILE);
            Type type = new TypeToken<Map<String, JsonElement>>() {}.getType();
            Map<String, JsonElement> parsed = GSON.fromJson(json, type);
            if (parsed == null) return;

            int loaded = 0;
            for (Map.Entry<String, JsonElement> e : parsed.entrySet()) {
                String entityId = e.getKey();
                if (entityId.startsWith("_")) continue;

                JsonElement element = e.getValue();
                if (element == null || !element.isJsonObject()) {
                    PhoenixSolaris.LOGGER.warn(
                            "solaris_mob_icons.json: entry '{}' isn't a JSON object, skipping", entityId);
                    continue;
                }
                ConfigEntry entry = GSON.fromJson(element, ConfigEntry.class);

                if (entry.texture == null || entry.texture.isBlank()) {
                    PhoenixSolaris.LOGGER.warn(
                            "solaris_mob_icons.json: entry '{}' is missing its texture, skipping", entityId);
                    continue;
                }

                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
                if (entityType == null) {
                    PhoenixSolaris.LOGGER.warn(
                            "solaris_mob_icons.json: '{}' isn't a known entity id (check for a typo), skipping",
                            entityId);
                    continue;
                }

                put(entityType, new IconSource(new ResourceLocation(entry.texture), entry.u, entry.v, entry.width,
                        entry.height, entry.texWidth, entry.texHeight));
                loaded++;
            }
            if (loaded > 0) {
                PhoenixSolaris.LOGGER.info("Loaded {} custom mob icon(s) from solaris_mob_icons.json", loaded);
            }
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.warn(
                    "Couldn't load solaris_mob_icons.json — custom mob icons from that file won't apply this session",
                    e);
        }
    }

    private static void writeTemplate() throws IOException {
        Files.createDirectories(CONFIG_FILE.getParent());
        String template = """
                {
                  "_comment": "Custom map icons for specific mobs, no Java required. Each real entry's key is the entity's own id (e.g. minecraft:bat, or somemod:custom_mob for a modded one), mapped to a crop rectangle from any texture. u/v is the crop's top-left corner in pixels; width/height is the crop's own size; texWidth/texHeight is the FULL texture's size (needed to convert pixel coordinates into GPU texture-coordinate space). Delete the two _example entries below (their leading underscore means they're parsed but ignored) and add your own real entries in the same shape. Restart the game (or reopen the world) to pick up changes.",
                  "_exampleVanillaEntity": {
                    "texture": "minecraft:textures/entity/bat.png",
                    "u": 6,
                    "v": 6,
                    "width": 6,
                    "height": 6,
                    "texWidth": 64,
                    "texHeight": 64
                  },
                  "_exampleModdedEntity": {
                    "texture": "somemod:textures/entity/custom_mob.png",
                    "u": 0,
                    "v": 0,
                    "width": 16,
                    "height": 16,
                    "texWidth": 64,
                    "texHeight": 64
                  }
                }
                """;
        Files.writeString(CONFIG_FILE, template);
    }
}
