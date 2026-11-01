// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

public final class SettingSliderRangeTest {
    public static void main(String[] args) {
        testLinearEndpointsAndSnapping();
        testSavedScaleAndTransparencyValues();
        testLogarithmicRangesAndZero();
        testKeyboardStepping();
        testSpecialMinimum();
        System.out.println("Setting slider range tests passed.");
    }

    private static void testLinearEndpointsAndSnapping() {
        SettingSliderRange range = new SettingSliderRange(0.25, 2.0, 0.01, false);
        equal(0.0, range.toProgress(0.25), "minimum progress");
        equal(1.0, range.toProgress(2.0), "maximum progress");
        equal(0.25, range.fromProgress(-4.0), "minimum clamp");
        equal(2.0, range.fromProgress(4.0), "maximum clamp");
        equal(0.26, range.fromProgress(range.toProgress(0.255)), "nearest step rounding");
    }

    private static void testSavedScaleAndTransparencyValues() {
        SettingSliderRange scale = new SettingSliderRange(0.25, 2.0, 0.01, false);
        equal(0.9, scale.fromProgress(scale.toProgress(0.9)), "custom minimap scale round trip");

        SettingSliderRange transparency = new SettingSliderRange(0.1, 1.0, 0.01, false);
        equal(0.92, transparency.fromProgress(transparency.toProgress(0.92)), "custom transparency round trip");
    }

    private static void testLogarithmicRangesAndZero() {
        SettingSliderRange tickSeconds = new SettingSliderRange(0.001, 3600.0, 0.001, true);
        equal(0.001, tickSeconds.fromProgress(tickSeconds.toProgress(0.001)), "tick minimum round trip");
        equal(0.05, tickSeconds.fromProgress(tickSeconds.toProgress(0.05)), "default tick seconds round trip");
        equal(3600.0, tickSeconds.fromProgress(1.0), "tick maximum");

        SettingSliderRange distances = new SettingSliderRange(0.0, 128.0, 0.5, true);
        equal(0.0, distances.fromProgress(0.0), "log1p zero endpoint");
        equal(128.0, distances.fromProgress(1.0), "log1p maximum");
        equal(5.0, distances.fromProgress(distances.toProgress(5.0)), "distance round trip");

        SettingSliderRange million = new SettingSliderRange(0.0, 1_000_000.0, 1.0, true);
        equal(0.0, million.fromProgress(million.toProgress(0.0)), "million range zero");
        equal(1_000_000.0, million.fromProgress(million.toProgress(1_000_000.0)), "million range maximum");
        equal(50.0, million.fromProgress(million.toProgress(50.0)), "station radius round trip");
    }

    private static void testKeyboardStepping() {
        SettingSliderRange hundredths = new SettingSliderRange(0.1, 1.0, 0.01, false);
        equal(0.93, hundredths.stepBy(0.92, 1), "keyboard increases by one step");
        equal(0.91, hundredths.stepBy(0.92, -1), "keyboard decreases by one step");
        equal(0.935, hundredths.stepBy(0.925, 1), "off-grid keyboard value keeps exact step");
        equal(0.93, hundredths.stepBy(0.93, 0), "zero direction leaves value unchanged");
        equal(1.0, hundredths.stepBy(1.0, 1), "keyboard clamps at maximum");
        equal(0.1, hundredths.stepBy(0.1, -1), "keyboard clamps at minimum");

        SettingSliderRange tick = new SettingSliderRange(0.001, 3600.0, 0.001, true);
        equal(0.051, tick.stepBy(0.05, 1), "tick precision keyboard increase");
        equal(0.049, tick.stepBy(0.05, -1), "tick precision keyboard decrease");
    }

    private static void testSpecialMinimum() {
        SettingSliderRange labels = new SettingSliderRange(-1, 500, 1, false);
        equal(-1.0, labels.fromProgress(0.0), "all-label sentinel remains minimum");
        equal(-1.0, labels.stepBy(-1.0, -1), "sentinel keyboard clamp");
        equal(0.0, labels.stepBy(-1.0, 1), "sentinel next value");
    }

    private static void equal(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
