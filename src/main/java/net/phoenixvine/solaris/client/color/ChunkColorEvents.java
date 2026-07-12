package net.phoenixvine.solaris.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.config.SolarisConfig;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChunkColorEvents {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide()) return;
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk)) return;
        // WORLD_SURFACE-based sampling is meaningless in a ceiling dimension (the Nether) — see
        // SolarisTexture.isCaveSliceMode's doc — and SolarisTexture never reads this cache for
        // one, so sampling/persisting it here would just be wasted CPU and disk for data nothing
        // ever looks at.
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

        // If the chunk to the south was already sampled before this one loaded, its north edge
        // (row lz=0) fell back to flat shading — see ChunkColorSampler's class doc — since it
        // had no loaded neighbor to compare against at the time. That fallback is otherwise
        // permanent (chunks only resample on load, not periodically), which is what actually
        // caused persistent thin-line artifacts along whichever chunk edges happened to sample
        // before their neighbor: this doesn't depend on chunk load order anymore, since the
        // south chunk gets a second, correct sample as soon as this one becomes available.
        ChunkKey southKey = new ChunkKey(key.dimension(), key.x(), key.z() + 1);
        if (ChunkColorCache.contains(southKey)) {
            SolarisProfiler.time("chunkSample", () -> resample(level, southKey, southKey.toChunkPos()));
        }
    }

    /**
     * Shared with {@code S2CForceUpdateChunkPacket}'s client-side handler — "force update a
     * chunk" is just this same sample-and-cache logic, invoked directly rather than through the
     * gate/range checks above (a force-update is explicitly meant to bypass those).
     */
    public static void resample(Level level, ChunkKey key, net.minecraft.world.level.ChunkPos pos) {
        int[] pixels = ChunkColorSampler.sample(level, pos);
        ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level, pos);
        ChunkColorCache.put(key, pixels);
        ChunkHeightCache.put(key, heightSample.heights);
        ChunkWaterCache.put(key, heightSample.water);
        ChunkRailCache.put(key, heightSample.rails);
        PersistentChunkStore.put(key, pixels, heightSample.heights, heightSample.water);
    }
}
