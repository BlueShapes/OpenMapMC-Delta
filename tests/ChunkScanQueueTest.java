// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.*;

public final class ChunkScanQueueTest {
    private static int assertions;
    public static void main(String[] args) {
        delayedTeleportChunks();
        staleAndBusyWork();
        refreshAndTeleportReset();
        boundaryAndColumnPriority();
        continuouslyChangingChunk();
        playerAndViewportUnion();
        System.out.println("Chunk scan queue tests passed (" + assertions + " assertions).");
    }

    private static void delayedTeleportChunks() {
        ChunkScanQueue queue = new ChunkScanQueue();
        int x = 16003, z = -32004;
        long center = ChunkScanQueue.key(x >> 4, z >> 4);
        long outer = ChunkScanQueue.key((x >> 4) + 3, z >> 4);
        long other = ChunkScanQueue.key((x >> 4) + 4, z >> 4);
        queue.reset(x, z, 64);
        Set<Long> loaded = new HashSet<>(List.of(outer, other));
        check(queue.pollReady(loaded::contains) == outer, "already loaded terrain can progress while nearby chunks are missing");
        queue.complete(outer);
        loaded.add(center);
        check(queue.pollReady(loaded::contains) == center, "late nearby chunk wins immediately before the rest of the outer pass");
        queue.complete(center);
        check(queue.pollReady(loaded::contains) == other, "remaining terrain progresses without losing pending chunks");
    }

    private static void staleAndBusyWork() {
        ChunkScanQueue queue = new ChunkScanQueue(); queue.reset(8, 8, 64);
        long center = ChunkScanQueue.key(0, 0), outer = ChunkScanQueue.key(3, 0);
        Set<Long> ready = Set.of(center, outer);
        check(queue.pollReady(ready::contains) == center, "near job starts first");
        check(queue.pollReady(ready::contains) == outer, "an in-flight chunk cannot be scheduled twice");
        queue.retry(center); queue.retry(center);
        check(queue.pollReady(ready::contains) == center, "packet-invalidated snapshot retries before a full pass");
        queue.retry(center); // A full shared executor must release the claim too.
        check(queue.pollReady(ready::contains) == center, "backpressure preserves the nearest job");
        check(queue.pollReady(ready::contains) == null, "in-flight jobs stay deduplicated");
    }

    private static void refreshAndTeleportReset() {
        ChunkScanQueue queue = new ChunkScanQueue(); queue.reset(0, 0, 192);
        long center = ChunkScanQueue.key(0, 0), distant = ChunkScanQueue.key(11, 11);
        Set<Long> ready = new HashSet<>(List.of(distant));
        check(queue.pollReady(ready::contains) == distant, "many missing chunks do not starve distant loaded terrain");
        queue.complete(distant);
        check(queue.pollReady(ready::contains) == distant, "loaded terrain refreshes despite persistently missing chunks");
        queue.complete(distant); ready.add(center);
        check(queue.pollReady(ready::contains) == center, "late center beats already completed outer chunks");
        queue.reset(1_000_000, -1_000_000, 64);
        queue.retry(center); queue.complete(distant);
        long destination = ChunkScanQueue.key(1_000_000 >> 4, -1_000_000 >> 4);
        check(queue.pollReady(key -> true) == destination, "teleport reset drops all old work and starts at destination");
        queue.clear();
        check(queue.pollReady(key -> true) == null, "logout clears pending and completed work");
    }

    private static void boundaryAndColumnPriority() {
        for (int[] point : new int[][]{{0, 0}, {-1, -1}, {15, 15}, {-16, 32}, {30_000_000, -30_000_000}}) {
            ChunkScanQueue queue = new ChunkScanQueue(); queue.reset(point[0], point[1], 64);
            long own = ChunkScanQueue.key(point[0] >> 4, point[1] >> 4);
            check(queue.pollReady(key -> true) == own, "own chunk wins at signed chunk boundaries");
            int[] columns = queue.columnOrder(own);
            check(columns[0] == (point[0] & 15) + (point[1] & 15) * 16, "player's block is applied first inside the chunk");
            Set<Integer> unique = new HashSet<>(); double previous = -1;
            for (int i : columns) {
                check(unique.add(i) && i >= 0 && i < 256, "each block appears exactly once");
                double dx = (i & 15) - (point[0] & 15), dz = (i >> 4) - (point[1] & 15);
                double distance = dx * dx + dz * dz;
                check(distance >= previous, "partial chunk application goes outwards from the player");
                previous = distance;
            }
        }
    }

    private static void continuouslyChangingChunk() {
        ChunkScanQueue queue = new ChunkScanQueue(); queue.reset(8, 8, 64);
        long center = ChunkScanQueue.key(0, 0), outer = ChunkScanQueue.key(3, 0);
        Set<Long> ready = Set.of(center, outer);
        queue.pollReady(ready::contains); queue.pollReady(ready::contains); queue.complete(outer);
        for (int changes = 1; changes <= 3; changes++) {
            queue.invalidated(center);
            check(queue.needsLiveScan(center) == (changes >= 3), "repeated packet changes activate bounded live sampling");
            check(queue.pollReady(ready::contains) == center, "busy near chunk keeps its priority");
        }
        queue.retry(center); // Partial live work continues instead of resetting its cursor.
        check(queue.pollReady(ready::contains) == center && queue.needsLiveScan(center), "partial live sampling stays active until complete");
        queue.complete(center);
        check(!queue.needsLiveScan(center), "successful scan permits workers on the next pass");
        check(queue.pollReady(ready::contains) == center, "completed terrain refreshes after the busy chunk progresses");
        check(queue.pollReady(ready::contains) == outer, "busy center no longer prevents outer refresh");
    }

    private static void playerAndViewportUnion() {
        ChunkScanQueue queue = new ChunkScanQueue();
        queue.reset(0, 0, 1_000_000, -1_000_000, 192);
        Set<Long> selected = new HashSet<>();
        Long key;
        check(queue.pollReady(candidate -> true) == ChunkScanQueue.key(0, 0), "player is prioritized even while viewport remains at old TP location");
        selected.add(ChunkScanQueue.key(0, 0));
        while ((key = queue.pollReady(candidate -> true)) != null) check(selected.add(key), "union deduplicates in-flight work");
        check(selected.size() == 1250, "separate player/view areas stay bounded without a connecting corridor");
        queue.reset(0, 0, 0, 0, 192);
        selected.clear();
        while ((key = queue.pollReady(candidate -> true)) != null) selected.add(key);
        check(selected.size() == 625, "overlapping player/view areas do not double the scan budget");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
