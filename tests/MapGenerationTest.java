// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.color.BiomeGrassColor;
import java.util.concurrent.*;

public final class MapGenerationTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        check(BoundedMapExecutor.workerCount(2, 0) == 0, "few cores use the client thread");
        check(BoundedMapExecutor.workerCount(4, .2) == 2, "reserve two cores");
        check(BoundedMapExecutor.workerCount(32, .2) == 4, "bound worker count");
        check(BoundedMapExecutor.workerCount(16, .81) == 0, "busy CPU avoids extra work");
        check(BoundedMapExecutor.workerCount(8, -1) == 4, "unknown load uses conservative core reserve");
        check(BiomeGrassColor.pixel(0xFF8040, 255, false, true) == 0xFF4080FF, "RGB grass converts to older texture ABGR");
        check(BiomeGrassColor.pixel(0xFF8040, 255, false, false) == 0xFFFF8040, "newer texture retains ARGB channel order");
        check(BiomeGrassColor.pixel(0xFFFFFF, 180, true, false) == 0xFF878787, "leaves preserve shade and brightness");
        check(BiomeGrassColor.allowsDimension("minecraft:overworld"), "overworld has tint");
        check(!BiomeGrassColor.allowsDimension("minecraft:the_nether") && !BiomeGrassColor.allowsDimension("minecraft:the_end"), "Nether and End keep map colors");
        scanColumns();
        ceilingColumns();
        boundedCancellation();
        System.out.println("Map generation tests passed (" + assertions + " assertions).");
    }

    private static void scanColumns() throws Exception {
        FakeColumns world = new FakeColumns();
        ChunkColumnScan.Prepared surface = ChunkColumnScan.prepare(world, Integer.MIN_VALUE);
        check(surface.tops()[0] == 10, "surface skips air above height candidate");
        check(surface.depths()[0] == 3, "fluid depth includes first solid block like vanilla");
        check(surface.tops()[1] == 12, "other columns retain opaque surface");
        ChunkColumnScan.Prepared cave = ChunkColumnScan.prepare(world, 8);
        check(cave.spaces()[0] == 8 && cave.floors()[0] == 7, "cave water finds the floor");
        check(cave.spaces()[1] == ChunkColumnScan.NO_SPACE && cave.floors()[1] == 8, "solid cave wall stays at requested slice");
        ChunkColumnScan.Prepared deep = ChunkColumnScan.prepare(new TallCaveColumns(), 128);
        check(deep.spaces()[0] == 128 && deep.floors()[0] == 0,
                "deep cave worker scan reaches the real floor beyond 64 blocks");
        ChunkColumnScan.Prepared high = ChunkColumnScan.prepare(world, 40);
        check(high.spaces()[0] == ChunkColumnScan.NO_SPACE, "worker leaves above-surface column for sampler surface fallback");
        world.empty = true;
        ChunkColumnScan.Prepared empty = ChunkColumnScan.prepare(world, Integer.MIN_VALUE);
        check(empty.tops()[0] == -1, "empty columns preserve below-minimum height");
        Thread.currentThread().interrupt();
        try { ChunkColumnScan.prepare(world, 0); throw new AssertionError("cancelled scan must stop"); }
        catch (InterruptedException expected) { assertions++; }
        finally { Thread.interrupted(); }
    }

    private static void ceilingColumns() throws Exception {
        CeilingColumns world = new CeilingColumns(0);
        ChunkColumnScan.Prepared surface = ChunkColumnScan.prepare(world, Integer.MIN_VALUE);
        check(surface.tops()[0] == 64, "Nether surface skips the roof and samples the interior floor");
        check(surface.tops()[1] == 34 && surface.depths()[1] == 4, "Nether lava retains its exposed surface and depth");
        check(surface.tops()[2] == 64, "a one-block roof pocket does not hide the main cavity");
        check(surface.tops()[3] == 64, "construction above the logical ceiling is excluded");
        check(surface.tops()[4] == 64, "a column without a roof still shows its exposed terrain");
        check(surface.tops()[5] == ChunkColumnScan.NO_SPACE, "solid columns have no exposed interior surface");
        check(surface.tops()[6] == ChunkColumnScan.NO_SPACE, "empty columns have no floor");
        ChunkColumnScan.Prepared negative = ChunkColumnScan.prepare(new CeilingColumns(-64), Integer.MIN_VALUE);
        check(negative.tops()[0] == 0, "ceiling selection honors negative build heights");
        ChunkColumnScan.Prepared cave = ChunkColumnScan.prepare(world, 8);
        check(cave.spaces()[0] == ChunkColumnScan.NO_SPACE && cave.floors()[0] == 8,
                "manual cave selection keeps its existing wall behavior");
    }

    private static void boundedCancellation() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (BoundedMapExecutor pool = new BoundedMapExecutor(1)) {
            Future<Integer> running = pool.trySubmit(() -> { started.countDown(); release.await(); return 1; });
            check(started.await(5, TimeUnit.SECONDS), "worker starts");
            Future<Integer> queued = pool.trySubmit(() -> 2);
            check(!pool.hasCapacity(), "full shared pool stops capture before copying chunks");
            check(pool.trySubmit(() -> 3) == null, "backpressure bounds queued snapshots");
            check(queued.cancel(true), "queued snapshot cancels");
            check(pool.hasCapacity(), "cancelled queue permits capture again");
            Future<Integer> replacement = pool.trySubmit(() -> 4);
            check(replacement != null, "cancellation frees queue capacity");
            release.countDown();
            check(running.get(5, TimeUnit.SECONDS) == 1 && replacement.get(5, TimeUnit.SECONDS) == 4, "replacement executes, cancelled job does not");
            pool.close();
            check(pool.trySubmit(() -> 5) == null, "closed pool accepts no world data");
        } finally { release.countDown(); }
    }

    private static final class FakeColumns implements ChunkColumnScan.Columns {
        boolean empty;
        public int minY() { return 0; }
        public int maxYExclusive() { return 32; }
        public int surface(int x, int z) { return empty ? -1 : 12; }
        public boolean air(int x, int y, int z) { return empty || (x == 0 && y > 10); }
        public boolean fluid(int x, int y, int z) { return !empty && x == 0 && y >= 8 && y <= 10; }
    }
    private static final class CeilingColumns implements ChunkColumnScan.Columns {
        private final int min;
        CeilingColumns(int min) { this.min = min; }
        public int minY() { return min; }
        public int maxYExclusive() { return min + 256; }
        public boolean hasCeiling() { return true; }
        public int ceilingYExclusive() { return min + 128; }
        public int surface(int x, int z) { return x == 6 ? min - 1 : min + (x == 3 ? 150 : x == 4 ? 64 : 127); }
        public boolean air(int x, int y, int z) {
            int localY = y - min;
            if (x == 6) return true;
            if (x == 4) return localY > 64;
            if (x == 3 && localY == 150) return false;
            if (localY > 127 || localY < 0) return true;
            if (x == 5 || localY >= 123) return false;
            if (x == 2 && localY == 121) return true;
            if (x == 2 && localY >= 120) return false;
            return localY > (x == 1 ? 34 : 64);
        }
        public boolean fluid(int x, int y, int z) { return x == 1 && y - min >= 31 && y - min <= 34; }
    }

    private static final class TallCaveColumns implements ChunkColumnScan.Columns {
        public int minY() { return 0; }
        public int maxYExclusive() { return 256; }
        public int surface(int x, int z) { return 200; }
        public boolean air(int x, int y, int z) { return y > 0 && y < 200; }
        public boolean fluid(int x, int y, int z) { return false; }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
