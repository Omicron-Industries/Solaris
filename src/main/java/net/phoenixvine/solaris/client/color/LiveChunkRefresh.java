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
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.config.SolarisConfig;

/**
 * {@link ChunkColorEvents} only ever re-samples a chunk when it (re)loads, so a block you place
 * in a chunk you're already standing in never shows up on the map until you leave and come back
 * — the cache has no other trigger to refresh it. Rather than a full block-edit event/mixin (more
 * invasive, and fires constantly for every single block change including other players' and
 * redstone/pistons far more often than a player actually wants to see the map catch up), this
 * takes a periodic, bounded sweep instead: every {@link SolarisConfig#LIVE_REFRESH_INTERVAL_SECONDS}
 * seconds, re-sample whatever's currently loaded within {@link SolarisConfig#LIVE_REFRESH_RADIUS_CHUNKS}
 * of the player (only actually-loaded chunks — never forces new ones in) and force any open
 * map/minimap to redraw from the refreshed cache. Bounded to a small radius specifically so this
 * stays cheap regardless of how large the player's overall explored area or configured map radius
 * is — it never touches more than a small, fixed working set per pass.
 */
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

        long sweepStart = SolarisProfiler.start();
        int radius = SolarisConfig.LIVE_REFRESH_RADIUS_CHUNKS.get();
        int centerX = player.chunkPosition().x;
        int centerZ = player.chunkPosition().z;

        boolean refreshedAny = false;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                // hasChunk is a cheap "is this already loaded" check — never forces a chunk in,
                // matching the read-only, best-effort spirit of this pass.
                if (!level.hasChunk(chunkX, chunkZ)) continue;

                ChunkKey key = ChunkKey.of(level, new ChunkPos(chunkX, chunkZ));
                SolarisProfiler.time("chunkSample", () -> {
                    int[] pixels = ChunkColorSampler.sample(level, key.toChunkPos());
                    ChunkColorSampler.HeightSample heightSample = ChunkColorSampler.sampleHeights(level,
                            key.toChunkPos());
                    ChunkColorCache.put(key, pixels);
                    ChunkHeightCache.put(key, heightSample.heights);
                    ChunkWaterCache.put(key, heightSample.water);
                    PersistentChunkStore.put(key, pixels, heightSample.heights, heightSample.water);
                });
                refreshedAny = true;
            }
        }
        SolarisProfiler.end("liveRefreshSweep", sweepStart);

        if (refreshedAny) SolarisTexture.invalidateAll();
        PersistentChunkStore.saveDirtyAsync();
    }
}
