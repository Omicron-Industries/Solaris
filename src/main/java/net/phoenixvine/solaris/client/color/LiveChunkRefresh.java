package net.phoenixvine.solaris.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LiveChunkRefresh {

    private static int tickCounter = 0;

    private LiveChunkRefresh() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        int intervalTicks = SolarisConfig.LIVE_REFRESH_INTERVAL_SECONDS.get() * 20;
        if (++tickCounter < intervalTicks) return;
        tickCounter = 0;

        boolean writeEnabled = SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WORLD_MAP, level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED);

        if (writeEnabled && !level.dimensionType().hasCeiling()) {
            long sweepStart = SolarisProfiler.start();
            int radius = Math.min(SolarisConfig.LIVE_REFRESH_RADIUS_CHUNKS.get(),
                    SolarisConfig.WORLD_MAP_WRITE_RANGE_CHUNKS.get());
            int centerX = player.chunkPosition().x;
            int centerZ = player.chunkPosition().z;

            boolean refreshedAny = false;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;

                    if (!level.hasChunk(chunkX, chunkZ)) continue;

                    ChunkKey key = ChunkKey.of(level, new ChunkPos(chunkX, chunkZ));
                    SolarisProfiler.time("chunkSample", () -> {
                        ChunkColorSampler.ColorSample colorSample = ChunkColorSampler.sample(level, key.toChunkPos());
                        ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level,
                                key.toChunkPos());
                        ChunkColorCache.put(key, colorSample.pixels);
                        ChunkLightCache.put(key, colorSample.lightEmitting);
                        ChunkHeightCache.put(key, heightSample.heights);
                        ChunkWaterCache.put(key, heightSample.water);
                        ChunkRailCache.put(key, heightSample.rails);
                        ChunkWaterTintCache.put(key, colorSample.waterTint);
                        ChunkWaterDepthCache.put(key, colorSample.waterDepth);
                        ChunkWaterOceanCache.put(key, colorSample.waterOcean);
                        ChunkFoliageCache.put(key, colorSample.foliage);

                        PersistentChunkStore.put(key, colorSample.pixels, heightSample.heights,
                                heightSample.water, colorSample.waterTint, colorSample.waterDepth,
                                colorSample.waterOcean, colorSample.foliage);
                        if (colorSample.hadFallback) {
                            ChunkColorEvents.FALLBACK_TAINTED.add(key);
                        } else {
                            ChunkColorEvents.FALLBACK_TAINTED.remove(key);
                        }
                    });
                    refreshedAny = true;
                }
            }

            int writeRange = SolarisConfig.WORLD_MAP_WRITE_RANGE_CHUNKS.get();
            for (int dz = -writeRange; dz <= writeRange; dz++) {
                for (int dx = -writeRange; dx <= writeRange; dx++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= radius) continue;
                    if (!level.hasChunk(chunkX, chunkZ)) continue;

                    ChunkKey key = ChunkKey.of(level, new ChunkPos(chunkX, chunkZ));
                    if (ChunkColorCache.contains(key)) continue;

                    ChunkColorEvents.resample(level, key, key.toChunkPos());
                    refreshedAny = true;
                }
            }

            if (!ChunkColorEvents.FALLBACK_TAINTED.isEmpty()) {
                for (ChunkKey key : List.copyOf(ChunkColorEvents.FALLBACK_TAINTED)) {
                    if (!level.hasChunk(key.x(), key.z())) continue;
                    ChunkColorSampler.ColorSample colorSample = ChunkColorSampler.sample(level, key.toChunkPos());
                    ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level,
                            key.toChunkPos());
                    if (colorSample.hadFallback) continue;

                    ChunkColorCache.put(key, colorSample.pixels);
                    ChunkLightCache.put(key, colorSample.lightEmitting);
                    ChunkHeightCache.put(key, heightSample.heights);
                    ChunkWaterCache.put(key, heightSample.water);
                    ChunkRailCache.put(key, heightSample.rails);
                    ChunkWaterTintCache.put(key, colorSample.waterTint);
                    ChunkWaterDepthCache.put(key, colorSample.waterDepth);
                    ChunkWaterOceanCache.put(key, colorSample.waterOcean);
                    ChunkFoliageCache.put(key, colorSample.foliage);
                    PersistentChunkStore.put(key, colorSample.pixels, heightSample.heights, heightSample.water,
                            colorSample.waterTint, colorSample.waterDepth, colorSample.waterOcean,
                            colorSample.foliage);
                    ChunkColorEvents.FALLBACK_TAINTED.remove(key);
                    refreshedAny = true;
                }
            }
            SolarisProfiler.end("liveRefreshSweep", sweepStart);

            if (refreshedAny) SolarisTexture.invalidateAll();
        }

        PersistentChunkStore.saveDirtyAsync();
    }
}
