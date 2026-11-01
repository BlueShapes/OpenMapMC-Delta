// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

public final class CaveLayerStepsTest {
    private static int assertions;

    public static void main(String[] args) {
        standardWorldBands();
        partialAndSmallWorlds();
        changingStepAndInvalidInput();
        bandCoverageAndSliderRoundTrips();
        System.out.println("Cave layer checks passed (" + assertions + " assertions).");
    }

    private static void standardWorldBands() {
        equal(49, CaveLayerSteps.layerCount(-64, 319, 8), "48 cave bands plus Surface");
        equal(-64, CaveLayerSteps.fromSlider(0, -64, 319, 8), "bottom slider anchor");
        equal(128, CaveLayerSteps.fromSlider(0.5, -64, 319, 8), "middle slider anchor");
        equal(312, CaveLayerSteps.fromSlider(47.0 / 48, -64, 319, 8), "last cave band");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.fromSlider(1, -64, 319, 8), "Surface endpoint");
        equal(-64, CaveLayerSteps.quantize(-63, -64, 319, 8), "negative lower band");
        equal(-8, CaveLayerSteps.quantize(-1, -64, 319, 8), "negative Y rounds toward anchor");
        equal(0, CaveLayerSteps.quantize(7, -64, 319, 8), "positive lower band");
        equal(312, CaveLayerSteps.quantize(319, -64, 319, 8), "maximum world Y");
        equal(-64, CaveLayerSteps.quantize(-1000, -64, 319, 8), "below build height");
        equal(312, CaveLayerSteps.quantize(1000, -64, 319, 8), "above build height");
        equal(-1, CaveLayerSteps.bandEnd(-8, -64, 319, 8), "inclusive negative band end");
        equal(319, CaveLayerSteps.bandEnd(312, -64, 319, 8), "inclusive final band end");
        close(1, CaveLayerSteps.toSlider(CaveLayerSteps.SURFACE, -64, 319, 8), "Surface slider position");
        close(23.0 / 48, CaveLayerSteps.toSlider(127, -64, 319, 8), "arbitrary Y snaps before conversion");
    }

    private static void partialAndSmallWorlds() {
        equal(49, CaveLayerSteps.layerCount(-60, 318, 8), "nonzero anchor and partial final band");
        equal(316, CaveLayerSteps.quantize(318, -60, 318, 8), "partial-band anchor");
        equal(318, CaveLayerSteps.bandEnd(316, -60, 318, 8), "partial band is bounded");
        equal(-60, CaveLayerSteps.quantize(-59, -60, 318, 8), "anchor follows world minimum");
        equal(2, CaveLayerSteps.layerCount(10, 12, 8), "one small cave band and Surface");
        equal(10, CaveLayerSteps.fromSlider(0, 10, 12, 8), "small-world selection");
        equal(12, CaveLayerSteps.bandEnd(10, 10, 12, 8), "small-world band end");
        equal(2, CaveLayerSteps.layerCount(3, 3, 8), "one-block world");
        equal(1, CaveLayerSteps.layerCount(3, 2, 8), "invalid world has only Surface");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.quantize(0, 3, 2, 8), "invalid world quantization");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.fromSlider(0, 3, 2, 8), "invalid world selection");
        close(1, CaveLayerSteps.toSlider(0, 3, 2, 8), "invalid world slider value");
    }

    private static void changingStepAndInvalidInput() {
        equal(8, CaveLayerSteps.quantize(13, -64, 319, 8), "old step");
        equal(0, CaveLayerSteps.quantize(13, -64, 319, 16), "new step requantizes anchor");
        equal(385, CaveLayerSteps.layerCount(-64, 319, 0), "invalid low step bounded to one");
        equal(7, CaveLayerSteps.layerCount(-64, 319, 100), "invalid high step bounded to 64");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.fromSlider(Double.NaN, -64, 319, 8), "NaN is Surface");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.fromSlider(Double.POSITIVE_INFINITY, -64, 319, 8), "positive infinity is Surface");
        equal(-64, CaveLayerSteps.fromSlider(Double.NEGATIVE_INFINITY, -64, 319, 8), "negative infinity is bounded");
        equal(-64, CaveLayerSteps.fromSlider(-10, -64, 319, 8), "negative slider value is bounded");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.fromSlider(10, -64, 319, 8), "large slider value is bounded");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.quantize(CaveLayerSteps.SURFACE, -64, 319, 8), "Surface stays Surface");
        equal(CaveLayerSteps.SURFACE, CaveLayerSteps.bandEnd(CaveLayerSteps.SURFACE, -64, 319, 8), "Surface has no cave band");
        equal(Integer.MAX_VALUE, CaveLayerSteps.bandEnd(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 20, Integer.MAX_VALUE, 8), "high bounds avoid integer overflow");
    }

    private static void bandCoverageAndSliderRoundTrips() {
        for (int[] bounds : new int[][]{{-64, 319}, {-60, 318}, {10, 12}}) {
            int min = bounds[0], max = bounds[1];
            for (int step = 1; step <= 64; step++) {
                int expectedBands = (max - min + step) / step;
                equal(expectedBands + 1, CaveLayerSteps.layerCount(min, max, step), "all configured steps count bands");
                int next = min;
                for (int i = 0; i < expectedBands; i++) {
                    int lower = min + i * step;
                    equal(next, lower, "bands cover height without gaps");
                    int end = CaveLayerSteps.bandEnd(lower, min, max, step);
                    check(end >= lower && end <= max && end - lower < step, "band end is inclusive and bounded");
                    equal(lower, CaveLayerSteps.quantize(end, min, max, step), "entire band maps to its lower anchor");
                    double slider = CaveLayerSteps.toSlider(lower, min, max, step);
                    check(slider >= 0 && slider < 1, "cave slider positions never select Surface");
                    equal(lower, CaveLayerSteps.fromSlider(slider, min, max, step), "slider anchor round trip");
                    next = end + 1;
                }
                equal(max + 1, next, "last band covers world maximum");
            }
        }
    }

    private static void equal(int expected, int actual, String message) {
        check(expected == actual, message + ": expected " + expected + ", got " + actual);
    }

    private static void close(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1.0e-12, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
