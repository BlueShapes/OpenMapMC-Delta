// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.*;
import java.util.function.LongPredicate;

/** Nearest-first scan passes that retain unloaded chunks and retry invalidated work. */
public final class ChunkScanQueue {
    private final Set<Long> inFlight = new HashSet<>();
    private final Set<Long> completed = new HashSet<>();
    private final Map<Long, Integer> invalidations = new HashMap<>();
    private NavigableSet<Long> waiting = new TreeSet<>();
    private int centerX, centerZ;

    public void reset(int x, int z, int radius) {
        reset(x, z, x, z, radius);
    }

    public void reset(int x, int z, int viewX, int viewZ, int radius) {
        centerX = x; centerZ = z;
        inFlight.clear(); completed.clear(); invalidations.clear();
        waiting = new TreeSet<>(Comparator.comparingDouble(this::distanceSquared).thenComparingLong(Long::longValue));
        addArea(x, z, radius);
        addArea(viewX, viewZ, radius);
    }

    private void addArea(int x, int z, int radius) {
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) waiting.add(key(cx, cz));
        }
    }

    public Long pollReady(LongPredicate ready) {
        Long selected = select(ready);
        if (selected == null && inFlight.isEmpty() && !completed.isEmpty()) {
            // Refresh loaded terrain while still retaining gaps that arrive after the teleport.
            waiting.addAll(completed); completed.clear();
            selected = select(ready);
        }
        return selected;
    }

    private Long select(LongPredicate ready) {
        Iterator<Long> iterator = waiting.iterator();
        while (iterator.hasNext()) {
            long key = iterator.next();
            if (!ready.test(key)) continue;
            iterator.remove(); inFlight.add(key);
            return key;
        }
        return null;
    }

    public void retry(long key) { if (inFlight.remove(key)) waiting.add(key); }
    public void invalidated(long key) {
        if (inFlight.contains(key)) {
            invalidations.merge(key, 1, Integer::sum);
            retry(key);
        }
    }
    public boolean needsLiveScan(long key) { return invalidations.getOrDefault(key, 0) >= 3; }
    public void complete(long key) {
        if (inFlight.remove(key)) { completed.add(key); invalidations.remove(key); }
    }
    public void clear() { waiting.clear(); inFlight.clear(); completed.clear(); invalidations.clear(); }

    /** Distance to the nearest block, so the player's own chunk wins at chunk boundaries. */
    public double distanceSquared(long key) {
        double startX = x(key) * 16.0, startZ = z(key) * 16.0;
        double dx = Math.max(startX, Math.min(startX + 15, centerX)) - centerX;
        double dz = Math.max(startZ, Math.min(startZ + 15, centerZ)) - centerZ;
        return dx * dx + dz * dz;
    }

    /** Prioritize nearby blocks too when a chunk needs several ticks to finish applying. */
    public int[] columnOrder(long key) {
        Integer[] order = new Integer[256];
        for (int i = 0; i < order.length; i++) order[i] = i;
        double startX = x(key) * 16.0, startZ = z(key) * 16.0;
        Arrays.sort(order, Comparator.comparingDouble(i -> {
            double dx = startX + (i & 15) - centerX, dz = startZ + (i >> 4) - centerZ;
            return dx * dx + dz * dz;
        }));
        int[] result = new int[256];
        for (int i = 0; i < result.length; i++) result[i] = order[i];
        return result;
    }

    public static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    public static int x(long key) { return (int) (key >> 32); }
    public static int z(long key) { return (int) key; }
}
