// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serial asynchronous disk coordinator; it never reads Minecraft objects or live tiles. */
public final class WorldCache {
    private static final System.Logger LOGGER = System.getLogger("OpenMapDelta-TileCache");
    private static final int AREA_BATCH_SIZE = 8;
    private static final int MAX_COMPLETED_AREA_LOADS = 32;

    private final Executor worker;
    private final ExecutorService ownedWorker;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final Set<TileCoordinate> requestedLoads = new HashSet<>();
    private final ConcurrentLinkedQueue<LoadResult> completedLoads = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<LoadResult> completedAreaLoads = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<SaveResult> completedSaves = new ConcurrentLinkedQueue<>();
    private TileStorage activeStorage;
    private TileStorage indexedStorage;
    private Set<TileStorage.StoredTileCoordinate> storedCoordinates;
    private final ArrayDeque<TileStorage.StoredTileCoordinate> areaCandidates = new ArrayDeque<>();
    private AreaRequest latestArea;
    private long areaCandidateRequestId = -1;
    private int areaPlanBuildCount;
    private long nextAreaRequestId;
    private long scheduledAreaGeneration = -1;
    private boolean areaNeedsWork;
    private boolean pollPrefetchFirst;
    private long generation;

    public WorldCache() {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "OpenMapDelta-TileCache");
            thread.setDaemon(true);
            return thread;
        });
        this.worker = executor;
        this.ownedWorker = executor;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "OpenMapDelta-TileCacheShutdown"));
        } catch (IllegalStateException | SecurityException ignored) {
            // The worker remains daemon-backed if JVM shutdown has already started.
        }
    }

    /** Injection point for deterministic pure-JDK tests. */
    public WorldCache(Executor worker) {
        this.worker = worker;
        this.ownedWorker = null;
    }

    /** Independent session state on this owner's serial worker; the owner outlives its siblings. */
    WorldCache createSibling() { return new WorldCache(worker); }

    public synchronized long beginSession(TileStorage storage) {
        generation++;
        activeStorage = storage;
        requestedLoads.clear();
        completedAreaLoads.clear();
        indexedStorage = null;
        storedCoordinates = null;
        latestArea = null;
        areaCandidates.clear();
        areaCandidateRequestId = -1;
        areaPlanBuildCount = 0;
        areaNeedsWork = false;
        scheduledAreaGeneration = -1;
        return generation;
    }

    public synchronized long getGeneration() { return generation; }

    public synchronized boolean requestLoad(int alignedX, int alignedZ) {
        if (activeStorage == null) return false;
        TileCoordinate coordinate = new TileCoordinate(alignedX, alignedZ);
        if (!requestedLoads.add(coordinate)) return false;
        TileStorage storage = activeStorage;
        long requestGeneration = generation;
        worker.execute(() -> {
            synchronized (WorldCache.this) {
                if (requestGeneration != generation || storage != activeStorage) return;
            }
            Tile.Snapshot snapshot = storage.load(alignedX, alignedZ).orElse(null);
            completedLoads.add(new LoadResult(requestGeneration, storage, alignedX, alignedZ, snapshot));
        });
        return true;
    }

    /** Requests only already-stored tiles intersecting an inclusive, tile-aligned rectangle. */
    public synchronized boolean requestArea(int minAlignedX, int minAlignedZ, int maxAlignedX, int maxAlignedZ) {
        if (activeStorage == null) return false;
        TileStorage.StoredTileCoordinate min = new TileStorage.StoredTileCoordinate(minAlignedX, minAlignedZ);
        TileStorage.StoredTileCoordinate max = new TileStorage.StoredTileCoordinate(maxAlignedX, maxAlignedZ);
        if (minAlignedX > maxAlignedX || minAlignedZ > maxAlignedZ) {
            throw new IllegalArgumentException("Area bounds must be ordered");
        }
        if (latestArea != null && latestArea.generation() == generation
                && latestArea.minX() == minAlignedX && latestArea.minZ() == minAlignedZ
                && latestArea.maxX() == maxAlignedX && latestArea.maxZ() == maxAlignedZ) return false;

        latestArea = new AreaRequest(generation, ++nextAreaRequestId,
                min.alignedX(), min.alignedZ(), max.alignedX(), max.alignedZ());
        areaCandidates.clear();
        areaCandidateRequestId = -1;
        areaNeedsWork = true;
        scheduleAreaWorkerLocked();
        return true;
    }

    public synchronized void save(int alignedX, int alignedZ, long contentRevision, Tile.Snapshot snapshot) {
        if (activeStorage == null || snapshot == null || !snapshot.hasObservedPixels()) return;
        enqueueSave(activeStorage, generation, alignedX, alignedZ, contentRevision, snapshot);
    }

    /** Queues final snapshots behind pending loads/saves, then makes their results stale. */
    public synchronized void endSession(Collection<TileWrite> finalWrites) {
        TileStorage storage = activeStorage;
        long endingGeneration = generation;
        if (storage != null) {
            for (TileWrite write : finalWrites) {
                if (write.snapshot().hasObservedPixels()) {
                    enqueueSave(storage, endingGeneration, write.alignedX(), write.alignedZ(),
                            write.contentRevision(), write.snapshot());
                }
            }
        }
        generation++;
        activeStorage = null;
        requestedLoads.clear();
        completedAreaLoads.clear();
        indexedStorage = null;
        storedCoordinates = null;
        latestArea = null;
        areaCandidates.clear();
        areaCandidateRequestId = -1;
        areaNeedsWork = false;
        scheduledAreaGeneration = -1;
    }

    /** Returns only load results belonging to the current world/dimension session. */
    public List<LoadResult> pollLoads() {
        return pollLoads(Integer.MAX_VALUE);
    }

    /** Returns at most {@code maxResults} active-session tile loads and resumes paused area work. */
    public List<LoadResult> pollLoads(int maxResults) {
        if (maxResults < 0) throw new IllegalArgumentException("maxResults must not be negative");
        long currentGeneration;
        TileStorage currentStorage;
        List<LoadResult> results = new ArrayList<>();
        synchronized (this) {
            currentGeneration = generation;
            currentStorage = activeStorage;
            while (results.size() < maxResults) {
                LoadResult result;
                if (pollPrefetchFirst) {
                    result = completedAreaLoads.pollFirst();
                    if (result == null) result = completedLoads.poll();
                } else {
                    result = completedLoads.poll();
                    if (result == null) result = completedAreaLoads.pollFirst();
                }
                pollPrefetchFirst = !pollPrefetchFirst;
                if (result == null) break;
                if (result.generation() == currentGeneration && result.storage() == currentStorage
                        && currentStorage != null) results.add(result);
            }
            scheduleAreaWorkerLocked();
        }
        return results;
    }

    /** Returns and consumes save acknowledgments from the active session. */
    public List<SaveResult> pollSaveResults() {
        long currentGeneration;
        TileStorage currentStorage;
        synchronized (this) {
            currentGeneration = generation;
            currentStorage = activeStorage;
        }
        List<SaveResult> results = new ArrayList<>();
        SaveResult result;
        while ((result = completedSaves.poll()) != null) {
            if (result.generation() == currentGeneration && result.storage() == currentStorage
                    && currentStorage != null) results.add(result);
        }
        return results;
    }

    private void enqueueSave(TileStorage storage, long saveGeneration, int alignedX, int alignedZ,
                             long contentRevision, Tile.Snapshot snapshot) {
        worker.execute(() -> {
            Throwable failure = null;
            try {
                storage.saveMerged(alignedX, alignedZ, snapshot);
            } catch (IOException | RuntimeException exception) {
                failure = exception;
                LOGGER.log(System.Logger.Level.ERROR,
                        "Failed to save map tile " + alignedX + "," + alignedZ, exception);
            }
            if (failure == null) {
                synchronized (WorldCache.this) {
                    if (generation == saveGeneration && activeStorage == storage && indexedStorage == storage
                            && storedCoordinates != null) {
                        TileStorage.StoredTileCoordinate coordinate =
                                new TileStorage.StoredTileCoordinate(alignedX, alignedZ);
                        storedCoordinates.add(coordinate);
                        TileCoordinate requestCoordinate = new TileCoordinate(alignedX, alignedZ);
                        if (latestArea != null && areaCandidateRequestId == latestArea.requestId()
                                && inArea(coordinate, latestArea) && !requestedLoads.contains(requestCoordinate)) {
                            areaCandidates.clear();
                            areaCandidateRequestId = -1;
                            areaNeedsWork = true;
                            scheduleAreaWorkerLocked();
                        }
                    }
                }
            }
            completedSaves.add(new SaveResult(saveGeneration, storage, alignedX, alignedZ, contentRevision, failure));
        });
    }

    private void scheduleAreaWorkerLocked() {
        if (shutdownStarted.get() || activeStorage == null || !areaNeedsWork || latestArea == null
                || scheduledAreaGeneration == generation || completedAreaLoads.size() >= MAX_COMPLETED_AREA_LOADS) return;
        long requestGeneration = generation;
        scheduledAreaGeneration = requestGeneration;
        worker.execute(() -> processAreaBatch(requestGeneration));
    }

    private void processAreaBatch(long requestGeneration) {
        TileStorage storage;
        AreaRequest area;
        synchronized (this) {
            if (requestGeneration != generation || activeStorage == null) {
                clearScheduledAreaWorkerLocked(requestGeneration);
                return;
            }
            storage = activeStorage;
            area = latestArea;
        }
        if (area == null || area.generation() != requestGeneration) {
            finishAreaBatch(requestGeneration, null);
            return;
        }

        boolean discoverIndex;
        synchronized (this) {
            discoverIndex = indexedStorage != storage || storedCoordinates == null;
        }
        if (discoverIndex) {
            List<TileStorage.StoredTileCoordinate> discovered = storage.listStoredTiles();
            synchronized (this) {
                if (requestGeneration != generation || storage != activeStorage) {
                    clearScheduledAreaWorkerLocked(requestGeneration);
                    return;
                }
                if (indexedStorage != storage || storedCoordinates == null) {
                    indexedStorage = storage;
                    storedCoordinates = new HashSet<>(discovered);
                }
            }
        }

        boolean buildPlan;
        List<TileStorage.StoredTileCoordinate> indexedTiles = List.of();
        synchronized (this) {
            if (requestGeneration != generation || storage != activeStorage) {
                clearScheduledAreaWorkerLocked(requestGeneration);
                return;
            }
            area = latestArea;
            buildPlan = areaCandidateRequestId != area.requestId();
            if (buildPlan) indexedTiles = List.copyOf(storedCoordinates);
        }
        if (buildPlan) {
            AreaRequest selectedArea = area;
            List<TileStorage.StoredTileCoordinate> sortedCandidates = indexedTiles.stream()
                    .filter(coordinate -> inArea(coordinate, selectedArea))
                    .sorted(Comparator.<TileStorage.StoredTileCoordinate>comparingDouble(
                                    coordinate -> distanceSquared(coordinate, selectedArea))
                            .thenComparingInt(TileStorage.StoredTileCoordinate::alignedX)
                            .thenComparingInt(TileStorage.StoredTileCoordinate::alignedZ))
                    .toList();
            synchronized (this) {
                if (requestGeneration != generation || storage != activeStorage) {
                    clearScheduledAreaWorkerLocked(requestGeneration);
                    return;
                }
                if (latestArea == null || latestArea.requestId() != selectedArea.requestId()) {
                    finishAreaBatch(requestGeneration, selectedArea);
                    return;
                }
                areaCandidates.clear();
                areaCandidates.addAll(sortedCandidates);
                areaCandidateRequestId = selectedArea.requestId();
                areaPlanBuildCount++;
            }
        }

        int examined = 0;
        while (examined < AREA_BATCH_SIZE) {
            TileStorage.StoredTileCoordinate coordinate;
            synchronized (this) {
                if (requestGeneration != generation || storage != activeStorage) {
                    clearScheduledAreaWorkerLocked(requestGeneration);
                    return;
                }
                if (latestArea == null || latestArea.requestId() != area.requestId()) {
                    break;
                }
                if (completedAreaLoads.size() >= MAX_COMPLETED_AREA_LOADS) {
                    break;
                }
                coordinate = areaCandidates.pollFirst();
                if (coordinate == null) break;
                examined++;
                if (!requestedLoads.add(new TileCoordinate(coordinate.alignedX(), coordinate.alignedZ()))) continue;
            }
            Tile.Snapshot snapshot = storage.load(coordinate.alignedX(), coordinate.alignedZ()).orElse(null);
            if (snapshot != null) {
                synchronized (this) {
                    if (requestGeneration == generation && storage == activeStorage
                            && completedAreaLoads.size() < MAX_COMPLETED_AREA_LOADS) {
                        completedAreaLoads.addLast(new LoadResult(requestGeneration, storage,
                                coordinate.alignedX(), coordinate.alignedZ(), snapshot));
                    }
                }
            }
        }

        finishAreaBatch(requestGeneration, area);
    }

    private synchronized void finishAreaBatch(long requestGeneration, AreaRequest processedArea) {
        if (scheduledAreaGeneration == requestGeneration) scheduledAreaGeneration = -1;
        if (requestGeneration != generation || activeStorage == null || latestArea == null) return;
        if (processedArea == null || latestArea.requestId() != processedArea.requestId()) {
            areaNeedsWork = true;
        } else if (completedAreaLoads.size() >= MAX_COMPLETED_AREA_LOADS) {
            areaNeedsWork = !areaCandidates.isEmpty();
        } else {
            areaNeedsWork = !areaCandidates.isEmpty();
        }
        scheduleAreaWorkerLocked();
    }

    private static boolean inArea(TileStorage.StoredTileCoordinate coordinate, AreaRequest area) {
        return coordinate.alignedX() >= area.minX() && coordinate.alignedX() <= area.maxX()
                && coordinate.alignedZ() >= area.minZ() && coordinate.alignedZ() <= area.maxZ();
    }

    private static double distanceSquared(TileStorage.StoredTileCoordinate coordinate, AreaRequest area) {
        double centerX = ((double) area.minX() + area.maxX()) / 2.0;
        double centerZ = ((double) area.minZ() + area.maxZ()) / 2.0;
        double dx = coordinate.alignedX() - centerX;
        double dz = coordinate.alignedZ() - centerZ;
        return dx * dx + dz * dz;
    }

    private synchronized void clearScheduledAreaWorkerLocked(long requestGeneration) {
        if (scheduledAreaGeneration == requestGeneration) scheduledAreaGeneration = -1;
    }

    synchronized int areaPlanBuildCountForTest() { return areaPlanBuildCount; }

    public void close() {
        if (ownedWorker == null || !shutdownStarted.compareAndSet(false, true)) return;
        ownedWorker.shutdown();
        try {
            if (!ownedWorker.awaitTermination(3, TimeUnit.SECONDS)) ownedWorker.shutdownNow();
        } catch (InterruptedException interrupted) {
            ownedWorker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record TileCoordinate(int x, int z) {}

    private record AreaRequest(long generation, long requestId, int minX, int minZ, int maxX, int maxZ) {}

    public record TileWrite(int alignedX, int alignedZ, long contentRevision, Tile.Snapshot snapshot) {}

    public record LoadResult(long generation, TileStorage storage, int alignedX, int alignedZ,
                             Tile.Snapshot snapshot) {}

    public record SaveResult(long generation, TileStorage storage, int alignedX, int alignedZ,
                             long contentRevision, Throwable failure) {
        public boolean succeeded() { return failure == null; }
    }
}
