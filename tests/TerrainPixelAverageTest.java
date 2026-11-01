// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileColorAverage;

public final class TerrainPixelAverageTest {
    private static int assertions;
    public static void main(String[] args) {
        Tile tile = new Tile();
        tile.setPixel(0, 0, 0xFFFF0000);
        tile.setPixel(1, 0, 0xFF0000FF);
        tile.setPixel(2, 0, 0x00000000);
        TileColorAverage summary = new TileColorAverage(tile);
        long[] total = new long[5];
        summary.accumulate(0, 0, 3, 1, total);
        check(TileColorAverage.color(total) == 0xFF7F007F, "unknown does not darken exact rectangle average");
        check(total[0] == 2, "only known opaque colors contribute");
        check(summary.matches(tile), "unchanged image reuses summary");
        tile.setPixel(0, 0, 0xFFFFFFFF);
        check(!summary.matches(tile), "live resampling invalidates summary");
        Tile loaded = new Tile();
        TileColorAverage empty = new TileColorAverage(loaded);
        loaded.mergeLoaded(tile.snapshot());
        check(!empty.matches(loaded), "late disk load invalidates summary");
        java.util.Arrays.fill(total, 0);
        summary.accumulate(4, 4, 8, 8, total);
        check(TileColorAverage.color(total) == 0, "unknown rectangle stays transparent");
        Tile other = new Tile(); other.setPixel(127, 127, 0xFF00FF00);
        TileColorAverage second = new TileColorAverage(other);
        java.util.Arrays.fill(total, 0);
        summary.accumulate(0, 0, 2, 1, total);
        second.accumulate(127, 127, 128, 128, total);
        check(TileColorAverage.color(total) == 0xFF555555, "footprints across tiles combine by observed area");
        check(total[0] == 3, "cross-tile average weights every observed source pixel");
        fractionalRectanglesUseExactAreaWeights();
        System.out.println("Terrain pixel average tests passed (" + assertions + " assertions).");
    }

    private static void fractionalRectanglesUseExactAreaWeights() {
        Tile tile = new Tile();
        tile.setPixel(0, 0, 0xFFFF0000);
        tile.setPixel(1, 0, 0xFF0000FF);
        tile.setPixel(0, 1, 0x00000000);
        tile.setPixel(1, 1, 0xFFFFFFFF);
        TileColorAverage summary = new TileColorAverage(tile);
        double[] total = new double[7];
        summary.accumulate(0.5, 0.5, 1.5, 1.5, total, 1);
        check(TileColorAverage.color(total, 1) == 0xFFAA55AA,
                "fractional rectangle averages actual overlap and excludes transparent terrain");
        check(Math.abs(total[1] - 0.75) < 1e-9,
                "fractional area total is the observed nontransparent coverage");

        Tile solid = new Tile();
        solid.setPixel(7, 9, 0xFFFFFFFF);
        solid.setPixel(8, 9, 0xFFFFFFFF);
        solid.setPixel(7, 10, 0xFFFFFFFF);
        solid.setPixel(8, 10, 0xFFFFFFFF);
        TileColorAverage solidSummary = new TileColorAverage(solid);
        double[] solidTotal = new double[5];
        solidSummary.accumulate(7.2, 9.2, 8.8, 10.8, solidTotal);
        check(TileColorAverage.color(solidTotal) == 0xFFFFFFFF,
                "fractional summation roundoff cannot turn full white into channel value 254");

        Tile left = new Tile(); left.setPixel(127, 20, 0xFFFF0000);
        Tile right = new Tile(); right.setPixel(0, 20, 0xFF0000FF);
        double[] acrossBoundary = new double[5];
        new TileColorAverage(left).accumulate(127.5, 20, 128, 21, acrossBoundary);
        new TileColorAverage(right).accumulate(0, 20, 0.5, 21, acrossBoundary);
        check(TileColorAverage.color(acrossBoundary) == 0xFF7F007F,
                "fractional footprints from adjacent tiles combine by observed area");
        check(Math.abs(acrossBoundary[0] - 1) < 1e-9,
                "cross-tile fractional footprint retains total contributing area");
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
