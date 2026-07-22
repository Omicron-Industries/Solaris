package net.phoenixvine.solaris.client.color;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Set;

public final class ChunkColorSampler {

    private static final int MAX_WATER_SCAN = 320;

    public static final double RELIEF_LOW = 0.62;
    public static final double RELIEF_HIGH = 1.3;

    public static final double RELIEF_NEIGHBOR_SCALE = 0.8;
    public static final double RELIEF_THRESHOLD = 1.2;

    private static final double MIN_TINT_STRENGTH = 0.15;

    private static final int SEA_LEVEL_MIN = 60;
    private static final int SEA_LEVEL_MAX = 65;
    private static final double SEA_LEVEL_BONUS = 0.1;

    private static final double MAX_TINT_STRENGTH = 0.92;

    private static final double GRADIENT_DEPTH_RANGE = 24.0;

    private static final double ABYSS_BRIGHTNESS = 0.28;

    private static final double SHALLOW_BRIGHTNESS = 1.25;

    private ChunkColorSampler() {}

    private static boolean isAquaticPlant(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) ||
                state.is(Blocks.TALL_SEAGRASS);
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) ||
                state.is(Blocks.FROSTED_ICE);
    }

    private static final Set<Block> GLASS_BLOCKS = Set.of(Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
            Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS, Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.ORANGE_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
            Blocks.YELLOW_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS_PANE, Blocks.PINK_STAINED_GLASS_PANE,
            Blocks.GRAY_STAINED_GLASS_PANE, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Blocks.CYAN_STAINED_GLASS_PANE,
            Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE, Blocks.BROWN_STAINED_GLASS_PANE,
            Blocks.GREEN_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS_PANE);

    private static boolean isGlass(BlockState state) {
        return GLASS_BLOCKS.contains(state.getBlock());
    }

    private static final double GLASS_TINT_STRENGTH = 0.3;

    private static final int PLAIN_GLASS_TINT_ABGR = packAbgr(0xC8E8F0, 255);

    private static Integer glassTintPixel(BlockState state, MapColor mapColor) {
        if (!isGlass(state)) return null;
        if (mapColor != MapColor.NONE) {
            return mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
        }
        return PLAIN_GLASS_TINT_ABGR;
    }

    public static double relief(double delta) {
        double t = Mth.clamp(delta / RELIEF_THRESHOLD, -1.0, 1.0);
        return t >= 0 ? Mth.lerp(t, 1.0, RELIEF_HIGH) : Mth.lerp(-t, 1.0, RELIEF_LOW);
    }

    private static final int DUSK_START = 11000;
    private static final int DUSK_END = 13000;
    private static final int DAWN_START = 21000;
    private static final int DAWN_END = 23000;

    public static double nightFactor(long dayTime) {
        long ticks = ((dayTime % 24000) + 24000) % 24000;
        if (ticks >= DUSK_START && ticks < DUSK_END) {
            return smoothstep((ticks - DUSK_START) / (double) (DUSK_END - DUSK_START));
        }
        if (ticks >= DUSK_END && ticks < DAWN_START) {
            return 1.0;
        }
        if (ticks >= DAWN_START && ticks < DAWN_END) {
            return 1.0 - smoothstep((ticks - DAWN_START) / (double) (DAWN_END - DAWN_START));
        }
        return 0.0;
    }

    private static double smoothstep(double x) {
        x = Mth.clamp(x, 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    public static final class ColorSample {

        public final int[] pixels;
        public final boolean[] lightEmitting;
        public final int[] waterTint;
        public final int[] waterDepth;
        public final boolean[] waterOcean;
        public final boolean hadFallback;
        public final boolean[] foliage;

        private ColorSample(int[] pixels, boolean[] lightEmitting, int[] waterTint, int[] waterDepth,
                            boolean[] waterOcean, boolean hadFallback, boolean[] foliage) {
            this.pixels = pixels;
            this.lightEmitting = lightEmitting;
            this.waterTint = waterTint;
            this.waterDepth = waterDepth;
            this.waterOcean = waterOcean;
            this.hadFallback = hadFallback;
            this.foliage = foliage;
        }
    }

    public static ColorSample sample(Level level, ChunkPos pos) {
        int[] pixels = new int[256];
        boolean[] lightEmitting = new boolean[256];
        int[] waterTint = new int[256];
        int[] waterDepth = new int[256];
        boolean[] waterOcean = new boolean[256];
        boolean hadFallback = false;
        boolean[] foliage = new boolean[256];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                cursor.set(worldX, surfaceY, worldZ);

                BlockState state = level.getBlockState(cursor);

                BlockState camoState = CamouflageResolver.resolveState(level, cursor, state);
                if (camoState != null) state = camoState;
                MapColor mapColor = state.getMapColor(level, cursor);

                Integer glassTintAbgr = glassTintPixel(state, mapColor);

                int minY = level.getMinBuildHeight() + 1;
                while (surfaceY > minY && (BlockColorOverrides.isTransparent(state.getBlock()) || isGlass(state) ||
                        (mapColor == MapColor.NONE && BlockColorOverrides.get(state.getBlock()) == null))) {
                    surfaceY--;
                    cursor.set(worldX, surfaceY, worldZ);
                    state = level.getBlockState(cursor);
                    camoState = CamouflageResolver.resolveState(level, cursor, state);
                    if (camoState != null) state = camoState;
                    mapColor = state.getMapColor(level, cursor);
                    if (glassTintAbgr == null) {
                        glassTintAbgr = glassTintPixel(state, mapColor);
                    }
                }

                Holder<Biome> biomeHolder = level.getBiome(cursor);
                Biome biome = biomeHolder.value();

                double reliefFactor = 1.0;
                if (!isAquaticPlant(state) && !isIce(state) && level.hasChunk(worldX >> 4, (worldZ - 1) >> 4)) {
                    int northY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ - 1) - 1;
                    reliefFactor = relief((surfaceY - northY) * RELIEF_NEIGHBOR_SCALE);
                }

                int finalPixel;
                if (state.isAir()) {

                    finalPixel = 0;
                    hadFallback = true;
                    pixels[lz * 16 + lx] = finalPixel;
                    lightEmitting[lz * 16 + lx] = false;
                    continue;
                }
                Integer override = BlockColorOverrides.get(state.getBlock());
                if (override == null) {

                    override = CamouflageResolver.resolveDirectRgb(level, cursor, state);
                }
                if (override != null) {

                    finalPixel = scaleBrightness(packAbgr(override, 255), reliefFactor);
                } else if (mapColor == MapColor.WATER || state.is(Blocks.ICE)) {

                    int tintPixel = packAbgr(blendedWaterColor(level, worldX, worldZ, surfaceY, biome), 255);

                    finalPixel = tintPixel;
                    waterTint[lz * 16 + lx] = tintPixel;
                    waterDepth[lz * 16 + lx] = sampleWaterDepth(level, cursor, worldX, worldZ, surfaceY);
                    waterOcean[lz * 16 + lx] = biomeHolder.is(BiomeTags.IS_OCEAN);
                } else if (mapColor == MapColor.GRASS) {
                    finalPixel = scaleBrightness(
                            packAbgr(blendedGrassColor(level, worldX, worldZ, surfaceY, biome), 255), reliefFactor);
                } else if (mapColor == MapColor.PLANT) {
                    finalPixel = scaleBrightness(
                            packAbgr(blendedFoliageColor(level, worldX, worldZ, surfaceY, biome), 255), reliefFactor);
                    foliage[lz * 16 + lx] = true;
                } else {

                    BlockTextureColors.Result textureResult = BlockTextureColors.getResult(state);
                    int abgr;
                    if (textureResult.fallback) {
                        hadFallback = true;

                        abgr = mapColor != MapColor.NONE ? mapColor.calculateRGBColor(MapColor.Brightness.NORMAL) :
                                packAbgr(textureResult.rgb, 255);
                    } else {
                        abgr = packAbgr(textureResult.rgb, 255);
                    }
                    finalPixel = scaleBrightness(abgr, reliefFactor);
                }

                if (glassTintAbgr != null) {

                    finalPixel = blendAbgr(finalPixel, glassTintAbgr, GLASS_TINT_STRENGTH);
                }

                pixels[lz * 16 + lx] = finalPixel;

                lightEmitting[lz * 16 + lx] = state.getLightEmission(level, cursor) > 0;
            }
        }

        return new ColorSample(pixels, lightEmitting, waterTint, waterDepth, waterOcean, hadFallback, foliage);
    }

    public static final class HeightSample {

        public final int[] heights;
        public final boolean[] water;
        public final boolean[] rails;

        private HeightSample(int[] heights, boolean[] water, boolean[] rails) {
            this.heights = heights;
            this.water = water;
            this.rails = rails;
        }
    }

    private static boolean isRail(BlockState state) {
        return state.is(Blocks.RAIL) || state.is(Blocks.POWERED_RAIL) || state.is(Blocks.DETECTOR_RAIL) ||
                state.is(Blocks.ACTIVATOR_RAIL);
    }

    public static HeightSample sampleHeights(Level level, ChunkPos pos) {
        int[] heights = new int[256];
        boolean[] water = new boolean[256];
        boolean[] rails = new boolean[256];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                cursor.set(worldX, surfaceY, worldZ);
                BlockState state = level.getBlockState(cursor);

                int minY = level.getMinBuildHeight() + 1;
                while (surfaceY > minY && (BlockColorOverrides.isTransparent(state.getBlock()) || isGlass(state) ||
                        (state.getMapColor(level, cursor) == MapColor.NONE &&
                                BlockColorOverrides.get(state.getBlock()) == null))) {
                    surfaceY--;
                    cursor.set(worldX, surfaceY, worldZ);
                    state = level.getBlockState(cursor);
                }

                heights[lz * 16 + lx] = surfaceY;

                water[lz * 16 + lx] = state.getMapColor(level, cursor) == MapColor.WATER || isAquaticPlant(state) ||
                        isIce(state);

                rails[lz * 16 + lx] = isRail(state);
            }
        }
        return new HeightSample(heights, water, rails);
    }

    private static int sampleWaterDepth(Level level, BlockPos.MutableBlockPos cursor, int worldX, int worldZ,
                                        int surfaceY) {
        int minY = Math.max(level.getMinBuildHeight() + 1, surfaceY - MAX_WATER_SCAN);
        for (int y = surfaceY - 1; y >= minY; y--) {
            cursor.set(worldX, y, worldZ);
            MapColor mc = level.getBlockState(cursor).getMapColor(level, cursor);
            if (mc != MapColor.WATER && mc != MapColor.NONE) {
                return surfaceY - y;
            }
        }
        return surfaceY - minY;
    }

    public static int compositeWaterTint(int tintPixel, int depth, boolean isOcean, int surfaceY) {
        int shallowPixel = scaleBrightness(tintPixel, SHALLOW_BRIGHTNESS);
        int deepPixel = scaleBrightness(tintPixel, ABYSS_BRIGHTNESS);

        if (!isOcean) {

            return blendAbgr(shallowPixel, deepPixel, SolarisConfig.WATER_OPACITY.get());
        }

        boolean forceShallow = SolarisConfig.WATER_DEEP_ONLY.get() &&
                surfaceY >= SolarisConfig.WATER_DEEP_Y_THRESHOLD.get();
        if (forceShallow) return shallowPixel;

        double depthFactor = Mth.clamp(depth / GRADIENT_DEPTH_RANGE, 0.0, 1.0);
        double maxBlend = MIN_TINT_STRENGTH +
                (MAX_TINT_STRENGTH - MIN_TINT_STRENGTH) * SolarisConfig.WATER_OPACITY.get();
        double blendFactor = depthFactor * maxBlend;
        if (surfaceY >= SEA_LEVEL_MIN && surfaceY <= SEA_LEVEL_MAX) {
            blendFactor += SEA_LEVEL_BONUS;
        }
        blendFactor = Mth.clamp(blendFactor, 0.0, MAX_TINT_STRENGTH);
        return blendAbgr(shallowPixel, deepPixel, blendFactor);
    }

    private static final int WATER_BLEND_STEP = 4;

    private static int blendedWaterColor(Level level, int worldX, int worldZ, int surfaceY, Biome centerBiome) {
        int radius = (SolarisConfig.WATER_BLEND_RADIUS.get() / WATER_BLEND_STEP) * WATER_BLEND_STEP;
        if (radius <= 0) return centerBiome.getWaterColor();

        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        for (int dz = -radius; dz <= radius; dz += WATER_BLEND_STEP) {
            for (int dx = -radius; dx <= radius; dx += WATER_BLEND_STEP) {
                int rgb;
                if (dx == 0 && dz == 0) {
                    rgb = centerBiome.getWaterColor();
                } else {
                    samplePos.set(worldX + dx, surfaceY, worldZ + dz);
                    rgb = level.getBiome(samplePos).value().getWaterColor();
                }
                r += rgb >> 16 & 255;
                g += rgb >> 8 & 255;
                b += rgb & 255;
                count++;
            }
        }
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    private static final int LAND_BLEND_DISTANCE = 4;

    private static int blendedGrassColor(Level level, int worldX, int worldZ, int surfaceY, Biome centerBiome) {
        int centerRgb = centerBiome.getGrassColor(worldX, worldZ);
        long r = centerRgb >> 16 & 255;
        long g = centerRgb >> 8 & 255;
        long b = centerRgb & 255;
        int count = 1;

        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        int[] dxs = { -LAND_BLEND_DISTANCE, LAND_BLEND_DISTANCE, 0, 0 };
        int[] dzs = { 0, 0, -LAND_BLEND_DISTANCE, LAND_BLEND_DISTANCE };
        for (int i = 0; i < dxs.length; i++) {
            int sampleX = worldX + dxs[i];
            int sampleZ = worldZ + dzs[i];
            samplePos.set(sampleX, surfaceY, sampleZ);
            int rgb = level.getBiome(samplePos).value().getGrassColor(sampleX, sampleZ);
            r += rgb >> 16 & 255;
            g += rgb >> 8 & 255;
            b += rgb & 255;
            count++;
        }
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    private static int blendedFoliageColor(Level level, int worldX, int worldZ, int surfaceY, Biome centerBiome) {
        int centerRgb = centerBiome.getFoliageColor();
        long r = centerRgb >> 16 & 255;
        long g = centerRgb >> 8 & 255;
        long b = centerRgb & 255;
        int count = 1;

        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        int[] dxs = { -LAND_BLEND_DISTANCE, LAND_BLEND_DISTANCE, 0, 0 };
        int[] dzs = { 0, 0, -LAND_BLEND_DISTANCE, LAND_BLEND_DISTANCE };
        for (int i = 0; i < dxs.length; i++) {
            samplePos.set(worldX + dxs[i], surfaceY, worldZ + dzs[i]);
            int rgb = level.getBiome(samplePos).value().getFoliageColor();
            r += rgb >> 16 & 255;
            g += rgb >> 8 & 255;
            b += rgb & 255;
            count++;
        }
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    private static int packAbgr(int rgb, int modifier) {
        int r = (rgb >> 16 & 255) * modifier / 255;
        int g = (rgb >> 8 & 255) * modifier / 255;
        int b = (rgb & 255) * modifier / 255;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    private static int blendAbgr(int baseAbgr, int overlayAbgr, double alpha) {
        int a = (int) Math.round(alpha * 255);
        int baseR = baseAbgr & 255;
        int baseG = baseAbgr >> 8 & 255;
        int baseB = baseAbgr >> 16 & 255;
        int ovR = overlayAbgr & 255;
        int ovG = overlayAbgr >> 8 & 255;
        int ovB = overlayAbgr >> 16 & 255;
        int r = (ovR * a + baseR * (255 - a)) / 255;
        int g = (ovG * a + baseG * (255 - a)) / 255;
        int b = (ovB * a + baseB * (255 - a)) / 255;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    private static int scaleBrightness(int abgr, double factor) {
        if (factor == 1.0) return abgr;
        int a = abgr >>> 24;
        int r = clampChannel((int) ((abgr & 255) * factor));
        int g = clampChannel((int) ((abgr >> 8 & 255) * factor));
        int b = clampChannel((int) ((abgr >> 16 & 255) * factor));
        return a << 24 | b << 16 | g << 8 | r;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
