package net.phoenixvine.solaris.client.perf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SolarisProfiler {

    private static final Map<String, Stats> STATS = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private SolarisProfiler() {}

    public static <T> T time(String label, Supplier<T> task) {
        if (!SolarisConfig.PERF_LOGGING.get()) return task.get();
        long start = System.nanoTime();
        T result = task.get();
        record(label, System.nanoTime() - start);
        return result;
    }

    public static void time(String label, Runnable task) {
        if (!SolarisConfig.PERF_LOGGING.get()) {
            task.run();
            return;
        }
        long start = System.nanoTime();
        task.run();
        record(label, System.nanoTime() - start);
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void end(String label, long startNanos) {
        if (!SolarisConfig.PERF_LOGGING.get()) return;
        record(label, System.nanoTime() - startNanos);
    }

    private static void record(String label, long elapsedNanos) {
        long elapsedMs = elapsedNanos / 1_000_000;
        STATS.computeIfAbsent(label, l -> new Stats()).add(elapsedMs);

        if (elapsedMs >= SolarisConfig.PERF_LOG_THRESHOLD_MS.get()) {
            PhoenixSolaris.LOGGER.warn("[Solaris perf] {} took {}ms", label, elapsedMs);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!SolarisConfig.PERF_LOGGING.get() || STATS.isEmpty()) return;

        int intervalTicks = SolarisConfig.PERF_SUMMARY_INTERVAL_SECONDS.get() * 20;
        if (++tickCounter < intervalTicks) return;
        tickCounter = 0;

        StringBuilder summary = new StringBuilder("[Solaris perf] summary:");
        for (Map.Entry<String, Stats> entry : STATS.entrySet()) {
            Stats stats = entry.getValue();
            summary.append(String.format(" %s(n=%d avg=%.1fms max=%dms)", entry.getKey(), stats.count,
                    stats.totalMs / (double) stats.count, stats.maxMs));
            stats.reset();
        }
        PhoenixSolaris.LOGGER.info(summary.toString());
    }

    private static final class Stats {

        private int count = 0;
        private long totalMs = 0;
        private long maxMs = 0;

        synchronized void add(long elapsedMs) {
            count++;
            totalMs += elapsedMs;
            maxMs = Math.max(maxMs, elapsedMs);
        }

        synchronized void reset() {
            count = 0;
            totalMs = 0;
            maxMs = 0;
        }
    }
}
