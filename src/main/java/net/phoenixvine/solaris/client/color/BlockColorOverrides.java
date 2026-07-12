package net.phoenixvine.solaris.client.color;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Solaris-owned block-to-color table, checked before falling back to vanilla's
 * {@code MapColor} in {@link ChunkColorSampler}. Vanilla's map palette is a ~13-year-old,
 * nearly-full (189 of 255 usable slots) fixed system that predates most of the blocks it now
 * has to represent — individual blocks were bucketed into one of a handful of base colors once,
 * years ago, and never revisited even as new, visually distinct blocks were added to the same
 * bucket. The clearest example: every flower shares the same flat {@code MapColor.PLANT} green
 * regardless of the actual dye color it produces, so a field of poppies, dandelions, and
 * cornflowers all read as identical green specks.
 *
 * This exists to let Solaris assign colors block-by-block without touching vanilla's palette at
 * all (no risk to anyone's existing locked vanilla maps) and without inheriting its shading
 * math — an override here is used as a flat, final color via the same relief-shading path every
 * other pixel gets, not run through vanilla's {@code MapColor.Brightness} scale.
 *
 * Colors are plain {@code 0xRRGGBB} (no alpha) — same convention {@code Biome#getWaterColor()}/
 * {@code getGrassColor()} already use, so they drop straight into {@code ChunkColorSampler}'s
 * existing {@code packAbgr} helper.
 */
public final class BlockColorOverrides {

    private static final Map<Block, Integer> OVERRIDES = new HashMap<>();

    /**
     * Cut down on clutter, same idea the video raised — short/tall grass adds visual noise on
     * top of the actual terrain without conveying anything useful, unlike a flower or mushroom.
     * Checked by {@link ChunkColorSampler#sample}'s existing colorless-block descend loop
     * (originally built for {@code MapColor.NONE} decorations like signs/torches) so these are
     * skipped the exact same way, revealing the real ground color underneath rather than a flat
     * green blob.
     */
    private static final Set<Block> TRANSPARENT = Set.of(Blocks.GRASS, Blocks.TALL_GRASS);

    private BlockColorOverrides() {}

    public static Integer get(Block block) {
        return OVERRIDES.get(block);
    }

    public static boolean isTransparent(Block block) {
        return TRANSPARENT.contains(block);
    }

    private static void put(Block block, int rgb) {
        OVERRIDES.put(block, rgb);
    }

    static {
        // Flowers, colored as the dye they actually produce (or the closest vanilla dye to their
        // real-world color, for the handful without a direct dye recipe) — vanilla draws every
        // one of these as identical MapColor.PLANT green.
        put(Blocks.DANDELION, 0xFED83D);
        put(Blocks.POPPY, 0xB02E26);
        put(Blocks.BLUE_ORCHID, 0x3AB3DA);
        put(Blocks.ALLIUM, 0xC74EBD);
        put(Blocks.AZURE_BLUET, 0xF9FFFE);
        put(Blocks.RED_TULIP, 0xB02E26);
        put(Blocks.ORANGE_TULIP, 0xF9801D);
        put(Blocks.WHITE_TULIP, 0xF9FFFE);
        put(Blocks.PINK_TULIP, 0xF38BAA);
        put(Blocks.OXEYE_DAISY, 0xF9FFFE);
        put(Blocks.CORNFLOWER, 0x3C44AA);
        put(Blocks.LILY_OF_THE_VALLEY, 0xF9FFFE);
        put(Blocks.WITHER_ROSE, 0x2B1A1A);
        put(Blocks.TORCHFLOWER, 0xF9801D);
        // Tall (2-block) flowers share their Block instance across both halves, so one entry
        // covers whichever half WORLD_SURFACE happens to sample.
        put(Blocks.SUNFLOWER, 0xFED83D);
        put(Blocks.LILAC, 0xC74EBD);
        put(Blocks.ROSE_BUSH, 0xB02E26);
        put(Blocks.PEONY, 0xF38BAA);
        put(Blocks.PITCHER_PLANT, 0x169C9C);

        // Mushroom caps — vanilla's flat MapColor.PLANT green doesn't distinguish these from
        // grass/ferns at all, let alone from each other.
        put(Blocks.RED_MUSHROOM, 0xC53B3B);
        put(Blocks.BROWN_MUSHROOM, 0x9C6B4F);

        // Rails default to MapColor.NONE in their own vanilla registration (verified against
        // decompiled Blocks.java — nobody at Mojang ever set one), so without an override
        // they're invisible — the descend loop walks straight past them to whatever's
        // underneath. A visible railway is exactly the kind of thing worth seeing on a
        // planning map.
        int railColor = 0xB6B6B6;
        put(Blocks.RAIL, railColor);
        put(Blocks.POWERED_RAIL, railColor);
        put(Blocks.DETECTOR_RAIL, railColor);
        put(Blocks.ACTIVATOR_RAIL, railColor);

        // More MapColor.NONE-by-omission blocks, verified the same way — this list is
        // deliberately NOT exhaustive. Included: blocks that occupy real, structurally
        // meaningful space (a wall of iron bars, a glass window, a ladder up a shaft, a nether
        // portal) where seeing them matters. Excluded on purpose: torches, tripwire, redstone
        // dust, flower pots, end rods — thin decorations sitting ON TOP of another block, where
        // the ORIGINAL point of descending past NONE blocks (see the class doc on
        // ChunkColorSampler's loop) was specifically so the real floor/wall shows through
        // instead of the decoration hiding it; overriding those would undo that, not extend it.
        put(Blocks.IRON_BARS, 0xC6C6C6);
        put(Blocks.CHAIN, 0x565656);
        put(Blocks.LADDER, 0xB6875A);
        put(Blocks.NETHER_PORTAL, 0x6E23B3);
        // Glass used to be a flat opaque tile here too, but a solid pane reading as a solid wall
        // was misleading on a planning map — glass is deliberately see-through in-world, so
        // ChunkColorSampler now descends past it like a transparent block and blends a light tint
        // of its own color over whatever's actually underneath, instead of hiding it. See
        // ChunkColorSampler.isGlass/GLASS_TINT_STRENGTH.
    }
}
