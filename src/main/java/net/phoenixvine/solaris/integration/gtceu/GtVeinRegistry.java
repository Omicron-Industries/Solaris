package net.phoenixvine.solaris.integration.gtceu;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Solaris's own record of GTCEu ore veins actually revealed to the player, fed by
 * {@code GTClientCacheMixin} rather than read live from GTCEu's own
 * {@code GTClientCache.getVeinsInArea}/{@code getVeinsInBounds}. That query is unreliable for
 * freshly-prospected veins: {@code SPacketProspectOre} stores each vein under a raw
 * chunk-coordinate grid key, but area queries divide the query bounds by
 * {@code oreVeinGridSize} (3 by default) before looking a key up — so a lookup essentially
 * never lands on the cell a prospected vein was actually stored under, except right near the
 * world origin. Mirroring every reveal into this flat per-dimension list side-steps that bug
 * entirely instead of trying to replicate GTCEu's (broken) grid math.
 *
 * Also persisted to its own per-world file, same as {@link WaypointManager}: GTCEu's own disk
 * cache reload on rejoin (its {@code readDimFile}) writes straight into its internal grid map
 * rather than going through {@code addVein}, so this mixin-fed registry never sees those veins
 * come back either — without its own persistence, every revealed vein would need reprospecting
 * after every world restart.
 */
public final class GtVeinRegistry {

    private GtVeinRegistry() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<GtceuIntegration.GtOreVein>> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static String loadedWorldKey = null;

    /** On-disk shape — a plain DTO so Gson round-trips it without needing an ItemStack (de)serializer. */
    private static final class SavedVein {

        int x, y, z;
        String name;
        int colorArgb;
        String itemId;
    }

    /**
     * Called from {@code GTClientCacheMixin} for every vein GTCEu's own client cache accepts —
     * i.e. from inside a mixin injection into GTCEu's own code, so this must never let an
     * exception escape (it would surface as GTCEu's {@code addVein} itself crashing).
     */
    public static void add(ResourceKey<Level> dim, GeneratedVeinMetadata vein) {
        try {
            ensureLoaded();
            if (vein.depleted()) return;

            String dimKey = dim.location().toString();
            String seenKey = dimKey + "@" + vein.center();
            if (!SEEN.add(seenKey)) return;

            String name = OreRenderLayer.getName(vein).getString();
            Material material = OreRenderLayer.getMaterial(vein);
            int argb = material.getMaterialARGB();
            ItemStack icon = rawOreIcon(material);
            BY_DIMENSION.computeIfAbsent(dimKey, k -> new CopyOnWriteArrayList<>())
                    .add(new GtceuIntegration.GtOreVein(vein.center(), name, argb, icon));
            save();
        } catch (Throwable ignored) {
            // Best-effort marker data — one bad vein shouldn't break GTCEu's own cache update.
        }
    }

    /**
     * The vein's primary material's raw ore item (e.g. Raw Copper) — that's "the actual ore"
     * for a vein per GTCEu's own convention (it's the same material {@link OreRenderLayer}
     * already treats as the vein's identity for naming/color). Falls back to the ore block
     * item, then to {@link ItemStack#EMPTY}, for exotic materials without a raw ore variant
     * (the caller falls back to a generic marker shape in that case).
     */
    private static ItemStack rawOreIcon(Material material) {
        if (material.isNull()) return ItemStack.EMPTY;
        ItemStack raw = ChemicalHelper.get(TagPrefix.rawOre, material);
        if (!raw.isEmpty()) return raw;
        return ChemicalHelper.get(TagPrefix.ore, material);
    }

    public static List<GtceuIntegration.GtOreVein> getInArea(ResourceKey<Level> dim, int minX, int minZ, int width,
                                                             int depth) {
        ensureLoaded();
        List<GtceuIntegration.GtOreVein> veins = BY_DIMENSION.get(dim.location().toString());
        if (veins == null || veins.isEmpty()) return List.of();

        int maxX = minX + width;
        int maxZ = minZ + depth;
        List<GtceuIntegration.GtOreVein> out = new ArrayList<>();
        for (GtceuIntegration.GtOreVein vein : veins) {
            BlockPos c = vein.center();
            if (c.getX() >= minX && c.getX() <= maxX && c.getZ() >= minZ && c.getZ() <= maxZ) out.add(vein);
        }
        return out;
    }

    private static void ensureLoaded() {
        String key = WaypointManager.currentWorldKey();
        if (key == null || key.equals(loadedWorldKey)) return;

        BY_DIMENSION.clear();
        SEEN.clear();
        try {
            Path file = fileFor(key);
            if (Files.exists(file)) {
                String json = Files.readString(file);
                Type type = new TypeToken<Map<String, List<SavedVein>>>() {}.getType();
                Map<String, List<SavedVein>> data = GSON.fromJson(json, type);
                if (data != null) {
                    for (Map.Entry<String, List<SavedVein>> entry : data.entrySet()) {
                        String dimKey = entry.getKey();
                        List<GtceuIntegration.GtOreVein> list = new CopyOnWriteArrayList<>();
                        for (SavedVein sv : entry.getValue()) {
                            BlockPos pos = new BlockPos(sv.x, sv.y, sv.z);
                            list.add(new GtceuIntegration.GtOreVein(pos, sv.name, sv.colorArgb, itemFromId(sv.itemId)));
                            SEEN.add(dimKey + "@" + pos);
                        }
                        BY_DIMENSION.put(dimKey, list);
                    }
                }
            }
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.error("Failed to load saved GT ore vein markers — starting fresh.", e);
        }
        loadedWorldKey = key;
    }

    private static void save() {
        String key = WaypointManager.currentWorldKey();
        if (key == null) return;
        try {
            Map<String, List<SavedVein>> data = new HashMap<>();
            for (Map.Entry<String, List<GtceuIntegration.GtOreVein>> entry : BY_DIMENSION.entrySet()) {
                List<SavedVein> list = new ArrayList<>();
                for (GtceuIntegration.GtOreVein vein : entry.getValue()) {
                    SavedVein sv = new SavedVein();
                    sv.x = vein.center().getX();
                    sv.y = vein.center().getY();
                    sv.z = vein.center().getZ();
                    sv.name = vein.name();
                    sv.colorArgb = vein.colorArgb();
                    sv.itemId = idFromItem(vein.icon());
                    list.add(sv);
                }
                data.put(entry.getKey(), list);
            }
            Path file = fileFor(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data));
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.error("Failed to save GT ore vein markers.", e);
        }
    }

    private static Path fileFor(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get("config", "phoenix_solaris", "gt_veins", safe + ".json");
    }

    private static String idFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl == null ? null : rl.toString();
    }

    private static ItemStack itemFromId(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
