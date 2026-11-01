// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.platform.DataPaths;
import net.aosankaku.openmapdelta.platform.Services;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TileCache {
    private static final long AUTOSAVE_INTERVAL_NANOS = 10_000_000_000L;
    private static final Tile EMPTY_TILE = new Tile();

    private final ColumnSampler sampler;
    private int layer;
    private final Path cacheRoot;
    private final WorldCache worldCache;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private final Map<Long, Long> queuedSaveRevisions = new HashMap<>();
    private ResourceKey<Level> currentDimension;
    private String sessionKey;
    private boolean sessionActive;
    private boolean persistentSession;
    private boolean hasPreloadedCenter;
    private int lastPreloadedTileX;
    private int lastPreloadedTileZ;
    private long revision;
    private long samplingEpoch;
    private boolean grassTintEnabled = OpenMapDeltaConfig.BIOME_GRASS_TINT.get();
    private long nextAutosaveNanos = System.nanoTime() + AUTOSAVE_INTERVAL_NANOS;

    public TileCache(ColumnSampler sampler) {
        this(sampler, OpenMapDeltaClient.SURFACE_LAYER);
    }

    public TileCache(ColumnSampler sampler, int layer) {
        this(sampler, DataPaths.cacheDirectory(), layer);
    }

    TileCache(ColumnSampler sampler, Path cacheRoot) {
        this(sampler, cacheRoot, OpenMapDeltaClient.SURFACE_LAYER);
    }

    private TileCache(ColumnSampler sampler, Path cacheRoot, int layer) {
        this(sampler, cacheRoot, layer, new WorldCache());
    }

    private TileCache(ColumnSampler sampler, Path cacheRoot, int layer, WorldCache worldCache) {
        this.sampler = sampler;
        this.layer = layer;
        this.cacheRoot = cacheRoot;
        this.worldCache = worldCache;
    }

    /** Cave views may change bands; serialize their disk writes even when a band changes owners. */
    public TileCache createSibling(int layer) {
        return new TileCache(sampler, cacheRoot, layer, worldCache.createSibling());
    }

    /** A tile key is the exact pair of aligned signed coordinates; dimension is guarded separately. */
    public static long tileKey(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        return tileKey(alignedX, alignedZ);
    }

    public static long tileKey(int alignedX, int alignedZ) {
        return ((long) alignedX << Integer.SIZE) | (alignedZ & 0xFFFFFFFFL);
    }

    public static int alignTile(int blockCoord) {
        long aligned = Math.floorDiv((long) blockCoord + 64L, Tile.SIZE) * Tile.SIZE;
        return (int) Math.max(Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE - (Integer.MAX_VALUE % Tile.SIZE), aligned));
    }

    /** Starts a dimension-scoped session. A null storage identity means memory-only. */
    public boolean beginSession(String key, String persistentWorldIdentity, ResourceKey<Level> dimension) {
        if (dimension == null) return false;
        String dimensionIdentity = dimensionId(dimension)
                + (layer == OpenMapDeltaClient.SURFACE_LAYER ? "" : "#cave:" + layer);
        if (sessionActive && Objects.equals(sessionKey, key)
                && Objects.equals(currentDimension, dimension)) return false;

        samplingEpoch++;
        if (sessionActive) worldCache.endSession(captureContentWrites());
        tiles.clear();
        queuedSaveRevisions.clear();
        currentDimension = dimension;
        sessionKey = key;
        sessionActive = true;
        persistentSession = persistentWorldIdentity != null && !persistentWorldIdentity.isBlank();
        hasPreloadedCenter = false;
        TileStorage storage = persistentSession
                ? new TileStorage(cacheRoot, persistentWorldIdentity, dimensionIdentity)
                : null;
        worldCache.beginSession(storage);
        nextAutosaveNanos = System.nanoTime() + AUTOSAVE_INTERVAL_NANOS;
        revision++;
        return true;
    }

    public Tile getOrCreate(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        if (currentDimension == null || !currentDimension.equals(dimension)) return EMPTY_TILE;
        long key = tileKey(alignedX, alignedZ);
        Tile tile = tiles.get(key);
        if (tile == null) {
            tile = createTile();
            tiles.put(key, tile);
            if (persistentSession) worldCache.requestLoad(alignedX, alignedZ);
        }
        return tile;
    }

    public Tile get(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        if (currentDimension == null || !currentDimension.equals(dimension)) return null;
        return tiles.get(tileKey(alignedX, alignedZ));
    }

    public void samplePixel(Level level, int worldX, int worldZ) {
        ResourceKey<Level> dimension = level.dimension();
        if (currentDimension == null || !currentDimension.equals(dimension)) return;
        int alignedX = alignTile(worldX);
        int alignedZ = alignTile(worldZ);
        Tile tile = getOrCreate(dimension, alignedX, alignedZ);
        int localX = worldX - alignedX + 64;
        int localZ = worldZ - alignedZ + 64;
        if (localX < 0 || localZ < 0 || localX >= Tile.SIZE || localZ >= Tile.SIZE) return;
        // An unloaded chunk is unknown. Keep its cached value for the next visible render.
        if (level.getChunkSource().getChunk(worldX >> 4, worldZ >> 4, ChunkStatus.FULL, false) == null) return;
        ColumnSampler.Sample sample = layer == OpenMapDeltaClient.SURFACE_LAYER
                ? sampler.sampleSurface(level, worldX, worldZ)
                : sampler.sampleCave(level, worldX, worldZ, layer);
        writeSample(level, worldX, worldZ, sample);
    }

    public int getLayer() { return layer; }
    public long getSamplingEpoch() { return samplingEpoch; }

    public void samplePreparedPixel(Level level, ChunkMapSnapshot snapshot, ChunkColumnScan.Prepared prepared, int x, int z) {
        if (currentDimension == null || !currentDimension.equals(level.dimension())) return;
        writeSample(level, snapshot.chunkX * 16 + x, snapshot.chunkZ * 16 + z,
                sampler.finish(level, snapshot, prepared, x, z, layer));
    }

    private void writeSample(Level level, int x, int z, ColumnSampler.Sample sample) {
        int ax = alignTile(x), az = alignTile(z);
        Tile tile = getOrCreate(level.dimension(), ax, az);
        if (tile.setPixel(x - ax + 64, z - az + 64, sample.color(), sample.height(), sample.grassColor())) revision++;
        tile.setLastUpdatedTick(level.getGameTime());
    }

    private Tile createTile() {
        Tile tile = new Tile();
        tile.setGrassTintEnabled(grassTintEnabled);
        return tile;
    }

    public void setLayer(int layer) {
        if (this.layer == layer) return;
        clear();
        this.layer = layer;
    }

    public int getHeight(ResourceKey<Level> dimension, int worldX, int worldZ) {
        int alignedX = alignTile(worldX);
        int alignedZ = alignTile(worldZ);
        Tile tile = get(dimension, alignedX, alignedZ);
        return tile == null ? Tile.UNKNOWN_HEIGHT : tile.getHeight(worldX - alignedX + 64, worldZ - alignedZ + 64);
    }

    public void invalidateArea(Level level, int minX, int minZ, int maxX, int maxZ) {
        ResourceKey<Level> dimension = level.dimension();
        for (int x = alignTile(minX); x <= alignTile(maxX); x += Tile.SIZE) {
            for (int z = alignTile(minZ); z <= alignTile(maxZ); z += Tile.SIZE) {
                Tile tile = get(dimension, x, z);
                if (tile != null) tile.markDirty();
            }
        }
    }

    /** Poll async disk loads and save acknowledgments; call every client tick, apart from scanning. */
    public void tick() {
        boolean tint = OpenMapDeltaConfig.BIOME_GRASS_TINT.get();
        if (tint != grassTintEnabled) {
            grassTintEnabled = tint;
            for (Tile tile : tiles.values()) tile.setGrassTintEnabled(tint);
            revision++;
        }
        for (WorldCache.SaveResult result : worldCache.pollSaveResults()) {
            long key = tileKey(result.alignedX(), result.alignedZ());
            Long queuedRevision = queuedSaveRevisions.get(key);
            if (queuedRevision != null && queuedRevision == result.contentRevision()) {
                queuedSaveRevisions.remove(key);
            }
            Tile tile = tiles.get(key);
            if (tile != null && result.succeeded()) tile.markSavedContent(result.contentRevision());
        }

        for (WorldCache.LoadResult result : worldCache.pollLoads(8)) {
            if (result.snapshot() == null) continue;
            long key = tileKey(result.alignedX(), result.alignedZ());
            Tile tile = tiles.computeIfAbsent(key, ignored -> createTile());
            if (tile.mergeLoaded(result.snapshot())) revision++;
        }

        long now = System.nanoTime();
        if (persistentSession && now >= nextAutosaveNanos) {
            saveDirtyContent();
            nextAutosaveNanos = now + AUTOSAVE_INTERVAL_NANOS;
        }
    }

    /** Queue the player's visible neighborhood nearest first on session entry or tile crossing. */
    public void preloadNear(ResourceKey<Level> dimension, int worldX, int worldZ) {
        if (!persistentSession || currentDimension == null || !currentDimension.equals(dimension)) return;
        int centerX = alignTile(worldX);
        int centerZ = alignTile(worldZ);
        if (hasPreloadedCenter && centerX == lastPreloadedTileX && centerZ == lastPreloadedTileZ) return;
        hasPreloadedCenter = true;
        lastPreloadedTileX = centerX;
        lastPreloadedTileZ = centerZ;

        List<int[]> nearby = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                nearby.add(new int[]{centerX + dx * Tile.SIZE, centerZ + dz * Tile.SIZE});
            }
        }
        nearby.sort(Comparator.comparingLong(point -> {
            long dx = (long) point[0] - centerX;
            long dz = (long) point[1] - centerZ;
            return dx * dx + dz * dz;
        }));
        for (int[] point : nearby) getOrCreate(dimension, point[0], point[1]);
    }

    /** Asynchronously requests stored tiles intersecting the inclusive viewport bounds. */
    public void preloadArea(ResourceKey<Level> dimension, int minWorldX, int minWorldZ,
                            int maxWorldX, int maxWorldZ) {
        if (!persistentSession || currentDimension == null || !currentDimension.equals(dimension)) return;
        if (minWorldX > maxWorldX || minWorldZ > maxWorldZ) return;
        worldCache.requestArea(alignTile(minWorldX), alignTile(minWorldZ),
                alignTile(maxWorldX), alignTile(maxWorldZ));
    }

    public void markAllDirty() { tiles.values().forEach(Tile::markDirty); }

    /** Monotonic map-data revision for tick-driven fullscreen composition invalidation. */
    public long getRevision() { return revision; }

    public void clear() {
        samplingEpoch++;
        if (sessionActive) worldCache.endSession(captureContentWrites());
        else worldCache.endSession(List.of());
        tiles.clear();
        queuedSaveRevisions.clear();
        currentDimension = null;
        sessionKey = null;
        sessionActive = false;
        persistentSession = false;
        hasPreloadedCenter = false;
        revision++;
    }

    public Map<Long, Tile> getTiles() { return tiles; }

    private void saveDirtyContent() {
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            long key = entry.getKey();
            Tile tile = entry.getValue();
            if (!tile.hasUnsavedContent()) continue;
            long contentRevision = tile.getContentRevision();
            Long queuedRevision = queuedSaveRevisions.get(key);
            if (queuedRevision != null && queuedRevision >= contentRevision) continue;
            Tile.Snapshot snapshot = tile.snapshot();
            if (!snapshot.hasObservedPixels()) continue;
            worldCache.save(coordinateX(key), coordinateZ(key), contentRevision, snapshot);
            queuedSaveRevisions.put(key, contentRevision);
        }
    }

    private List<WorldCache.TileWrite> captureContentWrites() {
        List<WorldCache.TileWrite> writes = new ArrayList<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            Tile tile = entry.getValue();
            if (!tile.hasUnsavedContent()) continue;
            Tile.Snapshot snapshot = tile.snapshot();
            if (!snapshot.hasObservedPixels()) continue;
            writes.add(new WorldCache.TileWrite(coordinateX(entry.getKey()), coordinateZ(entry.getKey()),
                    tile.getContentRevision(), snapshot));
        }
        return writes;
    }

    private static int coordinateX(long key) { return (int) (key >> Integer.SIZE); }
    private static int coordinateZ(long key) { return (int) key; }

    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }
}
