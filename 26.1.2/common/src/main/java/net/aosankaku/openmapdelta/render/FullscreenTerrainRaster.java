// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileColorAverage;
import net.aosankaku.openmapdelta.world.TerrainRelief;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reuses unchanged terrain and limits both color summaries and averaging workspace. */
public final class FullscreenTerrainRaster {
    public static final int MAX_CACHED_AVERAGES = 128;
    private static final int WORK_ROWS = 64;
    private final Map<Tile, TileColorAverage> averages = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Tile, TileColorAverage> entry) {
            return size() > MAX_CACHED_AVERAGES;
        }
    };
    private Map<Long, TileImage> previousTiles = Map.of();
    private int width, height, originX, originZ;
    private double stride;
    private int[] pixels;
    private double[] totals;
    private int averageBuildCount;
    private int dirtyMinX, dirtyMinZ, dirtyMaxX, dirtyMaxZ;

    /** All arguments are client-thread owned; no live tile is handed to an IO worker. */
    public boolean update(FullscreenMapViewport viewport, Map<Long, Tile> tiles) {
        int nextWidth = viewport.getTextureWidth(), nextHeight = viewport.getTextureHeight();
        int nextX = viewport.getOriginX(), nextZ = viewport.getOriginZ();
        double nextStride = viewport.getSampleStride();
        double endX = nextX + (double) nextWidth * nextStride;
        double endZ = nextZ + (double) nextHeight * nextStride;
        Map<Long, TileImage> visible = new HashMap<>();
        // Iterate known tiles, not millions of empty tile coordinates at distant zoom levels.
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            long tileX = (int) (entry.getKey() >> 32) - 64L;
            long tileZ = (int) (long) entry.getKey() - 64L;
            if (tileX >= endX || tileX + Tile.SIZE <= nextX
                    || tileZ >= endZ || tileZ + Tile.SIZE <= nextZ) continue;
            Tile tile = entry.getValue();
            if (tile == null || !tile.hasObservedPixels()) continue;
            int alignedX = (int) (entry.getKey() >> 32);
            int alignedZ = (int) (long) entry.getKey();
            TerrainRelief relief = new TerrainRelief(tile,
                    adjacent(tiles, alignedX, alignedZ, 0, -Tile.SIZE),
                    adjacent(tiles, alignedX, alignedZ, Tile.SIZE, 0),
                    adjacent(tiles, alignedX, alignedZ, 0, Tile.SIZE),
                    adjacent(tiles, alignedX, alignedZ, -Tile.SIZE, 0));
            visible.put(entry.getKey(), new TileImage(tile, relief));
        }
        boolean sameGeometry = pixels != null && width == nextWidth && height == nextHeight && originX == nextX
                && originZ == nextZ && Double.compare(stride, nextStride) == 0;
        if (sameGeometry && visible.equals(previousTiles)) return false;
        width = nextWidth; height = nextHeight; originX = nextX; originZ = nextZ; stride = nextStride;
        PixelArea changed = new PixelArea();
        if (sameGeometry) {
            for (Map.Entry<Long, TileImage> entry : visible.entrySet()) {
                if (!entry.getValue().equals(previousTiles.get(entry.getKey()))) changed.include(entry.getKey());
            }
            for (long key : previousTiles.keySet()) if (!visible.containsKey(key)) changed.include(key);
        } else {
            changed.minX = 0; changed.minZ = 0; changed.maxX = width; changed.maxZ = height;
        }
        dirtyMinX = changed.minX; dirtyMinZ = changed.minZ;
        dirtyMaxX = changed.maxX; dirtyMaxZ = changed.maxZ;
        if (pixels == null || pixels.length != Math.multiplyExact(width, height)) pixels = new int[width * height];
        for (int z = changed.minZ; z < changed.maxZ; z++) {
            Arrays.fill(pixels, z * width + changed.minX, z * width + changed.maxX, 0);
        }
        if (stride == 1) {
            for (Map.Entry<Long, TileImage> entry : visible.entrySet()) copyTile(entry.getKey(), entry.getValue(), changed);
        } else {
            averageTiles(visible, changed);
        }
        previousTiles = visible;
        return true;
    }

    private void copyTile(long key, TileImage tileImage, PixelArea changed) {
        long tileX = (int) (key >> 32) - 64L, tileZ = (int) key - 64L;
        int minX = (int) Math.max(changed.minX, tileX - originX), maxX = (int) Math.min(changed.maxX, tileX + Tile.SIZE - originX);
        int minZ = (int) Math.max(changed.minZ, tileZ - originZ), maxZ = (int) Math.min(changed.maxZ, tileZ + Tile.SIZE - originZ);
        for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x < maxX; x++) {
                int color = tileImage.relief().getPixel((int) (originX + x - tileX), (int) (originZ + z - tileZ));
                pixels[z * width + x] = color >>> 24 == 0 ? 0 : color;
            }
        }
    }

    private void averageTiles(Map<Long, TileImage> visible, PixelArea changed) {
        int workWidth = changed.maxX - changed.minX;
        int workSize = Math.multiplyExact(workWidth * Math.min(WORK_ROWS, changed.maxZ - changed.minZ), 5);
        if (totals == null || totals.length < workSize) totals = new double[workSize];
        for (int band = changed.minZ; band < changed.maxZ; band += WORK_ROWS) {
            int bandEnd = Math.min(changed.maxZ, band + WORK_ROWS);
            Arrays.fill(totals, 0, workWidth * (bandEnd - band) * 5, 0.0);
            // Tile order builds each summary once per band rather than once per output row.
            for (Map.Entry<Long, TileImage> entry : visible.entrySet()) {
                long tileX = (int) (entry.getKey() >> 32) - 64L;
                long tileZ = (int) (long) entry.getKey() - 64L;
                int minZ = (int) Math.max(band, Math.floor((tileZ - originZ) / stride));
                int maxZ = (int) Math.min(bandEnd, Math.ceil((tileZ + Tile.SIZE - originZ) / stride));
                if (minZ >= maxZ) continue;
                int minX = (int) Math.max(changed.minX, Math.floor((tileX - originX) / stride));
                int maxX = (int) Math.min(changed.maxX, Math.ceil((tileX + Tile.SIZE - originX) / stride));
                if (minX >= maxX) continue;
                TileColorAverage average = averageFor(entry.getValue().tile(), entry.getValue().relief());
                for (int z = minZ; z < maxZ; z++) {
                    double sampleZ = originZ + z * stride - tileZ;
                    for (int x = minX; x < maxX; x++) {
                        double sampleX = originX + x * stride - tileX;
                        average.accumulate(clampTileCoordinate(sampleX), clampTileCoordinate(sampleZ),
                                clampTileCoordinate(sampleX + stride), clampTileCoordinate(sampleZ + stride),
                                totals, ((z - band) * workWidth + x - changed.minX) * 5);
                    }
                }
            }
            for (int z = band; z < bandEnd; z++) {
                for (int x = changed.minX; x < changed.maxX; x++) {
                    pixels[z * width + x] = TileColorAverage.color(totals,
                            ((z - band) * workWidth + x - changed.minX) * 5);
                }
            }
        }
    }

    private static double clampTileCoordinate(double value) {
        return Math.max(0.0, Math.min(Tile.SIZE, value));
    }

    private TileColorAverage averageFor(Tile tile, TerrainRelief relief) {
        TileColorAverage average = averages.get(tile);
        if (average == null || !average.matches(relief)) {
            average = new TileColorAverage(relief);
            averageBuildCount++;
            averages.put(tile, average);
        }
        return average;
    }

    private static Tile adjacent(Map<Long, Tile> tiles, int alignedX, int alignedZ, int offsetX, int offsetZ) {
        long x = (long) alignedX + offsetX;
        long z = (long) alignedZ + offsetZ;
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            return null;
        }
        long key = (x << 32) | (z & 0xFFFFFFFFL);
        return tiles.get(key);
    }

    public int[] getPixels() { return pixels; }
    public int getDirtyMinX() { return dirtyMinX; }
    public int getDirtyMinZ() { return dirtyMinZ; }
    public int getDirtyMaxX() { return dirtyMaxX; }
    public int getDirtyMaxZ() { return dirtyMaxZ; }
    public int getCachedAverageCount() { return averages.size(); }
    int getAverageBuildCount() { return averageBuildCount; }
    public void clear() {
        averages.clear(); previousTiles = Map.of(); pixels = null; totals = null; averageBuildCount = 0;
        dirtyMinX = dirtyMinZ = dirtyMaxX = dirtyMaxZ = 0;
    }
    /** Union of output footprints affected by newly loaded, changed or removed tiles. */
    private final class PixelArea {
        private int minX = width, minZ = height, maxX, maxZ;
        private void include(long key) {
            long tileX = (int) (key >> 32) - 64L, tileZ = (int) key - 64L;
            minX = Math.min(minX, (int) Math.max(0.0, Math.floor((tileX - originX) / stride)));
            minZ = Math.min(minZ, (int) Math.max(0.0, Math.floor((tileZ - originZ) / stride)));
            maxX = Math.max(maxX, (int) Math.min(width, Math.ceil((tileX + Tile.SIZE - originX) / stride)));
            maxZ = Math.max(maxZ, (int) Math.min(height, Math.ceil((tileZ + Tile.SIZE - originZ) / stride)));
        }
    }
    private record TileImage(Tile tile, TerrainRelief relief) {}
}
