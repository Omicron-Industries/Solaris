package net.phoenixvine.solaris.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Map;

/**
 * A real, static crop of a mob's own texture as its map icon — the actual face, not a spawn egg
 * or a live-rendered 3D bust (two earlier attempts, both replaced per feedback: eggs don't say
 * anything about the specific mob, and a live entity render looked "alive"/animated rather than
 * a fixed icon like the player marker), and not just a flat hostile/passive color square either —
 * that was the fallback for anything not in this table, which for most overworld mobs (cows,
 * pigs, sheep, ...) was effectively always, since only the original humanoid-family set was
 * covered.
 *
 * Every entry's crop rectangle is verified against the mob's actual decompiled model class, not
 * guessed — each vanilla mob body model defines its head as {@code CubeListBuilder.create()
 * .texOffs(u, v).addBox(x, y, z, width, height, depth)}, and Minecraft's standard cube UV unwrap
 * places a box's front face at {@code (u + depth, v + depth)}, sized {@code width x height} — the
 * same formula that makes the well-known player/zombie head convention ({@code texOffs(0,0)},
 * an 8x8x8 box, front face at (8,8) sized 8x8) just one instance of a general rule, not a special
 * case. Getting this wrong crops the wrong region and looks broken, not just imprecise, so every
 * entry here was computed from a real {@code texOffs}/{@code addBox} call, then cross-checked
 * against the model's {@code LayerDefinition.create(meshdefinition, texW, texH)} for the texture
 * dimensions.
 *
 * {@link MobIconOverrides} is checked first — lets a specific mob's icon be swapped to a custom
 * texture/crop without touching this table, for whenever curation continues past this initial
 * batch (a modded mob, or simply a hand-picked crop that reads better than the mechanical one).
 */
public final class MobFaceIcons {

    /** {@code (u, v, width, height, texWidth, texHeight)} — a verified front-face crop rectangle. */
    private record Crop(int u, int v, int width, int height, int texWidth, int texHeight) {}

    private static final Map<EntityType<?>, Crop> CROPS = Map.ofEntries(
            // Humanoid family — the classic head-cube convention (texOffs(0,0), 8x8x8 box), texture
            // height is the one thing that varies across this family.
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
            // Creeper/Enderman's head models happen to use the exact same 8x8x8-at-(0,0) box as the
            // humanoid family — verified against CreeperModel/EndermanModel, not assumed just
            // because the numbers matched.
            Map.entry(EntityType.CREEPER, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.ENDERMAN, new Crop(8, 8, 8, 8, 64, 32)),
            // Quadrupeds/other non-humanoid mobs — each a genuinely different head box, verified
            // per-entry against CowModel/PigModel/SheepModel/ChickenModel/SpiderModel/WolfModel.
            Map.entry(EntityType.COW, new Crop(6, 6, 8, 8, 64, 32)),
            // MushroomCowRenderer bakes a CowModel<MushroomCow> — the exact same head geometry.
            Map.entry(EntityType.MOOSHROOM, new Crop(6, 6, 8, 8, 64, 32)),
            Map.entry(EntityType.PIG, new Crop(8, 8, 8, 8, 64, 32)),
            Map.entry(EntityType.SHEEP, new Crop(8, 8, 6, 6, 64, 32)),
            Map.entry(EntityType.CHICKEN, new Crop(3, 3, 4, 6, 64, 32)),
            Map.entry(EntityType.SPIDER, new Crop(40, 12, 8, 8, 64, 32)),
            // CaveSpiderRenderer bakes ModelLayers.CAVE_SPIDER, which LayerDefinitions registers to
            // the identical LayerDefinition instance as ModelLayers.SPIDER — same crop, just
            // rendered at a smaller in-world scale (irrelevant to a fixed-size map icon).
            Map.entry(EntityType.CAVE_SPIDER, new Crop(40, 12, 8, 8, 64, 32)),
            Map.entry(EntityType.WOLF, new Crop(4, 4, 6, 6, 64, 32)));

    private MobFaceIcons() {}

    /** Whether {@code type} has either a built-in verified crop or a registered {@link MobIconOverrides} entry. */
    public static boolean isSupported(EntityType<?> type) {
        return MobIconOverrides.get(type) != null || CROPS.containsKey(type);
    }

    /**
     * Draws the mob's face crop centered on ({@code cx}, {@code cy}); returns {@code false} (draws nothing) if
     * unsupported.
     */
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
