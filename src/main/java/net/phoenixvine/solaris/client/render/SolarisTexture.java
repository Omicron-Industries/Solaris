package net.phoenixvine.solaris.client.render;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.CaveColorSampler;
import net.phoenixvine.solaris.client.color.ChunkColorCache;
import net.phoenixvine.solaris.client.color.ChunkColorSampler;
import net.phoenixvine.solaris.client.color.ChunkFoliageCache;
import net.phoenixvine.solaris.client.color.ChunkHeightCache;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.ChunkLightCache;
import net.phoenixvine.solaris.client.color.ChunkWaterCache;
import net.phoenixvine.solaris.client.color.ChunkWaterDepthCache;
import net.phoenixvine.solaris.client.color.ChunkWaterOceanCache;
import net.phoenixvine.solaris.client.color.ChunkWaterTintCache;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SolarisTexture implements AutoCloseable {

    private static final List<SolarisTexture> ACTIVE = new CopyOnWriteArrayList<>();

    private static final int CAVE_REVEAL_RADIUS = 3;

    private static final int CAVE_Y_REBUILD_THRESHOLD = 3;

    private static final int DEFAULT_HEIGHT = 63;

    private final String name;
    private final ResourceLocation textureId;
    private final int radiusChunks;
    private final int spanChunks;
    private final int sizePixels;
    private final NativeImage image;
    private final DynamicTexture texture;

    private final int[] heights;

    private final boolean[] water;

    private final boolean[] lightEmitting;

    private final int[] waterDepth;

    private final long[] blurHSumR;
    private final long[] blurHSumG;
    private final long[] blurHSumB;
    private final int[] blurHCount;
    private final int[] blurred;

    private final NativeImage baseImage;

    private ResourceLocation globeTextureId;
    private NativeImage globeImage;
    private DynamicTexture globeTexture;

    private final int[] globeHeights;
    private int rebuildGeneration = 0;
    private int lastGlobeGeneration = -1;

    private ChunkKey lastCenter;
    private int lastPlayerY = Integer.MIN_VALUE;
    private boolean lastUnderground = false;

    private int seamX;
    private int seamZ;

    private double lastNightFactorBucket = -1;

    public SolarisTexture(String name, int radiusChunks) {
        this.name = name;
        this.radiusChunks = radiusChunks;
        this.spanChunks = radiusChunks * 2 + 1;
        this.sizePixels = spanChunks * 16;

        this.textureId = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/" + name);
        this.image = new NativeImage(sizePixels, sizePixels, false);
        this.texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(textureId, texture);

        texture.setFilter(true, false);
        this.heights = new int[sizePixels * sizePixels];
        this.water = new boolean[sizePixels * sizePixels];
        this.lightEmitting = new boolean[sizePixels * sizePixels];
        this.waterDepth = new int[sizePixels * sizePixels];
        this.blurHSumR = new long[sizePixels * sizePixels];
        this.blurHSumG = new long[sizePixels * sizePixels];
        this.blurHSumB = new long[sizePixels * sizePixels];
        this.blurHCount = new int[sizePixels * sizePixels];
        this.blurred = new int[sizePixels * sizePixels];
        this.baseImage = new NativeImage(sizePixels, sizePixels, false);
        this.globeHeights = new int[sizePixels * sizePixels];

        ACTIVE.add(this);
    }

    public ResourceLocation textureId() {
        return textureId;
    }

    public ChunkKey getLastCenter() {
        return lastCenter;
    }

    public int getSizePixels() {
        return sizePixels;
    }

    public int getRadiusChunks() {
        return radiusChunks;
    }

    public int[] getHeights() {
        return heights;
    }

    public int[] getGlobeHeights() {
        return globeHeights;
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter EXPORT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    public void exportToPng(Consumer<Component> messageConsumer) {
        NativeImage snapshot = new NativeImage(sizePixels, sizePixels, false);
        for (int lz = 0; lz < sizePixels; lz++) {
            int pz = (seamZ + lz) % sizePixels;
            for (int lx = 0; lx < sizePixels; lx++) {
                int px = (seamX + lx) % sizePixels;
                snapshot.setPixelRGBA(lx, lz, image.getPixelRGBA(px, pz));
            }
        }

        File dir = new File(Minecraft.getInstance().gameDirectory, "solaris_maps");
        dir.mkdirs();
        File file = new File(dir, name + "_" + LocalDateTime.now().format(EXPORT_NAME_FORMAT) + ".png");

        Util.ioPool().execute(() -> {
            try {
                snapshot.writeToFile(file);
                Component link = Component.literal(file.getName()).withStyle(ChatFormatting.UNDERLINE).withStyle(
                        style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
                messageConsumer.accept(Component.translatable("solaris.export.success", link));
            } catch (IOException e) {
                LOGGER.warn("Couldn't export Solaris map", e);
                messageConsumer.accept(Component.translatable("solaris.export.failure", e.getMessage()));
            } finally {
                snapshot.close();
            }
        });
    }

    private static boolean isCaveSliceMode(Level level, Player player) {
        if (level == null) return false;
        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_UNDERGROUND_MAP, level.dimension().location())
                .atLeast(SolarisFeatureState.VISIBLE)) {
            return false;
        }
        if (level.dimensionType().hasCeiling()) return true;
        return player != null && CaveColorSampler.isUnderground(level, player.blockPosition());
    }

    private static final double NIGHT_FACTOR_BUCKET = 0.05;

    public void maybeRebuild(ChunkKey center) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        int playerY = player != null ? Mth.floor(player.getY()) : 0;
        boolean underground = isCaveSliceMode(level, player);

        boolean centerChanged = !center.equals(lastCenter);
        boolean caveStale = underground &&
                (!lastUnderground || Math.abs(playerY - lastPlayerY) >= CAVE_Y_REBUILD_THRESHOLD);
        boolean surfacedAgain = !underground && lastUnderground;

        boolean nightFactorChanged = false;
        double nightFactorBucket = lastNightFactorBucket;
        if (level != null) {
            nightFactorBucket = Math.round(ChunkColorSampler.nightFactor(level.getDayTime()) / NIGHT_FACTOR_BUCKET) *
                    NIGHT_FACTOR_BUCKET;
            nightFactorChanged = nightFactorBucket != lastNightFactorBucket;
        }

        if (!centerChanged && !caveStale && !surfacedAgain && !nightFactorChanged) return;

        if (!PersistentChunkStore.isLoaded(center.dimension().toString())) return;

        rebuild(center);

        lastPlayerY = playerY;
        lastUnderground = underground;
        lastNightFactorBucket = nightFactorBucket;
    }

    public void rebuild(ChunkKey center) {
        long profileStart = SolarisProfiler.start();
        ChunkKey previousCenter = lastCenter;
        lastCenter = center;
        List<SolarisOverlay> overlays = SolarisOverlayRegistry.getOverlays();

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        boolean underground = isCaveSliceMode(level, player);
        int playerY = player != null ? Mth.floor(player.getY()) : 0;

        boolean nightFactorChanged = level != null &&
                Math.round(ChunkColorSampler.nightFactor(level.getDayTime()) / NIGHT_FACTOR_BUCKET) *
                        NIGHT_FACTOR_BUCKET != lastNightFactorBucket;

        boolean canSkip = previousCenter != null && !underground && !lastUnderground &&
                previousCenter.dimension().equals(center.dimension());

        int prevSeamX = this.seamX;
        int prevSeamZ = this.seamZ;

        int seamX = TextureAddressing.properMod(center.x() - radiusChunks, spanChunks) * 16;
        int seamZ = TextureAddressing.properMod(center.z() - radiusChunks, spanChunks) * 16;
        this.seamX = seamX;
        this.seamZ = seamZ;

        int dirtyMinX = Integer.MAX_VALUE;
        int dirtyMaxX = -1;
        int dirtyMinZ = Integer.MAX_VALUE;
        int dirtyMaxZ = -1;

        int fogArgb = SolarisThemeUtils.C_FAINT;
        int fogColor = FastColor.ABGR32.color(FastColor.ARGB32.alpha(fogArgb), FastColor.ARGB32.blue(fogArgb),
                FastColor.ARGB32.green(fogArgb), FastColor.ARGB32.red(fogArgb));

        int hiddenColor = FastColor.ABGR32.color(255, 0, 0, 0);

        double saturation = SolarisConfig.SATURATION.get();
        double contrast = SolarisConfig.CONTRAST.get();
        double brightness = SolarisConfig.BRIGHTNESS.get();
        double foliageBrightness = SolarisConfig.FOLIAGE_BRIGHTNESS.get();
        double tintRed = SolarisConfig.TINT_RED.get();
        double tintGreen = SolarisConfig.TINT_GREEN.get();
        double tintBlue = SolarisConfig.TINT_BLUE.get();
        boolean hasColorTint = tintRed != 1.0 || tintGreen != 1.0 || tintBlue != 1.0;

        boolean profiling = SolarisConfig.PERF_LOGGING.get();
        long cacheLookupNanos = 0;
        long cacheMissFallbackNanos = 0;
        int cacheMissCount = 0;
        int processedChunkCount = 0;
        long overlayNanos = 0;
        long pixelLoopNanos = 0;

        ConcurrentHashMap<ChunkKey, PersistentChunkStore.Entry> persistedChunks = PersistentChunkStore
                .chunksFor(center.dimension().toString());

        long mainLoopStart = SolarisProfiler.start();
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;

                if (canSkip && Math.abs(chunkX - previousCenter.x()) <= radiusChunks &&
                        Math.abs(chunkZ - previousCenter.z()) <= radiusChunks) {

                    continue;
                }
                processedChunkCount++;

                ChunkKey key = new ChunkKey(center.dimension(), chunkX, chunkZ);
                int baseX = TextureAddressing.properMod(chunkX, spanChunks) * 16;
                int baseZ = TextureAddressing.properMod(chunkZ, spanChunks) * 16;
                dirtyMinX = Math.min(dirtyMinX, baseX);
                dirtyMaxX = Math.max(dirtyMaxX, baseX + 15);
                dirtyMinZ = Math.min(dirtyMinZ, baseZ);
                dirtyMaxZ = Math.max(dirtyMaxZ, baseZ + 15);

                boolean caveReveal = underground && level != null &&
                        Math.max(Math.abs(dx), Math.abs(dz)) <= CAVE_REVEAL_RADIUS;

                long cacheStart = profiling ? System.nanoTime() : 0;
                int[] pixels;
                int unknownColor;
                int[] chunkHeights;
                boolean[] chunkWater;
                boolean[] chunkLight;
                int[] chunkWaterTint;
                int[] chunkWaterDepth;
                boolean[] chunkWaterOcean;
                boolean[] chunkFoliage;
                if (underground) {
                    pixels = caveReveal ? CaveColorSampler.sample(level, key.toChunkPos(), playerY) : null;
                    unknownColor = caveReveal ? fogColor : hiddenColor;

                    chunkHeights = ChunkHeightCache.get(key);
                    chunkWater = ChunkWaterCache.get(key);

                    chunkLight = null;
                    chunkWaterTint = null;
                    chunkWaterDepth = null;
                    chunkWaterOcean = null;
                    chunkFoliage = null;
                } else {
                    pixels = ChunkColorCache.get(key);
                    chunkHeights = ChunkHeightCache.get(key);
                    chunkWater = ChunkWaterCache.get(key);
                    chunkLight = ChunkLightCache.get(key);
                    chunkWaterTint = ChunkWaterTintCache.get(key);
                    chunkWaterDepth = ChunkWaterDepthCache.get(key);
                    chunkWaterOcean = ChunkWaterOceanCache.get(key);
                    chunkFoliage = ChunkFoliageCache.get(key);
                    if (pixels == null) {

                        long fallbackStart = profiling ? System.nanoTime() : 0;
                        PersistentChunkStore.Entry persisted = persistedChunks != null ? persistedChunks.get(key) :
                                null;
                        if (persisted != null) {
                            persisted.touch();
                            pixels = persisted.pixels();
                            chunkHeights = persisted.heights();
                            chunkWater = persisted.water();
                            chunkWaterTint = persisted.waterTint();
                            chunkWaterDepth = persisted.waterDepth();
                            chunkWaterOcean = persisted.waterOcean();
                            chunkFoliage = persisted.foliage();
                            ChunkColorCache.put(key, pixels);
                            ChunkHeightCache.put(key, chunkHeights);
                            ChunkWaterCache.put(key, chunkWater);
                            ChunkWaterTintCache.put(key, chunkWaterTint);
                            ChunkWaterDepthCache.put(key, chunkWaterDepth);
                            ChunkWaterOceanCache.put(key, chunkWaterOcean);
                            ChunkFoliageCache.put(key, chunkFoliage);
                        }
                        if (profiling) {
                            cacheMissFallbackNanos += System.nanoTime() - fallbackStart;
                            cacheMissCount++;
                        }
                    }
                    unknownColor = fogColor;
                }
                if (profiling) cacheLookupNanos += System.nanoTime() - cacheStart;

                long overlayStart = profiling ? System.nanoTime() : 0;
                int tint = 0;
                boolean hasTint = false;
                for (SolarisOverlay overlay : overlays) {
                    Optional<Integer> color = overlay.colorAt(center.dimension(), chunkX, chunkZ);
                    if (color.isPresent()) {
                        tint = color.get();
                        hasTint = true;

                    }
                }
                if (profiling) overlayNanos += System.nanoTime() - overlayStart;

                long pixelLoopStart = profiling ? System.nanoTime() : 0;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int local = lz * 16 + lx;
                        int height = chunkHeights != null ? chunkHeights[local] : DEFAULT_HEIGHT;

                        int color = pixels != null ? pixels[local] : 0;
                        if (color == 0) {
                            color = unknownColor;
                        } else {

                            if (chunkWaterTint != null && chunkWaterTint[local] != 0) {
                                color = ChunkColorSampler.compositeWaterTint(chunkWaterTint[local],
                                        chunkWaterDepth[local], chunkWaterOcean[local], height);
                            }
                            if (saturation != 1.0) color = applySaturation(color, saturation);
                            if (contrast != 1.0) color = applyContrast(color, contrast);

                            if (brightness != 1.0) color = scaleBrightness(color, brightness);

                            if (chunkFoliage != null && chunkFoliage[local] && foliageBrightness != 1.0) {
                                color = scaleBrightness(color, foliageBrightness);
                            }

                            if (hasColorTint) color = scaleChannels(color, tintRed, tintGreen, tintBlue);
                        }
                        if (hasTint) color = blend(color, tint);
                        baseImage.setPixelRGBA(baseX + lx, baseZ + lz, color);

                        int idx = (baseZ + lz) * sizePixels + (baseX + lx);
                        heights[idx] = height;
                        water[idx] = chunkWater != null && chunkWater[local];
                        lightEmitting[idx] = chunkLight != null && chunkLight[local];
                        waterDepth[idx] = chunkWaterDepth != null ? chunkWaterDepth[local] : 0;
                    }
                }
                if (profiling) pixelLoopNanos += System.nanoTime() - pixelLoopStart;
            }
        }
        SolarisProfiler.end("mainLoop:" + name, mainLoopStart);
        if (profiling) {
            long now = System.nanoTime();
            SolarisProfiler.end("mainLoop:cacheLookup:" + name, now - cacheLookupNanos);
            SolarisProfiler.end("mainLoop:cacheMissFallback:" + name, now - cacheMissFallbackNanos);
            SolarisProfiler.end("mainLoop:overlay:" + name, now - overlayNanos);
            SolarisProfiler.end("mainLoop:pixelLoop:" + name, now - pixelLoopNanos);
            if (cacheMissCount > 0) {

                PhoenixSolaris.LOGGER.info(
                        "[Solaris perf] {}: {} of {} processed chunks were cache misses" +
                                " (fell back to PersistentChunkStore)",
                        name, cacheMissCount, processedChunkCount);
            }
        }

        SolarisProfiler.time("copyBaseImage:" + name, () -> image.copyFrom(baseImage));

        SolarisProfiler.time("waterBlur:" + name, this::blurWater);
        SolarisProfiler.time("waterRelief:" + name, this::applyWaterRelief);
        if (SolarisConfig.HILLSHADING.get()) {
            SolarisProfiler.time("hillshading:" + name, this::applyHillshading);
        }

        if (level != null && !underground) {
            SolarisProfiler.time("nightMode:" + name, this::applyNightDarkening);
        }
        if (SolarisConfig.VIGNETTE.get()) {
            SolarisProfiler.time("vignette:" + name, this::applyVignette);
        }
        if (SolarisConfig.BLACK_AND_WHITE.get()) {
            SolarisProfiler.time("blackAndWhite:" + name, this::applyBlackAndWhite);
        }

        long uploadStart = SolarisProfiler.start();
        boolean partialEligible = canSkip && !nightFactorChanged && dirtyMaxX >= 0;
        if (partialEligible) {
            int padMinX = Math.max(0, dirtyMinX - UPLOAD_DIRTY_PAD);
            int padMinZ = Math.max(0, dirtyMinZ - UPLOAD_DIRTY_PAD);
            int padMaxX = Math.min(sizePixels - 1, dirtyMaxX + UPLOAD_DIRTY_PAD);
            int padMaxZ = Math.min(sizePixels - 1, dirtyMaxZ + UPLOAD_DIRTY_PAD);
            int w = padMaxX - padMinX + 1;
            int h = padMaxZ - padMinZ + 1;

            texture.bind();
            image.upload(0, padMinX, padMinZ, padMinX, padMinZ, w, h, false, false, false, false);

            int seamBand = WATER_BLUR_RADIUS + 1;
            int colMin = Math.max(0, Math.min(seamX, prevSeamX) - seamBand);
            int colMax = Math.min(sizePixels - 1, Math.max(seamX, prevSeamX) + seamBand);
            image.upload(0, colMin, 0, colMin, 0, colMax - colMin + 1, sizePixels, false, false, false, false);

            int rowMin = Math.max(0, Math.min(seamZ, prevSeamZ) - seamBand);
            int rowMax = Math.min(sizePixels - 1, Math.max(seamZ, prevSeamZ) + seamBand);
            image.upload(0, 0, rowMin, 0, rowMin, sizePixels, rowMax - rowMin + 1, false, false, false, false);

            if (SolarisConfig.PERF_LOGGING.get()) {
                PhoenixSolaris.LOGGER.info(
                        "[Solaris upload] {}: partial upload, processed={}, dirtyX=[{},{}] dirtyZ=[{},{}] " +
                                "size={} center=({},{}) prevCenter=({},{})",
                        name, processedChunkCount, dirtyMinX, dirtyMaxX, dirtyMinZ, dirtyMaxZ, sizePixels,
                        center.x(), center.z(), previousCenter.x(), previousCenter.z());
            }

            SolarisProfiler.end("upload:partial:" + name, uploadStart);
        } else {
            texture.upload();
            if (SolarisConfig.PERF_LOGGING.get()) {
                PhoenixSolaris.LOGGER.info(
                        "[Solaris upload] {}: full upload, canSkip={}, nightFactorChanged={}, dirtyMaxX={}, " +
                                "processed={}, center=({},{}) prevCenter={}",
                        name, canSkip, nightFactorChanged, dirtyMaxX, processedChunkCount, center.x(), center.z(),
                        previousCenter);
            }
            SolarisProfiler.end("upload:full:" + name, uploadStart);
        }

        texture.setFilter(true, false);
        rebuildGeneration++;
        SolarisProfiler.end("textureRebuild:" + name, profileStart);
    }

    private static final int UPLOAD_DIRTY_PAD = 16;

    private int wrappedNeighbor(int coord, int delta, int seam) {
        if (delta < 0 && coord == seam) return coord;
        int next = Math.floorMod(coord + delta, sizePixels);
        if (delta > 0 && next == seam) return coord;
        return next;
    }

    private static final int WATER_BLUR_RADIUS = 3;

    private void blurWater() {
        boolean anyWater = false;
        for (boolean w : water) {
            if (w) {
                anyWater = true;
                break;
            }
        }
        if (!anyWater) return;

        long[] hSumR = blurHSumR;
        long[] hSumG = blurHSumG;
        long[] hSumB = blurHSumB;
        int[] hCount = blurHCount;

        for (int z = 0; z < sizePixels; z++) {
            int rowBase = z * sizePixels;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            int count = 0;
            for (int lx = 0; lx <= Math.min(WATER_BLUR_RADIUS, sizePixels - 1); lx++) {
                int px = (seamX + lx) % sizePixels;
                if (!water[rowBase + px]) continue;
                int abgr = image.getPixelRGBA(px, z);
                sumR += FastColor.ABGR32.red(abgr);
                sumG += FastColor.ABGR32.green(abgr);
                sumB += FastColor.ABGR32.blue(abgr);
                count++;
            }
            hSumR[rowBase + seamX] = sumR;
            hSumG[rowBase + seamX] = sumG;
            hSumB[rowBase + seamX] = sumB;
            hCount[rowBase + seamX] = count;

            for (int lx = 1; lx < sizePixels; lx++) {
                int addLx = lx + WATER_BLUR_RADIUS;
                int removeLx = lx - WATER_BLUR_RADIUS - 1;
                if (addLx < sizePixels) {
                    int addPx = (seamX + addLx) % sizePixels;
                    if (water[rowBase + addPx]) {
                        int abgr = image.getPixelRGBA(addPx, z);
                        sumR += FastColor.ABGR32.red(abgr);
                        sumG += FastColor.ABGR32.green(abgr);
                        sumB += FastColor.ABGR32.blue(abgr);
                        count++;
                    }
                }
                if (removeLx >= 0) {
                    int removePx = (seamX + removeLx) % sizePixels;
                    if (water[rowBase + removePx]) {
                        int abgr = image.getPixelRGBA(removePx, z);
                        sumR -= FastColor.ABGR32.red(abgr);
                        sumG -= FastColor.ABGR32.green(abgr);
                        sumB -= FastColor.ABGR32.blue(abgr);
                        count--;
                    }
                }
                int px = (seamX + lx) % sizePixels;
                int idx = rowBase + px;
                hSumR[idx] = sumR;
                hSumG[idx] = sumG;
                hSumB[idx] = sumB;
                hCount[idx] = count;
            }
        }

        int[] blurred = this.blurred;
        for (int x = 0; x < sizePixels; x++) {
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            long count = 0;
            for (int lz = 0; lz <= Math.min(WATER_BLUR_RADIUS, sizePixels - 1); lz++) {
                int pz = (seamZ + lz) % sizePixels;
                int idx = pz * sizePixels + x;
                sumR += hSumR[idx];
                sumG += hSumG[idx];
                sumB += hSumB[idx];
                count += hCount[idx];
            }
            setBlurredPixel(blurred, x, seamZ, sumR, sumG, sumB, count);

            for (int lz = 1; lz < sizePixels; lz++) {
                int addLz = lz + WATER_BLUR_RADIUS;
                int removeLz = lz - WATER_BLUR_RADIUS - 1;
                if (addLz < sizePixels) {
                    int addPz = (seamZ + addLz) % sizePixels;
                    int idx = addPz * sizePixels + x;
                    sumR += hSumR[idx];
                    sumG += hSumG[idx];
                    sumB += hSumB[idx];
                    count += hCount[idx];
                }
                if (removeLz >= 0) {
                    int removePz = (seamZ + removeLz) % sizePixels;
                    int idx = removePz * sizePixels + x;
                    sumR -= hSumR[idx];
                    sumG -= hSumG[idx];
                    sumB -= hSumB[idx];
                    count -= hCount[idx];
                }
                int pz = (seamZ + lz) % sizePixels;
                setBlurredPixel(blurred, x, pz, sumR, sumG, sumB, count);
            }
        }

        for (int z = 0; z < sizePixels; z++) {
            for (int x = 0; x < sizePixels; x++) {
                int idx = z * sizePixels + x;
                if (water[idx]) image.setPixelRGBA(x, z, blurred[idx]);
            }
        }
    }

    private void setBlurredPixel(int[] blurred, int x, int z, long sumR, long sumG, long sumB, long count) {
        int idx = z * sizePixels + x;
        if (!water[idx] || count == 0) return;
        blurred[idx] = FastColor.ABGR32.color(255, (int) (sumB / count), (int) (sumG / count), (int) (sumR / count));
    }

    private static final float WATER_RELIEF_NEIGHBOR_SCALE = 0.3f;

    private static final float WATER_RELIEF_GAIN = 0.6f;

    private static final float WATER_RELIEF_CLAMP = 0.15f;

    private void applyWaterRelief() {
        boolean anyWater = false;
        for (boolean w : water) {
            if (w) {
                anyWater = true;
                break;
            }
        }
        if (!anyWater) return;

        for (int z = 0; z < sizePixels; z++) {
            int north = wrappedNeighbor(z, -1, seamZ);
            int south = wrappedNeighbor(z, 1, seamZ);
            for (int x = 0; x < sizePixels; x++) {
                int idx = z * sizePixels + x;
                if (!water[idx]) continue;
                int west = wrappedNeighbor(x, -1, seamX);
                int east = wrappedNeighbor(x, 1, seamX);

                int idxWest = z * sizePixels + west;
                int idxEast = z * sizePixels + east;
                int idxNorth = north * sizePixels + x;
                int idxSouth = south * sizePixels + x;

                int depthHere = waterDepth[idx];
                int depthWest = water[idxWest] ? waterDepth[idxWest] : depthHere;
                int depthEast = water[idxEast] ? waterDepth[idxEast] : depthHere;
                int depthNorth = water[idxNorth] ? waterDepth[idxNorth] : depthHere;
                int depthSouth = water[idxSouth] ? waterDepth[idxSouth] : depthHere;

                float dzdx = -(depthEast - depthWest) * 0.5f * WATER_RELIEF_NEIGHBOR_SCALE;
                float dzdy = -(depthSouth - depthNorth) * 0.5f * WATER_RELIEF_NEIGHBOR_SCALE;

                float nx = -dzdx;
                float ny = -dzdy;
                float nz = 1f;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

                float shade = (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / (nLen * LIGHT_LEN);
                float factor = 1f + WATER_RELIEF_GAIN * (shade - FLAT_SHADE);
                factor = Mth.clamp(factor, 1f - WATER_RELIEF_CLAMP, 1f + WATER_RELIEF_CLAMP);
                if (factor == 1f) continue;

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, scaleBrightness(abgr, factor));
            }
        }
    }

    private static final float LIGHT_X = -0.5f;
    private static final float LIGHT_Y = -0.5f;
    private static final float LIGHT_Z = 0.8f;
    private static final float LIGHT_LEN = (float) Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);

    private static final float FLAT_SHADE = LIGHT_Z / LIGHT_LEN;

    private static final float HILLSHADE_GAIN = 1.8f;

    private void applyHillshading() {
        double strength = SolarisConfig.HILLSHADING_STRENGTH.get();
        for (int z = 0; z < sizePixels; z++) {
            int north = wrappedNeighbor(z, -1, seamZ);
            int south = wrappedNeighbor(z, 1, seamZ);
            for (int x = 0; x < sizePixels; x++) {
                int west = wrappedNeighbor(x, -1, seamX);
                int east = wrappedNeighbor(x, 1, seamX);

                float dzdx = (heights[z * sizePixels + east] - heights[z * sizePixels + west]) * 0.5f;
                float dzdy = (heights[south * sizePixels + x] - heights[north * sizePixels + x]) * 0.5f;

                float nx = -dzdx;
                float ny = -dzdy;
                float nz = 1f;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

                float shade = (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / (nLen * LIGHT_LEN);
                float factor = 1f + (float) strength * HILLSHADE_GAIN * (shade - FLAT_SHADE);
                factor = Mth.clamp(factor, 0.3f, 1.8f);

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, scaleBrightness(abgr, factor));
            }
        }
    }

    private void applyNightDarkening() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        double factor = ChunkColorSampler.nightFactor(level.getDayTime());
        if (factor <= 0.0) return;

        double strength = SolarisConfig.NIGHT_MODE_STRENGTH.get();
        double multiplier = Mth.lerp(factor, 1.0, 1.0 - strength);

        for (int z = 0; z < sizePixels; z++) {
            for (int x = 0; x < sizePixels; x++) {
                int idx = z * sizePixels + x;
                if (lightEmitting[idx]) continue;
                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, scaleBrightness(abgr, multiplier));
            }
        }
    }

    private void applyVignette() {
        double strength = SolarisConfig.VIGNETTE_STRENGTH.get();
        if (strength <= 0.0) return;

        double centerX = sizePixels / 2.0;
        double centerZ = sizePixels / 2.0;
        double maxDist = Math.sqrt(centerX * centerX + centerZ * centerZ);

        for (int z = 0; z < sizePixels; z++) {
            double dz = z - centerZ;
            for (int x = 0; x < sizePixels; x++) {
                double dx = x - centerX;
                double normalizedDist = Math.sqrt(dx * dx + dz * dz) / maxDist;
                double multiplier = 1.0 - normalizedDist * strength;

                int abgr = image.getPixelRGBA(x, z);
                image.setPixelRGBA(x, z, scaleBrightness(abgr, multiplier));
            }
        }
    }

    private void applyBlackAndWhite() {
        for (int z = 0; z < sizePixels; z++) {
            for (int x = 0; x < sizePixels; x++) {
                int abgr = image.getPixelRGBA(x, z);
                int a = FastColor.ABGR32.alpha(abgr);
                int r = FastColor.ABGR32.red(abgr);
                int g = FastColor.ABGR32.green(abgr);
                int b = FastColor.ABGR32.blue(abgr);
                int gray = clampChannel(Math.round(0.299f * r + 0.587f * g + 0.114f * b));
                image.setPixelRGBA(x, z, FastColor.ABGR32.color(a, gray, gray, gray));
            }
        }
    }

    public ResourceLocation ensureGlobeTexture() {
        if (globeTexture == null) {
            globeTextureId = new ResourceLocation(PhoenixSolaris.MOD_ID, "dynamic/" + name + "_globe");
            globeImage = new NativeImage(sizePixels, sizePixels, false);
            globeTexture = new DynamicTexture(globeImage);
            Minecraft.getInstance().getTextureManager().register(globeTextureId, globeTexture);

            globeTexture.setFilter(true, false);
        }
        if (lastGlobeGeneration == rebuildGeneration) return globeTextureId;
        lastGlobeGeneration = rebuildGeneration;

        for (int lz = 0; lz < sizePixels; lz++) {
            int pz = (seamZ + lz) % sizePixels;
            for (int lx = 0; lx < sizePixels; lx++) {
                int px = (seamX + lx) % sizePixels;
                int idx = pz * sizePixels + px;
                int abgr = image.getPixelRGBA(px, pz);
                double reliefFactor = 1.0;
                if (lz > 0 && !water[idx]) {
                    int prevPz = (seamZ + lz - 1) % sizePixels;
                    int prevIdx = prevPz * sizePixels + px;
                    double delta = (heights[idx] - heights[prevIdx]) * ChunkColorSampler.RELIEF_NEIGHBOR_SCALE;
                    reliefFactor = ChunkColorSampler.relief(delta);
                }
                int unReliefed = reliefFactor != 1.0 ? scaleBrightness(abgr, 1.0 / reliefFactor) : abgr;
                globeImage.setPixelRGBA(lx, lz, unReliefed);
                globeHeights[lz * sizePixels + lx] = heights[idx];
            }
        }
        globeTexture.upload();

        globeTexture.setFilter(true, false);
        return globeTextureId;
    }

    private static int blend(int baseAbgr, int overlayArgb) {
        int a = FastColor.ARGB32.alpha(overlayArgb);
        if (a <= 0) return baseAbgr;

        int ovR = FastColor.ARGB32.red(overlayArgb);
        int ovG = FastColor.ARGB32.green(overlayArgb);
        int ovB = FastColor.ARGB32.blue(overlayArgb);
        if (a >= 255) return FastColor.ABGR32.color(255, ovB, ovG, ovR);

        int baseR = FastColor.ABGR32.red(baseAbgr);
        int baseG = FastColor.ABGR32.green(baseAbgr);
        int baseB = FastColor.ABGR32.blue(baseAbgr);
        int r = (ovR * a + baseR * (255 - a)) / 255;
        int g = (ovG * a + baseG * (255 - a)) / 255;
        int b = (ovB * a + baseB * (255 - a)) / 255;
        return FastColor.ABGR32.color(255, b, g, r);
    }

    private static int applySaturation(int abgr, double saturation) {
        int a = FastColor.ABGR32.alpha(abgr);
        int r = FastColor.ABGR32.red(abgr);
        int g = FastColor.ABGR32.green(abgr);
        int b = FastColor.ABGR32.blue(abgr);

        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        int newR = clampChannel((int) Math.round(luminance + (r - luminance) * saturation));
        int newG = clampChannel((int) Math.round(luminance + (g - luminance) * saturation));
        int newB = clampChannel((int) Math.round(luminance + (b - luminance) * saturation));
        return FastColor.ABGR32.color(a, newB, newG, newR);
    }

    private static int applyContrast(int abgr, double contrast) {
        int a = FastColor.ABGR32.alpha(abgr);
        int r = clampChannel((int) ((FastColor.ABGR32.red(abgr) - 128) * contrast) + 128);
        int g = clampChannel((int) ((FastColor.ABGR32.green(abgr) - 128) * contrast) + 128);
        int b = clampChannel((int) ((FastColor.ABGR32.blue(abgr) - 128) * contrast) + 128);

        return FastColor.ABGR32.color(a, b, g, r);
    }

    private static int scaleBrightness(int abgr, double factor) {
        int a = abgr >>> 24;
        int r = clampChannel((int) ((abgr & 255) * factor));
        int g = clampChannel((int) ((abgr >> 8 & 255) * factor));
        int b = clampChannel((int) ((abgr >> 16 & 255) * factor));
        return a << 24 | b << 16 | g << 8 | r;
    }

    private static int scaleChannels(int abgr, double rFactor, double gFactor, double bFactor) {
        int a = abgr >>> 24;
        int r = clampChannel((int) ((abgr & 255) * rFactor));
        int g = clampChannel((int) ((abgr >> 8 & 255) * gFactor));
        int b = clampChannel((int) ((abgr >> 16 & 255) * bFactor));
        return a << 24 | b << 16 | g << 8 | r;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public void invalidate() {
        lastCenter = null;
    }

    public static void invalidateAll() {
        for (SolarisTexture texture : ACTIVE) texture.invalidate();
    }

    @Override
    public void close() {
        ACTIVE.remove(this);
        texture.close();
        if (globeTexture != null) globeTexture.close();
        baseImage.close();
    }
}
