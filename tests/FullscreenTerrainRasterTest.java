// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TerrainRelief;
import java.util.HashMap;
import java.util.Map;

/** Exact pixels, late disk arrivals, reuse and bounded summary storage without a client launch. */
public final class FullscreenTerrainRasterTest {
    private static int assertions;
    public static void main(String[] args) {
        exactFootprintsAndNegativeCoordinates();
        distantAndCrossTileFootprints();
        revisionsAndViewportReuse();
        summaryStorageIsBounded();
        denseViewOnlyRebuildsChangedTerrain();
        repeatedEditsKeepOneShadedSummaryPerTile();
        fractionalFootprintsMatchIndependentReferenceAndExposeDirtyBounds();
        neighboringHeightChangesRefreshVisibleRelief();
        shadedTerrainIsAveragedAtZoomAndFractionalStride();
        System.out.println("Fullscreen terrain raster tests passed (" + assertions + " assertions).");
    }

    private static void exactFootprintsAndNegativeCoordinates() {
        Map<Long, Tile> tiles = new HashMap<>();
        for (int alignedX : new int[]{-128, 0, 128}) {
            for (int alignedZ : new int[]{-128, 0, 128}) {
                Tile tile = new Tile();
                for (int z = 0; z < Tile.SIZE; z++) {
                    for (int x = 0; x < Tile.SIZE; x++) {
                        if ((x + z) % 5 != 0) tile.setPixel(x, z,
                                (x + z) % 7 == 0 ? 0x00ABCDEF : 0xFF000000 | x << 16 | z << 8 | (x * z & 255));
                    }
                }
                tiles.put(key(alignedX, alignedZ), tile);
            }
        }
        for (int stride : new int[]{1, 2, 3, 7}) {
            FullscreenMapViewport view = FullscreenMapViewport.forView(41, 89, stride, -17.25, 43.5);
            FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
            check(raster.update(view, tiles), "initial terrain is composed");
            int[] actual = raster.getPixels();
            for (int z = 0; z < view.getTextureHeight(); z++) {
                for (int x = 0; x < view.getTextureWidth(); x++) {
                    check(actual[z * view.getTextureWidth() + x] == reference(tiles, view, x, z),
                            "exact average across negative tile edges and 64-row work bands");
                }
            }
            if (stride == 1) check(raster.getCachedAverageCount() == 0, "block detail needs no color summaries");
            Tile changed = tiles.get(key(0, 0));
            changed.setPixel(60, 60, changed.getPixel(60, 60) ^ 0x00010101);
            check(raster.update(view, tiles), "changed tile updates its output footprints");
            for (int z = 0; z < view.getTextureHeight(); z++) {
                for (int x = 0; x < view.getTextureWidth(); x++) {
                    check(raster.getPixels()[z * view.getTextureWidth() + x] == reference(tiles, view, x, z),
                            "incremental update includes contributions from adjacent unchanged tiles");
                }
            }
        }
    }

    private static void distantAndCrossTileFootprints() {
        Map<Long, Tile> tiles = new HashMap<>();
        Tile red = new Tile(); red.setPixel(127, 127, 0xFFFF0000);
        Tile blue = new Tile(); blue.setPixel(0, 0, 0xFF0000FF);
        tiles.put(key(0, 0), red); tiles.put(key(128, 128), blue);
        for (int stride : new int[]{16, 129, 256}) {
            FullscreenMapViewport view = FullscreenMapViewport.forView(5, 7, stride, 64, 64);
            FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
            raster.update(view, tiles);
            int[] pixels = raster.getPixels();
            // Check the area containing known pixels and every empty corner without scanning huge empty footprints.
            for (int z : new int[]{0, view.getTextureHeight() / 2 - 1, view.getTextureHeight() / 2, view.getTextureHeight() - 1}) {
                for (int x : new int[]{0, view.getTextureWidth() / 2 - 1, view.getTextureWidth() / 2, view.getTextureWidth() - 1}) {
                    check(pixels[z * view.getTextureWidth() + x] == reference(tiles, view, x, z),
                            "zoomed-out footprint combines tiles and keeps missing terrain transparent");
                }
            }
        }
    }

    private static void revisionsAndViewportReuse() {
        Map<Long, Tile> tiles = new HashMap<>();
        FullscreenMapViewport view = FullscreenMapViewport.forView(17, 19, 2, 0, 0);
        FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
        check(raster.update(view, tiles), "empty view initializes transparent pixels");
        int[] pixels = raster.getPixels();
        check(!raster.update(view, tiles) && pixels == raster.getPixels(), "unchanged terrain avoids recomposition and reallocation");
        Tile loaded = new Tile(); loaded.setPixel(1, 1, 0xFF112233);
        tiles.put(key(0, 0), loaded);
        check(raster.update(view, tiles), "disk arrival is visible without waiting for a live scan");
        check(!raster.update(view, tiles), "completed arrival is reused");
        loaded.setPixel(1, 1, 0xFF445566);
        check(raster.update(view, tiles), "sampled color revision invalidates visible terrain");
        Tile outside = new Tile(); outside.setPixel(1, 1, 0xFFFFFFFF);
        tiles.put(key(8192, 8192), outside);
        check(!raster.update(view, tiles), "offscreen disk arrivals do not rebuild the texture");
        outside.setPixel(1, 1, 0xFF000000);
        check(!raster.update(view, tiles), "offscreen scans do not rebuild the texture");
        Tile replacement = new Tile(); replacement.setPixel(1, 1, 0xFFABCDEF); replacement.setPixel(1, 1, 0xFFABCDEE);
        check(replacement.getImageRevision() == loaded.getImageRevision(), "replacement fixture has equal revision");
        tiles.put(key(0, 0), replacement);
        check(raster.update(view, tiles), "same-revision tile identity changes invalidate terrain");
        tiles.remove(key(0, 0));
        check(raster.update(view, tiles), "removing visible terrain clears old pixels");
        check(raster.update(FullscreenMapViewport.forView(17, 19, 2, 128, 0), tiles), "pan to a new region composes immediately");
        check(raster.update(FullscreenMapViewport.forView(17, 19, 4, 128, 0), tiles), "zoom change recomputes footprints");
        check(raster.update(FullscreenMapViewport.forView(19, 17, 4, 128, 0), tiles), "same-area resize recomputes geometry");
        raster.clear();
        check(raster.getCachedAverageCount() == 0 && raster.getPixels() == null, "world/layer reset releases prior terrain");
        check(raster.update(view, tiles), "reset forces initial composition even when geometry repeats");
    }

    private static void summaryStorageIsBounded() {
        FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
        Map<Long, Tile> tiles = new HashMap<>();
        for (int i = 0; i < FullscreenTerrainRaster.MAX_CACHED_AVERAGES + 20; i++) {
            int alignedX = i * 256;
            Tile tile = new Tile(); tile.setPixel(64, 64, 0xFF123456);
            tiles.put(key(alignedX, 0), tile);
            raster.update(FullscreenMapViewport.forView(3, 3, 2, alignedX, 0), tiles);
            check(raster.getCachedAverageCount() <= FullscreenTerrainRaster.MAX_CACHED_AVERAGES,
                    "panning through explored terrain cannot grow summary storage without limit");
        }
    }

    private static void denseViewOnlyRebuildsChangedTerrain() {
        FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
        FullscreenMapViewport view = FullscreenMapViewport.forView(192, 16, 16, 0, 0);
        Map<Long, Tile> tiles = new HashMap<>();
        for (int x = -1280; x <= 1280; x += 128) {
            for (int z = -384; z <= 384; z += 128) {
                Tile tile = new Tile(); tile.setPixel(64, 64, 0xFF112233);
                tiles.put(key(x, z), tile);
            }
        }
        check(tiles.size() > FullscreenTerrainRaster.MAX_CACHED_AVERAGES, "dense fixture exceeds summary capacity");
        raster.update(view, tiles);
        int before = raster.getAverageBuildCount();
        tiles.get(key(0, 0)).setPixel(64, 64, 0xFFFFFFFF);
        check(raster.update(view, tiles), "dense view applies a single changed tile");
        check(raster.getAverageBuildCount() - before <= 9,
                "one changed tile rebuilds only its center, four dependencies and footprints in their dirty union");
        for (int z = 0; z < view.getTextureHeight(); z++) {
            for (int x = 0; x < view.getTextureWidth(); x++) {
                check(raster.getPixels()[z * view.getTextureWidth() + x] == reference(tiles, view, x, z),
                        "dense incremental update preserves every other pixel");
            }
        }
    }

    private static void repeatedEditsKeepOneShadedSummaryPerTile() {
        Tile tile = new Tile();
        tile.setPixel(64, 64, 0xFF112233);
        Map<Long, Tile> tiles = Map.of(key(0, 0), tile);
        FullscreenMapViewport view = FullscreenMapViewport.forView(5, 5, 2, 0, 0);
        FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
        check(raster.update(view, tiles), "initial shaded summary is created");
        check(raster.getCachedAverageCount() == 1, "one visible tile has one cached shaded summary");
        tile.setPixel(64, 64, 0xFF445566);
        check(raster.update(view, tiles), "a tile revision refreshes its shaded summary");
        tile.setPixel(64, 64, 0xFF778899);
        check(raster.update(view, tiles), "repeated revisions refresh the same tile summary");
        check(raster.getCachedAverageCount() == 1, "old revisions do not occupy additional summary-cache entries");
        check(!raster.update(view, tiles), "an unchanged relief dependency reuses its refreshed summary");
        check(raster.getCachedAverageCount() == 1, "an unchanged update preserves the bounded per-tile summary");
    }

    private static void fractionalFootprintsMatchIndependentReferenceAndExposeDirtyBounds() {
        Map<Long, Tile> tiles = new HashMap<>();
        Tile left = new Tile();
        left.setPixel(126, 63, 0xFFFF0000);
        left.setPixel(127, 63, 0x00000000);
        left.setPixel(127, 64, 0xFF00FF00);
        Tile right = new Tile();
        right.setPixel(0, 63, 0xFF0000FF);
        right.setPixel(0, 64, 0xFFFFFFFF);
        tiles.put(key(0, 0), left);
        tiles.put(key(128, 0), right);

        FullscreenMapViewport view = FullscreenMapViewport.forView(100, 17, 4,
                64.25, 0.375, 3);
        FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
        check(raster.update(view, tiles), "fractional physical-resolution view composes");
        check(raster.getDirtyMinX() == 0 && raster.getDirtyMinZ() == 0
                        && raster.getDirtyMaxX() == view.getTextureWidth()
                        && raster.getDirtyMaxZ() == view.getTextureHeight(),
                "initial terrain composition marks the full texture dirty");
        assertMatchesReference(tiles, view, raster, "fractional cross-tile footprint uses exact block overlap");

        int[] before = raster.getPixels().clone();
        right.setPixel(0, 63, 0xFFFFFF00);
        check(raster.update(view, tiles), "changed tile updates fractional output footprints");
        check(raster.getDirtyMinX() >= 0 && raster.getDirtyMinZ() >= 0
                        && raster.getDirtyMaxX() <= view.getTextureWidth()
                        && raster.getDirtyMaxZ() <= view.getTextureHeight(),
                "changed-tile dirty bounds stay within the texture");
        boolean changedOutput = false;
        int[] after = raster.getPixels();
        for (int z = 0; z < view.getTextureHeight(); z++) {
            for (int x = 0; x < view.getTextureWidth(); x++) {
                int index = z * view.getTextureWidth() + x;
                if (before[index] != after[index]) {
                    changedOutput = true;
                    check(x >= raster.getDirtyMinX() && x < raster.getDirtyMaxX()
                                    && z >= raster.getDirtyMinZ() && z < raster.getDirtyMaxZ(),
                            "fractional dirty bounds include every changed output pixel");
                }
            }
        }
        check(changedOutput, "fixture mutation affects a sampled fractional footprint");
        check(raster.getDirtyMaxX() - raster.getDirtyMinX() < view.getTextureWidth(),
                "a single changed tile does not mark the whole high-resolution texture dirty");
        assertMatchesReference(tiles, view, raster, "incremental fractional update preserves adjacent tiles");

        FullscreenMapViewport moved = FullscreenMapViewport.forView(100, 17, 4,
                128.25, -2.375, 3);
        check(raster.update(moved, tiles), "fractional pan refreshes terrain geometry");
        check(raster.getDirtyMinX() == 0 && raster.getDirtyMinZ() == 0
                        && raster.getDirtyMaxX() == moved.getTextureWidth()
                        && raster.getDirtyMaxZ() == moved.getTextureHeight(),
                "geometry changes mark the full texture dirty");
        assertMatchesReference(tiles, moved, raster, "fractional panning recomposes the new origin");
    }

    private static void neighboringHeightChangesRefreshVisibleRelief() {
        for (int alignedCenterX : new int[]{0, -128}) {
        FullscreenMapViewport view = FullscreenMapViewport.forView(1, 1, 1, alignedCenterX + 31, 0);
        check(view.getOriginX() == alignedCenterX - 1 && view.getTextureWidth() == 65,
                "fixture shows the last center-tile block while its east neighbor begins just beyond the raster");
        Tile center = solidTile(10, 0xff788c9a);
        Map<Long, Tile> tiles = new HashMap<>();
        tiles.put(key(alignedCenterX, 0), center);
        FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
        raster.update(view, tiles);
        int edge = (view.getTextureHeight() / 2) * view.getTextureWidth() + view.getTextureWidth() - 1;
        int original = raster.getPixels()[edge];
        check(original == center.getPixel(127, 64), "unknown offscreen neighbor leaves known edge blocks unshaded");

        Tile east = solidTile(11, 0xff788c9a);
        tiles.put(key(alignedCenterX + 128, 0), east);
        check(raster.update(view, tiles), "an offscreen east-tile arrival invalidates center-tile relief");
        int oneBlockDifference = raster.getPixels()[edge];
        check(oneBlockDifference != original, "neighbor arrival shades the visible boundary block");
        check(!tilesVisibleAt(tiles, key(alignedCenterX + 128, 0), view), "the changed neighbor remains outside sampled terrain");
        assertEdgeMatchesFresh(raster, view, tiles, edge, "neighbor arrival matches a fresh composition");

        east.setPixel(0, 64, 0xff788c9a, 18);
        check(raster.update(view, tiles), "a height-only edit in an offscreen neighbor invalidates its relief dependency");
        int tallDifference = raster.getPixels()[edge];
        check(tallDifference != oneBlockDifference, "a taller offscreen neighbor changes shadow strength");
        assertEdgeMatchesFresh(raster, view, tiles, edge, "height-only neighbor edit matches a fresh composition");

        Tile replacement = solidTile(18, 0xff788c9a);
        tiles.put(key(alignedCenterX + 128, 0), replacement);
        check(raster.update(view, tiles), "offscreen neighbor identity replacement invalidates relief");
        assertEdgeMatchesFresh(raster, view, tiles, edge, "neighbor replacement matches a fresh composition");
        tiles.remove(key(alignedCenterX + 128, 0));
        check(raster.update(view, tiles), "offscreen neighbor removal clears its previous shadow");
        check(raster.getPixels()[edge] == original, "removing the known neighbor removes its height shadow");
        assertEdgeMatchesFresh(raster, view, tiles, edge, "neighbor removal matches a fresh composition");

        Tile unrelated = solidTile(40, 0xff788c9a);
        tiles.put(key(alignedCenterX + 2048, 0), unrelated);
        check(!raster.update(view, tiles), "unrelated height updates do not rebuild the current terrain raster");
        }
    }

    private static Tile solidTile(int height, int color) {
        Tile tile = new Tile();
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) tile.setPixel(x, z, color, height);
        }
        return tile;
    }

    private static boolean tilesVisibleAt(Map<Long, Tile> tiles, long key, FullscreenMapViewport view) {
        long tileX = (int) (key >> 32) - 64L;
        long tileZ = (int) key - 64L;
        double endX = view.getOriginX() + (double) view.getTextureWidth() * view.getSampleStride();
        double endZ = view.getOriginZ() + (double) view.getTextureHeight() * view.getSampleStride();
        return tileX < endX && tileX + Tile.SIZE > view.getOriginX()
                && tileZ < endZ && tileZ + Tile.SIZE > view.getOriginZ();
    }

    private static void assertEdgeMatchesFresh(FullscreenTerrainRaster raster, FullscreenMapViewport view,
                                               Map<Long, Tile> tiles, int edge, String message) {
        FullscreenTerrainRaster fresh = new FullscreenTerrainRaster();
        fresh.update(view, tiles);
        check(raster.getPixels()[edge] == fresh.getPixels()[edge], message);
    }

    private static void shadedTerrainIsAveragedAtZoomAndFractionalStride() {
        Tile tile = solidTile(10, 0xff788c9a);
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 65; x < Tile.SIZE; x++) tile.setPixel(x, z, 0xff788c9a, 14);
        }
        Map<Long, Tile> tiles = Map.of(key(0, 0), tile);
        for (double guiScale : new double[]{1.0, 2.0}) {
            FullscreenMapViewport view = FullscreenMapViewport.forView(9, 7, 2.5, 0.25, -0.375, guiScale);
            FullscreenTerrainRaster raster = new FullscreenTerrainRaster();
            check(raster.update(view, tiles), "height-step raster is built for a zoomed or fractional stride");
            int[] actual = raster.getPixels();
            for (int z = 0; z < view.getTextureHeight(); z++) {
                for (int x = 0; x < view.getTextureWidth(); x++) {
                    check(actual[z * view.getTextureWidth() + x] == shadedReference(tiles, view, x, z),
                            "terrain shading is applied before exact zoomed and fractional pixel averaging");
                }
            }
        }
    }

    private static int shadedReference(Map<Long, Tile> tiles, FullscreenMapViewport view, int px, int pz) {
        double stride = view.getSampleStride();
        double startX = view.getOriginX() + px * stride, startZ = view.getOriginZ() + pz * stride;
        double endX = startX + stride, endZ = startZ + stride;
        double count = 0, red = 0, green = 0, blue = 0, alpha = 0;
        Map<Long, TerrainRelief> reliefs = reliefViews(tiles);
        for (int z = (int) Math.floor(startZ); z < Math.ceil(endZ); z++) {
            double overlapZ = Math.min(endZ, z + 1.0) - Math.max(startZ, z);
            for (int x = (int) Math.floor(startX); x < Math.ceil(endX); x++) {
                double area = (Math.min(endX, x + 1.0) - Math.max(startX, x)) * overlapZ;
                if (area <= 0) continue;
                int alignedX = Math.floorDiv(x + 64, Tile.SIZE) * Tile.SIZE;
                int alignedZ = Math.floorDiv(z + 64, Tile.SIZE) * Tile.SIZE;
                TerrainRelief relief = reliefs.get(key(alignedX, alignedZ));
                if (relief == null) continue;
                int color = relief.getPixel(x - alignedX + 64, z - alignedZ + 64);
                if (color >>> 24 == 0) continue;
                count += area;
                alpha += (color >>> 24) * area;
                red += (color >> 16 & 255) * area;
                green += (color >> 8 & 255) * area;
                blue += (color & 255) * area;
            }
        }
        return count == 0 ? 0 : channel(alpha / count) << 24 | channel(red / count) << 16
                | channel(green / count) << 8 | channel(blue / count);
    }

    private static Map<Long, TerrainRelief> reliefViews(Map<Long, Tile> tiles) {
        Map<Long, TerrainRelief> result = new HashMap<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            long tileKey = entry.getKey();
            int alignedX = (int) (tileKey >> 32), alignedZ = (int) tileKey;
            result.put(tileKey, new TerrainRelief(entry.getValue(), tiles.get(key(alignedX, alignedZ - Tile.SIZE)),
                    tiles.get(key(alignedX + Tile.SIZE, alignedZ)),
                    tiles.get(key(alignedX, alignedZ + Tile.SIZE)), tiles.get(key(alignedX - Tile.SIZE, alignedZ))));
        }
        return result;
    }

    private static void assertMatchesReference(Map<Long, Tile> tiles, FullscreenMapViewport view,
                                               FullscreenTerrainRaster raster, String description) {
        int[] pixels = raster.getPixels();
        for (int z = 0; z < view.getTextureHeight(); z++) {
            for (int x = 0; x < view.getTextureWidth(); x++) {
                check(pixels[z * view.getTextureWidth() + x] == reference(tiles, view, x, z), description);
            }
        }
    }

    private static int reference(Map<Long, Tile> tiles, FullscreenMapViewport view, int px, int pz) {
        double stride = view.getSampleStride();
        double startX = view.getOriginX() + px * stride, startZ = view.getOriginZ() + pz * stride;
        double endX = startX + stride, endZ = startZ + stride;
        double count = 0, red = 0, green = 0, blue = 0, alpha = 0;
        for (int z = (int) Math.floor(startZ); z < Math.ceil(endZ); z++) {
            double overlapZ = Math.min(endZ, z + 1.0) - Math.max(startZ, z);
            for (int x = (int) Math.floor(startX); x < Math.ceil(endX); x++) {
                double overlapX = Math.min(endX, x + 1.0) - Math.max(startX, x);
                double area = overlapX * overlapZ;
                int alignedX = Math.floorDiv(x + 64, 128) * 128;
                int alignedZ = Math.floorDiv(z + 64, 128) * 128;
                Tile tile = tiles.get(key(alignedX, alignedZ));
                int color = tile == null ? 0 : tile.getPixel(x - alignedX + 64, z - alignedZ + 64);
                if (area <= 0 || color >>> 24 == 0) continue;
                count += area;
                alpha += (color >>> 24) * area;
                red += (color >> 16 & 255) * area;
                green += (color >> 8 & 255) * area;
                blue += (color & 255) * area;
            }
        }
        return count == 0 ? 0 : channel(alpha / count) << 24 | channel(red / count) << 16
                | channel(green / count) << 8 | channel(blue / count);
    }
    private static int channel(double value) { return (int) Math.floor(value + 1e-9); }
    private static long key(int x, int z) { return (long) x << 32 | (z & 0xFFFFFFFFL); }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
