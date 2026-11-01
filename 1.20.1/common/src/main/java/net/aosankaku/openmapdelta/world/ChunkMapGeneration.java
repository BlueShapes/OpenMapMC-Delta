// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Future;

/** Surface and cave scanners share the same spare-core budget. Accessed on the client thread. */
public final class ChunkMapGeneration {
    private static BoundedMapExecutor pool;
    private static long nextLoadSample;
    private static double cpuLoad = -1;
    private ChunkMapGeneration() {}

    public static int workers() {
        if (!OpenMapDeltaConfig.ASYNC_MAP_GENERATION.get()) { close(); return 0; }
        long now = System.nanoTime();
        if (now >= nextLoadSample) {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            cpuLoad = bean instanceof com.sun.management.OperatingSystemMXBean extended ? extended.getCpuLoad() : -1;
            nextLoadSample = now + 1_000_000_000L;
        }
        return BoundedMapExecutor.workerCount(Runtime.getRuntime().availableProcessors(), cpuLoad);
    }

    /** All submissions originate on the client thread, so capacity cannot be consumed during capture. */
    public static boolean hasCapacity() { return pool == null || pool.hasCapacity(); }

    public static Future<ChunkColumnScan.Prepared> submit(ChunkMapSnapshot snapshot, int layer) {
        int workers = workers();
        if (workers == 0) return null;
        if (pool == null) pool = new BoundedMapExecutor(workers);
        return pool.trySubmit(() -> ChunkColumnScan.prepare(snapshot, layer));
    }

    public static void close() {
        if (pool != null) pool.close();
        pool = null;
    }
}
