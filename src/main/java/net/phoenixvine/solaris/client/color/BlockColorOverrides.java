package net.phoenixvine.solaris.client.color;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BlockColorOverrides {

    private static final Map<Block, Integer> OVERRIDES = new HashMap<>();

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

        put(Blocks.SUNFLOWER, 0xFED83D);
        put(Blocks.LILAC, 0xC74EBD);
        put(Blocks.ROSE_BUSH, 0xB02E26);
        put(Blocks.PEONY, 0xF38BAA);
        put(Blocks.PITCHER_PLANT, 0x169C9C);

        put(Blocks.RED_MUSHROOM, 0xC53B3B);
        put(Blocks.BROWN_MUSHROOM, 0x9C6B4F);

        int railColor = 0xB6B6B6;
        put(Blocks.RAIL, railColor);
        put(Blocks.POWERED_RAIL, railColor);
        put(Blocks.DETECTOR_RAIL, railColor);
        put(Blocks.ACTIVATOR_RAIL, railColor);

        put(Blocks.IRON_BARS, 0xC6C6C6);
        put(Blocks.CHAIN, 0x565656);
        put(Blocks.LADDER, 0xB6875A);
        put(Blocks.NETHER_PORTAL, 0x6E23B3);

    }
}
