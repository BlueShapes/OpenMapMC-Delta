// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Standalone geometry regression tests; no Minecraft or test framework is required. */
public final class FullscreenMapViewportTest {
    private static int assertions;

    public static void main(String[] args) {
        fractionalPanKeepsTextureAndMovesDrawOffset();
        originsSnapAcrossPositiveAndNegativeCoordinates();
        recenteringWaitsUntilTheInsetIsExceeded();
        cachedAndRecenteredTerrainStayAligned();
        resizedPanelsUseTheirCurrentCenterBeforeRecompose();
        largeOddPanelsStayBoundedAndAligned();
        zoomAndSizeChangesInvalidateShape();
        extremePanelsStayWithinGpuBound();
        twoTimesZoomUsesWholeBlockSamplesAndAlignedDrawing();
        guiScaleControlsPhysicalSampleDensity();
        guiScaleChangesInvalidateShape();
        fractionalSamplesStayAlignedAtNegativeCoordinates();
        highDpiExtremePanelsStayWithinGpuBound();
        System.out.println("Fullscreen map viewport tests passed (" + assertions + " assertions).");
    }

    private static void fractionalPanKeepsTextureAndMovesDrawOffset() {
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(731, 511, 2, 103.25, -71.75);
        double x = viewport.drawX(7, 103.25, 2);
        double y = viewport.drawY(11, -71.75, 2);
        double nextX = viewport.drawX(7, 103.75, 2);
        double nextY = viewport.drawY(11, -71.25, 2);
        check(viewport.containsView(103.75, -71.25), "small fractional pan reuses cached viewport");
        check(Math.abs((nextX - x) + 0.25) < 1e-9, "fractional X pan moves without pixel quantization");
        check(Math.abs((nextY - y) + 0.25) < 1e-9, "fractional Z pan moves without pixel quantization");
    }

    private static void originsSnapAcrossPositiveAndNegativeCoordinates() {
        for (double pan : new double[] { 517.5, -517.5 }) {
            FullscreenMapViewport viewport = FullscreenMapViewport.forView(900, 700, 3, pan, pan);
            check(viewport.getOriginX() % viewport.getSampleStride() == 0, "positive/negative X origin snaps");
            check(viewport.getOriginZ() % viewport.getSampleStride() == 0, "positive/negative Z origin snaps");
            check(viewport.containsView(pan, pan), "newly created view is within its inset");
        }
    }

    private static void recenteringWaitsUntilTheInsetIsExceeded() {
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(800, 600, 1, 0, 0);
        check(viewport.containsView(0, 0), "initial center is cached");
        check(viewport.containsView(viewport.getSampleStride() * 15.0, 0), "small pan reuses cached terrain");
        check(!viewport.containsView(viewport.getSampleStride() * 17.0, 0), "large pan requests recenter");
    }

    private static void cachedAndRecenteredTerrainStayAligned() {
        int panelWidth = 800;
        int panelHeight = 600;
        int panelX = 17;
        int panelY = 23;
        double panX = 123.25;
        double panZ = -402.75;
        FullscreenMapViewport original = FullscreenMapViewport.forView(panelWidth, panelHeight, 2, panX, panZ);
        double worldX = 510.5;
        double worldZ = 87.25;
        checkAligned(original, panelX, panelY, panX, panZ, worldX, worldZ, 2,
                "cached terrain aligns with arbitrary world point");

        double movedX = panX + original.getSampleStride() * 17.0;
        double movedZ = panZ - original.getSampleStride() * 17.0;
        FullscreenMapViewport recentered = FullscreenMapViewport.forView(
                panelWidth, panelHeight, 2, movedX, movedZ);
        checkAligned(recentered, panelX, panelY, movedX, movedZ, worldX, worldZ, 2,
                "recentered terrain keeps arbitrary world point aligned");

        checkAligned(original, panelX, panelY, panX, panZ, worldX, worldZ, 4,
                "old cached terrain scales immediately for a changed zoom");
    }

    private static void checkAligned(FullscreenMapViewport viewport, int panelX, int panelY,
                                     double panX, double panZ, double worldX, double worldZ,
                                     double currentBpp, String description) {
        checkAligned(viewport, panelX, panelY, panX, panZ, worldX, worldZ, currentBpp,
                viewport.getPanelWidth(), viewport.getPanelHeight(), description);
    }

    private static void checkAligned(FullscreenMapViewport viewport, int panelX, int panelY,
                                     double panX, double panZ, double worldX, double worldZ,
                                     double currentBpp, int currentPanelWidth, int currentPanelHeight,
                                     String description) {
        double actualX = viewport.drawX(panelX, panX, currentBpp, currentPanelWidth)
                + (worldX - viewport.getOriginX()) / viewport.getSampleStride()
                * viewport.renderScale(currentBpp);
        double actualY = viewport.drawY(panelY, panZ, currentBpp, currentPanelHeight)
                + (worldZ - viewport.getOriginZ()) / viewport.getSampleStride()
                * viewport.renderScale(currentBpp);
        double expectedX = panelX + currentPanelWidth / 2
                + (worldX - panX) / currentBpp;
        double expectedY = panelY + currentPanelHeight / 2
                + (worldZ - panZ) / currentBpp;
        check(Math.abs(actualX - expectedX) < 1e-9, description + " (X)");
        check(Math.abs(actualY - expectedY) < 1e-9, description + " (Z)");
    }

    private static void resizedPanelsUseTheirCurrentCenterBeforeRecompose() {
        double panX = -1200.25;
        double panZ = 70.75;
        FullscreenMapViewport cached = FullscreenMapViewport.forView(800, 600, 2, panX, panZ);
        int[][] currentViews = {
                { 930, 600, 2 }, { 670, 600, 2 },
                { 800, 730, 2 }, { 800, 470, 2 },
                { 931, 731, 4 }, { 669, 469, 1 }
        };
        for (int[] view : currentViews) {
            checkAligned(cached, 17, 23, panX + 0.25, panZ - 0.5, panX + 86.25, panZ - 53.75,
                    view[2], view[0], view[1], "resized panel and current zoom match marker coordinates");
        }
        check(cached.getPanelWidth() == 800 && cached.getPanelHeight() == 600,
                "frame drawing does not change cached composition dimensions");
    }

    private static void largeOddPanelsStayBoundedAndAligned() {
        int width = 2561;
        int height = 1441;
        int panelX = 13;
        int panelY = 21;
        double panX = -1200.25;
        double panZ = 70.75;
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(width, height, 4, panX, panZ);
        check(viewport.getTextureWidth() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION && viewport.getTextureHeight() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION,
                "large panel texture remains bounded");
        check(viewport.getSampleScale() == 1, "ordinary large panel keeps one texel per GUI pixel");
        check(Math.abs(viewport.renderScale(4) - 1.0) < 1e-9, "cached terrain has uniform world/GUI scale");
        checkAligned(viewport, panelX, panelY, panX, panZ, panX, panZ, 4,
                "odd panel center matches integer marker center");
    }

    private static void zoomAndSizeChangesInvalidateShape() {
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(640, 480, 2, 0, 0);
        check(viewport.hasSameShape(640, 480, 2), "unchanged shape reuses cache");
        check(!viewport.hasSameShape(640, 480, 4), "zoom change invalidates cache shape");
        check(!viewport.hasSameShape(641, 480, 2), "size change invalidates cache shape");
    }

    private static void extremePanelsStayWithinGpuBound() {
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(8193, 4097, 16, -10.5, 33.25);
        check(viewport.getTextureWidth() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION, "extreme width stays bounded");
        check(viewport.getTextureHeight() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION, "extreme height stays bounded");
        check(viewport.containsView(-10.5, 33.25), "extreme view fits its cached inset");
    }

    private static void twoTimesZoomUsesWholeBlockSamplesAndAlignedDrawing() {
        for (int[] size : new int[][] {{731, 511}, {8193, 4097}}) {
            FullscreenMapViewport viewport = FullscreenMapViewport.forView(size[0], size[1], 0.5, -17.25, 33.75);
            check(viewport.containsView(-17.25, 33.75), "2x view fits the cached inset");
            check(viewport.getSampleStride() >= 1, "2x never samples fractional blocks");
            check(viewport.getTextureWidth() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION,
                    "2x texture width is bounded");
            check(viewport.getTextureHeight() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION,
                    "2x texture height is bounded");
            checkAligned(viewport, 7, 11, -17.25, 33.75, -16, 32, 0.5,
                    "2x terrain and markers share the world transform");
            check(viewport.hasSameShape(size[0], size[1], 0.5), "2x cache shape is reusable");
            check(!viewport.hasSameShape(size[0], size[1], 1), "2x to 1x invalidates sampling shape");
        }
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(731, 511, 0.5, -17.25, 33.75);
        check(viewport.getSampleStride() == 1, "ordinary 2x view uses one texel per block");
        check(Math.abs(viewport.renderScale(0.5) - 2) < 1e-9, "one block renders as two GUI pixels");
        checkAligned(viewport, 7, 11, -17, 33.25, -16, 32, 1,
                "cached 2x terrain remains aligned before a zoom-out recompose");
    }

    private static void guiScaleControlsPhysicalSampleDensity() {
        double[][] cases = {
                { 1, 4, 4 },
                { 2, 4, 2 },
                { 3, 4, 4.0 / 3.0 },
                { 1.5, 4, 8.0 / 3.0 },
                { 3, 0.5, 1 }
        };
        for (double[] testCase : cases) {
            double guiScale = testCase[0], bpp = testCase[1], expectedStride = testCase[2];
            FullscreenMapViewport viewport = FullscreenMapViewport.forView(900, 700, bpp,
                    -117.25, 238.75, guiScale);
            check(Math.abs(viewport.getSampleStride() - expectedStride) < 1e-9,
                    "sampling follows physical display density at GUI scale " + guiScale);
            check(Math.abs(viewport.renderScale(bpp) * guiScale
                            - Math.max(1, guiScale / bpp)) < 1e-9,
                    "rendered sample footprint matches the physical display pixel density");
            check(viewport.containsView(-117.25, 238.75), "physical-resolution view stays in its inset");
        }
    }

    private static void guiScaleChangesInvalidateShape() {
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(640, 480, 4, 0, 0, 2);
        check(viewport.hasSameShape(640, 480, 4, 2), "same GUI scale reuses physical-resolution cache");
        check(!viewport.hasSameShape(640, 480, 4, 1), "GUI scale change invalidates cached sampling shape");
        check(!viewport.hasSameShape(640, 480, 4, 3), "fractional density change invalidates cached shape");
        FullscreenMapViewport normalized = FullscreenMapViewport.forView(640, 480, 4, 0, 0, Double.NaN);
        check(normalized.hasSameShape(640, 480, 4, 0)
                        && normalized.hasSameShape(640, 480, 4, Double.POSITIVE_INFINITY),
                "nonpositive or nonfinite GUI scales normalize consistently to one");
    }

    private static void fractionalSamplesStayAlignedAtNegativeCoordinates() {
        double panX = -517.375, panZ = -91.625;
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(731, 511, 4,
                panX, panZ, 3);
        check(Math.abs(viewport.getSampleStride() - 4.0 / 3.0) < 1e-9,
                "fractional physical pixel footprint is preserved instead of rounded to blocks");
        checkAligned(viewport, 13, 19, panX, panZ, -388.125, -46.75, 4,
                "fractional samples stay aligned with markers at negative coordinates");
        check(viewport.containsView(panX, panZ), "negative pan remains inside the fractional-sample inset");
    }

    private static void highDpiExtremePanelsStayWithinGpuBound() {
        FullscreenMapViewport ordinary = FullscreenMapViewport.forView(900, 700, 4,
                23.5, -88.25, 3);
        check(Math.abs(ordinary.getSampleStride() - 4.0 / 3.0) < 1e-9,
                "normal high-DPI panel retains physical-pixel terrain detail");
        FullscreenMapViewport extreme = FullscreenMapViewport.forView(8193, 4097, 16,
                -10.5, 33.25, 3);
        check(extreme.getTextureWidth() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION,
                "extreme high-DPI width stays bounded by the GPU texture limit");
        check(extreme.getTextureHeight() <= FullscreenMapViewport.MAX_TEXTURE_DIMENSION,
                "extreme high-DPI height stays bounded by the GPU texture limit");
        check(extreme.containsView(-10.5, 33.25), "extreme high-DPI view fits its cached inset");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
