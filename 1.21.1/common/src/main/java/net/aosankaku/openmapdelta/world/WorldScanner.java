// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class WorldScanner {
    private final TileCache tileCache;
    private final ChunkScanQueue chunkQueue = new ChunkScanQueue();
    private final List<Job> pending = new ArrayList<>();
    private final Map<Long, SyncProgress> syncProgress = new HashMap<>();
    private ResourceKey<Level> lastDimension;
    private int lastCenterX, lastCenterZ, lastViewX, lastViewZ, lastRadius;
    private boolean lastAsync, failedAsync;

    private static final class SyncProgress {
        final int[] columns;
        int nextColumn;
        SyncProgress(int[] columns) { this.columns = columns; }
    }

    private static final class Job {
        final ClientLevel level;
        final LevelChunk liveChunk;
        final ChunkMapSnapshot snapshot;
        final Future<ChunkColumnScan.Prepared> future;
        final long epoch, key;
        final int[] columns;
        int nextColumn;
        Job(ClientLevel level, LevelChunk liveChunk, ChunkMapSnapshot snapshot,
            Future<ChunkColumnScan.Prepared> future, long epoch, long key, int[] columns) {
            this.level = level; this.liveChunk = liveChunk; this.snapshot = snapshot;
            this.future = future; this.epoch = epoch; this.key = key; this.columns = columns;
        }
    }

    public WorldScanner(TileCache tileCache) { this.tileCache = tileCache; }

    /** Refresh focus before applying workers too, so TP does not wait for the scan interval. */
    private boolean ensureFocus(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        int x = Mth.floor(mc.player.getX()), z = Mth.floor(mc.player.getZ());
        int viewX = x, viewZ = z;
        if (mc.screen instanceof FullscreenMapScreen fullscreen) {
            viewX = Mth.floor(fullscreen.getPanX()); viewZ = Mth.floor(fullscreen.getPanZ());
        }
        int radius = OpenMapDeltaConfig.effectiveScanRadius();
        if (!mc.level.dimension().equals(lastDimension) || Math.abs((long) x - lastCenterX) > 16
                || Math.abs((long) z - lastCenterZ) > 16 || Math.abs((long) viewX - lastViewX) > 16
                || Math.abs((long) viewZ - lastViewZ) > 16 || radius != lastRadius) {
            cancelPending(); syncProgress.clear();
            lastDimension = mc.level.dimension(); lastCenterX = x; lastCenterZ = z;
            lastViewX = viewX; lastViewZ = viewZ; lastRadius = radius;
            chunkQueue.reset(x, z, viewX, viewZ, radius);
        }
        return true;
    }

    public void tick(Minecraft mc) {
        if (!ensureFocus(mc)) return;
        ClientLevel level = mc.level;
        int workers = failedAsync ? 0 : ChunkMapGeneration.workers();
        boolean async = workers > 0;
        if (async != lastAsync) {
            cancelPending(); syncProgress.clear();
            chunkQueue.reset(lastCenterX, lastCenterZ, lastViewX, lastViewZ, lastRadius);
            lastAsync = async;
        }
        if (async) {
            long deadline = System.nanoTime() + 2_000_000L;
            int handled = 0;
            int limit = Math.min(workers, Math.max(1, OpenMapDeltaConfig.effectiveColumnsPerTick() / 128));
            while (handled < limit && pending.size() < workers * 2 && System.nanoTime() < deadline) {
                Long key = chunkQueue.pollReady(candidate -> readyChunk(level, candidate) != null);
                if (key == null) break;
                if (chunkQueue.needsLiveScan(key)) {
                    // Continuously changing chunks must progress without a stable worker snapshot.
                    scanLiveChunk(level, key, Math.min(256, OpenMapDeltaConfig.effectiveColumnsPerTick()), deadline);
                    handled++;
                    continue;
                }
                if (!ChunkMapGeneration.hasCapacity()) { chunkQueue.retry(key); break; }
                LevelChunk chunk = readyChunk(level, key);
                if (chunk == null) { chunkQueue.retry(key); break; }
                ChunkMapSnapshot snapshot = new ChunkMapSnapshot(chunk);
                Future<ChunkColumnScan.Prepared> future = ChunkMapGeneration.submit(snapshot, tileCache.getLayer());
                if (future == null) { chunkQueue.retry(key); break; }
                pending.add(new Job(level, chunk, snapshot, future, tileCache.getSamplingEpoch(), key, chunkQueue.columnOrder(key)));
                handled++;
            }
        } else {
            scanSynchronously(level);
        }
    }

    private static LevelChunk readyChunk(ClientLevel level, long key) {
        LevelChunk chunk = level.getChunkSource().getChunk(ChunkScanQueue.x(key), ChunkScanQueue.z(key), ChunkStatus.FULL, false);
        return chunk != null && chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE) ? chunk : null;
    }

    /** Same late-chunk priority when CPU load or configuration selects the client-thread path. */
    private void scanSynchronously(ClientLevel level) {
        int applied = 0, budget = OpenMapDeltaConfig.effectiveColumnsPerTick();
        long deadline = System.nanoTime() + 2_000_000L;
        while (applied < budget) {
            Long key = chunkQueue.pollReady(candidate -> readyChunk(level, candidate) != null);
            if (key == null) break;
            applied += scanLiveChunk(level, key, budget - applied, deadline);
            if (applied >= budget || System.nanoTime() >= deadline) break;
        }
    }

    private int scanLiveChunk(ClientLevel level, long key, int budget, long deadline) {
        SyncProgress progress = syncProgress.computeIfAbsent(key, candidate -> new SyncProgress(chunkQueue.columnOrder(candidate)));
        int applied = 0;
        while (progress.nextColumn < 256 && applied < budget) {
            int i = progress.columns[progress.nextColumn++];
            tileCache.samplePixel(level, ChunkScanQueue.x(key) * 16 + (i & 15), ChunkScanQueue.z(key) * 16 + (i >> 4));
            applied++;
            if ((applied & 31) == 0 && System.nanoTime() >= deadline) break;
        }
        if (progress.nextColumn == 256) { syncProgress.remove(key); chunkQueue.complete(key); }
        else chunkQueue.retry(key);
        return applied;
    }

    /** Drain completed workers every tick, apart from submission intervals. */
    public void applyCompleted(Minecraft mc) {
        if (!ensureFocus(mc)) return;
        if (!OpenMapDeltaConfig.ASYNC_MAP_GENERATION.get()) { cancelPending(); ChunkMapGeneration.close(); return; }
        long deadline = System.nanoTime() + 2_000_000L;
        int budget = Math.max(32, OpenMapDeltaConfig.effectiveColumnsPerTick() * 2), applied = 0;
        pending.sort(Comparator.comparingDouble(job -> chunkQueue.distanceSquared(job.key)));
        Iterator<Job> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (mc.level != job.level || job.epoch != tileCache.getSamplingEpoch()
                    || ((ChunkMapRevision) job.liveChunk).odysseymap$getMapRevision() != job.snapshot.sourceRevision
                    || mc.level.getChunkSource().getChunk(job.snapshot.chunkX, job.snapshot.chunkZ, ChunkStatus.FULL, false) != job.liveChunk) {
                job.future.cancel(true); chunkQueue.invalidated(job.key); iterator.remove(); continue;
            }
            if (!job.future.isDone()) continue;
            ChunkColumnScan.Prepared prepared;
            try { prepared = job.future.get(); }
            catch (CancellationException cancelled) { chunkQueue.retry(job.key); iterator.remove(); continue; }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            catch (ExecutionException failure) {
                LoggerFactory.getLogger("OpenMapDelta").warn("Chunk map generation failed; using client-thread sampling", failure.getCause());
                failedAsync = true; chunkQueue.retry(job.key); iterator.remove(); continue;
            }
            while (job.nextColumn < 256 && applied < budget) {
                int i = job.columns[job.nextColumn++];
                tileCache.samplePreparedPixel(job.level, job.snapshot, prepared, i & 15, i >> 4);
                applied++;
                if ((applied & 31) == 0 && System.nanoTime() >= deadline) break;
            }
            if (job.nextColumn == 256) { chunkQueue.complete(job.key); iterator.remove(); }
            if (applied >= budget || System.nanoTime() >= deadline) return;
        }
    }

    public void reset() {
        cancelPending(); lastDimension = null; failedAsync = false;
        chunkQueue.clear(); syncProgress.clear();
    }
    private void cancelPending() {
        for (Job job : pending) { job.future.cancel(true); chunkQueue.retry(job.key); }
        pending.clear();
    }
}
