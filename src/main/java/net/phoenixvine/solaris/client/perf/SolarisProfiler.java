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

/**
 * A minimap/map mod's cost is easy to miss: it never throws, never shows a red overlay, it just
 * quietly adds a few milliseconds to chunk loads and periodic rebuilds — individually invisible,
 * but a real, easy-to-blame-on-something-else source of stutter over a play session. This wraps
 * Solaris's own genuinely expensive operations (texture rebuilds, chunk sampling, the water blur
 * pass) with cheap timing, and reports two ways: an immediate warning for any single call that's
 * unusually slow ({@link SolarisConfig#PERF_LOG_THRESHOLD_MS}), and a periodic count/average/max
 * summary ({@link SolarisConfig#PERF_SUMMARY_INTERVAL_SECONDS}) that catches the other failure
 * mode — something cheap per call but running often enough to add up. Both log through the
 * ordinary mod logger, so they show up in the same log file players already know to check.
 *
 * Overhead when {@link SolarisConfig#PERF_LOGGING} is off is a single volatile-ish config read
 * per call site, no timing or map bookkeeping at all — the profiler itself is never the "silent
 * killer" it exists to catch.
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SolarisProfiler {

    private static final Map<String, Stats> STATS = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private SolarisProfiler() {}

    /** Times a value-returning operation. Runs {@code task} unmeasured if perf logging is off. */
    public static <T> T time(String label, Supplier<T> task) {
        if (!SolarisConfig.PERF_LOGGING.get()) return task.get();
        long start = System.nanoTime();
        T result = task.get();
        record(label, System.nanoTime() - start);
        return result;
    }

    /** Times a void operation. Runs {@code task} unmeasured if perf logging is off. */
    public static void time(String label, Runnable task) {
        if (!SolarisConfig.PERF_LOGGING.get()) {
            task.run();
            return;
        }
        long start = System.nanoTime();
        task.run();
        record(label, System.nanoTime() - start);
    }

    /**
     * Manual-span start, for call sites where wrapping in a lambda is awkward (e.g. mid-method). Paired with
     * {@link #end}.
     */
    public static long start() {
        return System.nanoTime();
    }

    /**
     * No-op if perf logging is off — {@code startNanos} is still whatever {@link #start()} returned, just discarded.
     */
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
