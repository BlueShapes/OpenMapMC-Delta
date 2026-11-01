// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Standalone chunk boundary regression tests; no Minecraft startup is required. */
public final class FullscreenChunkGridTest {
    private static int assertions;

    public static void main(String[] args) {
        knownBoundariesRespectPanAndNegativeCoordinates();
        fractionalPanningMovesLinesWithTerrain();
        allVisibleLinesAreChunkBoundaries();
        linesAlignWithCachedTerrainAfterZoomAndResize();
        coarseZoomGroupsWorldAlignedChunks();
        finiteGeometryAndThicknessAreBounded();
        System.out.println("Fullscreen chunk grid tests passed (" + assertions + " assertions).");
    }

    private static void knownBoundariesRespectPanAndNegativeCoordinates() {
        near(FullscreenChunkGrid.firstLineOffset(64, 0, 1), 0, "boundary at left edge is included");
        near(FullscreenChunkGrid.firstLineOffset(64, 0, 0.5), 0, "2x boundary at left edge is included");
        near(FullscreenChunkGrid.lineSpacing(0.5), 32, "2x chunks span thirty-two pixels");
        near(FullscreenChunkGrid.firstLineOffset(64, 5, 1), 11, "positive pan starts at world -16");
        near(FullscreenChunkGrid.firstLineOffset(64, -5, 1), 5, "negative pan starts at world -32");
        near(FullscreenChunkGrid.firstLineOffset(65, 5, 1), 11, "odd panel uses integer terrain center");
        near(FullscreenChunkGrid.firstLineOffset(64, 12.5, 3), 3.5 / 3,
                "nonintegral spacing starts at world -80 without rounding");
        near(FullscreenChunkGrid.firstLineOffset(64, 32.25, 1), 15.75,
                "boundary outside left edge is excluded");
    }

    private static void fractionalPanningMovesLinesWithTerrain() {
        double before = FullscreenChunkGrid.firstLineOffset(731, 103.25, 2);
        double after = FullscreenChunkGrid.firstLineOffset(731, 103.75, 2);
        near(after - before, -0.25, "fractional pan moves boundary by fractional GUI pixels");
        near(FullscreenChunkGrid.firstLineOffset(64, 0.25, 1), 15.75,
                "next chunk becomes first when previous boundary leaves panel");
    }

    private static void allVisibleLinesAreChunkBoundaries() {
        for (int length : new int[] { 64, 65, 731, 2561 }) {
            for (double bpp : new double[] { 0.5, 1, 2, 3, 4, 5, 6, 7, 13, 14, 16, 32, 64, 128, 256 }) {
                for (double pan : new double[] { 0, 0.25, -0.25, 103.25, -517.5, 29999999.75, -29999999.75 }) {
                    double first = FullscreenChunkGrid.firstLineOffset(length, pan, bpp);
                    FullscreenChunkGrid.Layout grid = FullscreenChunkGrid.layout(bpp, length, 0.5);
                    double spacing = grid.lineSpacing();
                    check(first >= 0 && first < spacing, "first line is the first visible chunk boundary");
                    double previousWorld = Double.NaN;
                    for (double offset = first; offset < length; offset += spacing) {
                        double world = pan + (offset - length / 2) * bpp;
                        near(world, Math.rint(world / grid.worldSpacing()) * grid.worldSpacing(), "every line aligns with its world-origin group");
                        if (!Double.isNaN(previousWorld)) {
                            near(world - previousWorld, grid.worldSpacing(), "adjacent lines enclose the selected power-of-two chunk group");
                        }
                        previousWorld = world;
                    }
                }
            }
        }
    }

    private static void linesAlignWithCachedTerrainAfterZoomAndResize() {
        FullscreenMapViewport cached = FullscreenMapViewport.forView(800, 600, 2, -1200.25, 70.75);
        double panX = -1199.75;
        double panZ = 70.25;
        for (int[] view : new int[][] { {800, 600, 2}, {931, 731, 3}, {669, 469, 1}, {2561, 1441, 4} }) {
            int bpp = view[2];
            double offsetX = FullscreenChunkGrid.firstLineOffset(view[0], panX, bpp);
            double offsetY = FullscreenChunkGrid.firstLineOffset(view[1], panZ, bpp);
            double worldX = panX + (offsetX - view[0] / 2) * bpp;
            double worldZ = panZ + (offsetY - view[1] / 2) * bpp;
            double terrainX = cached.drawX(17, panX, bpp, view[0])
                    + (worldX - cached.getOriginX()) / cached.getSampleStride() * cached.renderScale(bpp);
            double terrainY = cached.drawY(23, panZ, bpp, view[1])
                    + (worldZ - cached.getOriginZ()) / cached.getSampleStride() * cached.renderScale(bpp);
            near(17 + offsetX, terrainX, "chunk X boundary follows current terrain draw transform");
            near(23 + offsetY, terrainY, "chunk Z boundary follows current terrain draw transform");
        }
    }

    private static void coarseZoomGroupsWorldAlignedChunks() {
        near(FullscreenChunkGrid.lineSpacing(1), 16, "one-block scale has sixteen-pixel chunks");
        near(FullscreenChunkGrid.lineSpacing(3), 16.0 / 3, "chunk spacing is never rounded");
        for (int bpp = 1; bpp <= 6; bpp++) {
            check(FullscreenChunkGrid.layout(bpp, 1000, 0.5).worldSpacing() == 16,
                    "every chunk remains at or above 0.15x: bpp " + bpp);
        }
        check(FullscreenChunkGrid.layout(7, 1000, 0.5).worldSpacing() == 32,
                "below 0.15x uses two-chunk groups");
        check(FullscreenChunkGrid.layout(13, 1000, 0.5).worldSpacing() == 32,
                "two-chunk groups stay until their spacing falls below 2.4 pixels");
        check(FullscreenChunkGrid.layout(14, 1000, 0.5).worldSpacing() == 64,
                "next threshold uses four-chunk groups");
        for (int bpp = 7; bpp <= 2048; bpp++) {
            FullscreenChunkGrid.Layout grid = FullscreenChunkGrid.layout(bpp, 1000, 0.5);
            long chunks = grid.worldSpacing() / 16;
            check(chunks >= 2 && (chunks & (chunks - 1)) == 0, "groups are powers of two");
            check(grid.lineSpacing() >= 2.4 && grid.lineSpacing() < 4.8,
                    "grouped grids retain a bounded line density");
            near(FullscreenChunkGrid.firstLineOffset(731, -517.5, bpp),
                    grid.firstLineOffset(731, -517.5), "public axis helper uses the grouped layout");
        }
    }

    private static void finiteGeometryAndThicknessAreBounded() {
        for (int extent : new int[]{0, -1, 65, 731, 10000, Integer.MAX_VALUE}) {
            for (int bpp : new int[]{Integer.MIN_VALUE, 0, 1, 6, 7, 256, Integer.MAX_VALUE}) {
                for (double width : new double[]{0.25, 0.5, 4.0, 100, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                    FullscreenChunkGrid.Layout grid = FullscreenChunkGrid.layout(bpp, extent, width);
                    check(Double.isFinite(grid.lineSpacing()) && grid.lineSpacing() > 0.0,
                            "invalid and extreme zooms keep finite positive spacing");
                    check(Float.isFinite(grid.lineThickness()) && grid.lineThickness() > 0.0
                                    && grid.lineThickness() <= grid.lineSpacing() / 4.0,
                            "width stays finite and leaves a gap between adjacent lines");
                    double first = grid.firstLineOffset(extent, -29999999.75);
                    int count = grid.lineCount(extent, first);
                    check(count >= 0 && count <= FullscreenChunkGrid.MAX_LINES_PER_AXIS,
                            "geometry is bounded at extreme panel extents");
                    if (extent <= 0) check(count == 0, "empty panels have no lines");
                    else {
                        check(first >= 0 && first < grid.lineSpacing(), "first extreme-axis line is bounded");
                        double last = first + (count - 1) * grid.lineSpacing();
                        check(count > 0 && last < extent && last + grid.lineSpacing() >= extent,
                                "bounded geometry covers the whole panel");
                    }
                }
            }
        }
        FullscreenChunkGrid.Layout detail = FullscreenChunkGrid.layout(6, 1000, 0.5);
        check(detail.lineThickness() < 0.5F, "dense actual chunk boundaries become thinner");
        for (double pan : new double[]{Double.MAX_VALUE, -Double.MAX_VALUE}) {
            double first = detail.firstLineOffset(731, pan);
            check(Double.isFinite(first) && first >= 0 && first < detail.lineSpacing(),
                    "extreme finite pans do not overflow boundary calculations");
        }
        for (double pan : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(detail.lineCount(731, detail.firstLineOffset(731, pan)) == 0,
                    "nonfinite pans produce no geometry");
        }
    }

    private static void near(double actual, double expected, String description) {
        check(Math.abs(actual - expected) < 1e-8, description + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
