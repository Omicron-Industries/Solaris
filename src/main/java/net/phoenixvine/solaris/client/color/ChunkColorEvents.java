package net.phoenixvine.solaris.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChunkColorEvents {

    static final Set<ChunkKey> FALLBACK_TAINTED = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {

        SolarisTexture.invalidateAll();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide()) return;
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk)) return;

        if (level.dimensionType().hasCeiling()) return;

        if (!SolarisAPI.getFeatureState(SolarisAPI.FEATURE_WORLD_MAP, level.dimension().location())
                .atLeast(SolarisFeatureState.ENABLED)) {
            return;
        }

        var pos = event.getChunk().getPos();

        var player = Minecraft.getInstance().player;
        if (player != null) {
            int writeRange = SolarisConfig.WORLD_MAP_WRITE_RANGE_CHUNKS.get();
            int dx = pos.x - player.chunkPosition().x;
            int dz = pos.z - player.chunkPosition().z;
            if (Math.max(Math.abs(dx), Math.abs(dz)) > writeRange) return;
        }

        ChunkKey key = ChunkKey.of(level, pos);
        SolarisProfiler.time("chunkSample", () -> resample(level, key, pos));

        for (int[] d : new int[][] { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } }) {
            ChunkKey neighborKey = new ChunkKey(key.dimension(), key.x() + d[0], key.z() + d[1]);
            if (ChunkColorCache.contains(neighborKey)) {
                SolarisProfiler.time("chunkSample", () -> resample(level, neighborKey, neighborKey.toChunkPos()));
            }
        }
    }

    public static void resample(Level level, ChunkKey key, net.minecraft.world.level.ChunkPos pos) {
        ChunkColorSampler.ColorSample colorSample = ChunkColorSampler.sample(level, pos);
        ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level, pos);
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
                colorSample.waterTint, colorSample.waterDepth, colorSample.waterOcean, colorSample.foliage);
        if (colorSample.hadFallback) {
            FALLBACK_TAINTED.add(key);
        } else {
            FALLBACK_TAINTED.remove(key);
        }
    }
}
