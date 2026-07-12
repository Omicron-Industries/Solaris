package net.phoenixvine.solaris.client.render;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-mob custom map-icon registry, checked before {@link MobFaceIcons}'s own built-in crop
 * table. Empty by default — this exists purely as an extension point so a specific mob's icon
 * can later be swapped to a hand-picked texture/crop (a modded mob {@link MobFaceIcons} doesn't
 * know about, or simply a better-looking crop than the mechanical head-cube formula produces)
 * without needing to touch {@code MobFaceIcons} itself, the same relationship
 * {@link net.phoenixvine.solaris.client.color.BlockColorOverrides} has to the block-texture
 * -average color system.
 */
public final class MobIconOverrides {

    /**
     * A fixed crop rectangle from a specific texture — unlike {@link MobFaceIcons}'s table, the texture itself is part
     * of the override.
     */
    public record IconSource(ResourceLocation texture, int u, int v, int width, int height, int texWidth,
                             int texHeight) {}

    private static final Map<EntityType<?>, IconSource> OVERRIDES = new ConcurrentHashMap<>();

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
}
