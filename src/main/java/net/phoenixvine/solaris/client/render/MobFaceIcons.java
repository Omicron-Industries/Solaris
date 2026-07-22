package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Map;

public final class MobFaceIcons {

    private record Crop(int u, int v, int width, int height, int texWidth, int texHeight) {}

    private static final Map<EntityType<?>, Crop> CROPS = Map.ofEntries(

            Map.entry(EntityType.ZOMBIE, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.HUSK, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.DROWNED, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.ZOMBIE_VILLAGER, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.SKELETON, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.STRAY, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.WITHER_SKELETON, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.VILLAGER, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.WANDERING_TRADER, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.VINDICATOR, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.PILLAGER, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.EVOKER, new Crop(8, 8, 8, 8, 64, 64)),
            Map.entry(EntityType.WITCH, new Crop(8, 8, 8, 8, 64, 128)),

            Map.entry(EntityType.CREEPER, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.ENDERMAN, new Crop(8, 8, 8, 8, 64, 32)),

            Map.entry(EntityType.COW, new Crop(6, 6, 8, 8, 64, 32)),

            Map.entry(EntityType.MOOSHROOM, new Crop(6, 6, 8, 8, 64, 32)),
            Map.entry(EntityType.PIG, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.SHEEP, new Crop(8, 8, 6, 6, 64, 32)),
            Map.entry(EntityType.CHICKEN, new Crop(3, 3, 4, 6, 64, 32)),
            Map.entry(EntityType.SPIDER, new Crop(40, 12, 8, 8, 64, 32)),

            Map.entry(EntityType.CAVE_SPIDER, new Crop(40, 12, 8, 8, 64, 32)),
            Map.entry(EntityType.WOLF, new Crop(4, 4, 6, 6, 64, 32)),

            Map.entry(EntityType.HORSE, new Crop(7, 20, 6, 5, 64, 64)),
            Map.entry(EntityType.SKELETON_HORSE, new Crop(7, 20, 6, 5, 64, 64)),
            Map.entry(EntityType.ZOMBIE_HORSE, new Crop(7, 20, 6, 5, 64, 64)),

            Map.entry(EntityType.DONKEY, new Crop(7, 20, 6, 5, 64, 64)),
            Map.entry(EntityType.MULE, new Crop(7, 20, 6, 5, 64, 64)),

            Map.entry(EntityType.LLAMA, new Crop(9, 9, 4, 4, 128, 64)),
            Map.entry(EntityType.TRADER_LLAMA, new Crop(9, 9, 4, 4, 128, 64)),

            Map.entry(EntityType.RABBIT, new Crop(37, 5, 5, 4, 64, 32)),

            Map.entry(EntityType.CAT, new Crop(5, 5, 5, 4, 64, 32)),
            Map.entry(EntityType.OCELOT, new Crop(5, 5, 5, 4, 64, 32)),
            Map.entry(EntityType.FOX, new Crop(7, 11, 8, 6, 48, 32)),
            Map.entry(EntityType.TURTLE, new Crop(9, 6, 6, 5, 128, 64)),

            Map.entry(EntityType.PANDA, new Crop(9, 15, 13, 10, 64, 64)),
            Map.entry(EntityType.POLAR_BEAR, new Crop(7, 7, 7, 7, 128, 64)),
            Map.entry(EntityType.PARROT, new Crop(4, 4, 2, 3, 32, 32)),

            Map.entry(EntityType.FROG, new Crop(9, 22, 7, 3, 48, 48)),
            Map.entry(EntityType.AXOLOTL, new Crop(5, 6, 8, 5, 64, 64)),
            Map.entry(EntityType.ALLAY, new Crop(5, 5, 5, 5, 32, 32)),

            Map.entry(EntityType.BAT, new Crop(6, 6, 6, 6, 64, 64)),

            Map.entry(EntityType.BEE, new Crop(10, 10, 7, 7, 64, 64)),

            Map.entry(EntityType.CAMEL, new Crop(28, 7, 7, 14, 128, 128)),

            Map.entry(EntityType.SNIFFER, new Crop(19, 26, 13, 18, 192, 192)),

            Map.entry(EntityType.SQUID, new Crop(12, 12, 12, 16, 64, 32)),
            Map.entry(EntityType.GLOW_SQUID, new Crop(12, 12, 12, 16, 64, 32)),
            Map.entry(EntityType.DOLPHIN, new Crop(6, 6, 8, 7, 64, 64)),
            Map.entry(EntityType.COD, new Crop(14, 3, 2, 4, 32, 32)),
            Map.entry(EntityType.SALMON, new Crop(25, 3, 2, 4, 32, 32)),

            Map.entry(EntityType.PUFFERFISH, new Crop(3, 30, 3, 2, 32, 32)),

            Map.entry(EntityType.TROPICAL_FISH, new Crop(6, 6, 2, 3, 32, 32)),

            Map.entry(EntityType.STRIDER, new Crop(16, 16, 16, 14, 64, 128)),

            Map.entry(EntityType.PIGLIN, new Crop(8, 8, 10, 8, 64, 64)),
            Map.entry(EntityType.PIGLIN_BRUTE, new Crop(8, 8, 10, 8, 64, 64)),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, new Crop(8, 8, 10, 8, 64, 64)),

            Map.entry(EntityType.HOGLIN, new Crop(80, 20, 14, 6, 128, 64)),
            Map.entry(EntityType.ZOGLIN, new Crop(80, 20, 14, 6, 128, 64)),

            Map.entry(EntityType.GHAST, new Crop(16, 16, 16, 16, 64, 32)),
            Map.entry(EntityType.BLAZE, new Crop(8, 8, 8, 8, 64, 32)),

            Map.entry(EntityType.MAGMA_CUBE, new Crop(4, 20, 4, 4, 64, 32)),

            Map.entry(EntityType.SLIME, new Crop(6, 22, 6, 6, 64, 32)),

            Map.entry(EntityType.SHULKER, new Crop(6, 58, 6, 6, 64, 64)),

            Map.entry(EntityType.GUARDIAN, new Crop(16, 16, 12, 12, 64, 64)),
            Map.entry(EntityType.ELDER_GUARDIAN, new Crop(16, 16, 12, 12, 64, 64)),

            Map.entry(EntityType.SILVERFISH, new Crop(2, 2, 3, 2, 64, 32)),
            Map.entry(EntityType.ENDERMITE, new Crop(2, 2, 4, 3, 64, 32)),

            Map.entry(EntityType.PHANTOM, new Crop(9, 17, 5, 3, 64, 64)),
            Map.entry(EntityType.VEX, new Crop(5, 5, 5, 5, 32, 32)),

            Map.entry(EntityType.RAVAGER, new Crop(16, 16, 16, 20, 128, 128)),
            Map.entry(EntityType.IRON_GOLEM, new Crop(8, 8, 8, 10, 128, 128)),
            Map.entry(EntityType.SNOW_GOLEM, new Crop(8, 8, 8, 8, 64, 64)));

    private MobFaceIcons() {}

    public static boolean isSupported(EntityType<?> type) {
        return MobIconOverrides.get(type) != null || CROPS.containsKey(type);
    }

    public static boolean draw(GuiGraphics g, Mob mob, int cx, int cy, int size) {
        MobIconOverrides.IconSource override = MobIconOverrides.get(mob.getType());
        if (override != null) {
            g.blit(override.texture(), cx - size / 2, cy - size / 2, size, size, override.u(), override.v(),
                    override.width(), override.height(), override.texWidth(), override.texHeight());
            return true;
        }

        Crop crop = CROPS.get(mob.getType());
        if (crop == null) return false;

        ResourceLocation texture = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob)
                .getTextureLocation(mob);
        g.blit(texture, cx - size / 2, cy - size / 2, size, size, crop.u(), crop.v(), crop.width(), crop.height(),
                crop.texWidth(), crop.texHeight());
        return true;
    }
}
