// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.OptionalDouble;

/** Standalone boundary tests for the dependency-free minimap info math helpers. */
public final class MinimapInfoMathTest {
    private static int assertions;

    public static void main(String[] args) {
        timeFormatting();
        rollingMotion();
        staleServerTicks();
        System.out.println("Minimap info math tests passed (" + assertions + " assertions).");
    }

    private static void timeFormatting() {
        check(MinimapInfoMath.normalizedDayTick(-1) == 23_999, "negative daytime wraps to previous day");
        check(MinimapInfoMath.normalizedDayTick(24_001) == 1, "daytime wraps after midnight");
        check(MinimapInfoMath.formatClock(MinimapInfoMath.clock24(0)).equals("06:00"), "24-hour clock starts at six");
        check(MinimapInfoMath.formatClock(MinimapInfoMath.clock24(500)).equals("06:30"), "24-hour clock converts half-hour tick offset");
        check(MinimapInfoMath.formatClock(MinimapInfoMath.clock24(1_000)).equals("07:00"), "24-hour clock advances at one thousand ticks per hour");
        check(MinimapInfoMath.formatClock(MinimapInfoMath.clock24(5_999)).equals("11:59"), "24-hour clock reaches the next hour");
        check(MinimapInfoMath.formatClock(MinimapInfoMath.clock24(18_000)).equals("00:00"), "24-hour clock wraps at midnight");
        check(MinimapInfoMath.formatClock(MinimapInfoMath.clock12(18_000)).equals("12:00"), "12-hour clock displays midnight as twelve");
        check(MinimapInfoMath.formatClock12(18_000, "AM").equals("12:00 AM"), "12-hour clock disambiguates midnight");
        check(MinimapInfoMath.formatClock12(0, "PM").equals("06:00 PM"), "12-hour clock disambiguates evening");
        check(MinimapInfoMath.customClock(20, 0.05).equals("00:01"), "custom clock applies seconds per daytime tick");
        check(MinimapInfoMath.customClock(-1, 0.05).equals("19:59"), "custom clock wraps negative daytime");
        check(MinimapInfoMath.worldDay(-1) == -1, "world day uses floor division for negative daytime");
        check(MinimapInfoMath.worldDay(48_000) == 2, "world day counts daytime boundaries");
    }

    private static void rollingMotion() {
        RollingPathAverage average = new RollingPathAverage();
        average.add(0L, 0.0);
        average.add(1_000_000_000L, 5.0);
        check(close(average.blocksPerSecond(1_000_000_000L, 5_000_000_000L), 5.0), "path speed uses elapsed wall time");
        average.add(2_000_000_000L, 0.0);
        check(close(average.blocksPerSecond(2_000_000_000L, 5_000_000_000L), 2.5), "stationary time lowers average speed");
        check(close(average.blocksPerSecond(2_000_000_000L, 1_000_000_000L), 0.0), "short window retains stationary interval");
        average.reset();
        average.add(3_000_000_000L, 0.0);
        average.add(4_000_000_000L, 2.0);
        check(close(average.blocksPerSecond(4_000_000_000L, 5_000_000_000L), 2.0), "reset removes prior dimension path");
    }

    private static void staleServerTicks() {
        ServerTickEstimate estimate = new ServerTickEstimate();
        check(estimate.ticksPerSecond(0L).isEmpty(), "no TPS before first interval");
        estimate.observe(100L, 0L);
        estimate.observe(120L, 1_000_000_000L);
        OptionalDouble tps = estimate.ticksPerSecond(1_000_000_000L);
        check(tps.isPresent() && close(tps.getAsDouble(), 20.0), "server TPS uses observed packet timing");
        estimate.observe(142L, 2_000_000_000L);
        check(close(estimate.ticksPerSecond(2_000_000_000L).orElseThrow(), 22.0), "packet jitter estimate above 20 TPS is retained");
        estimate.observe(182L, 3_000_000_000L);
        check(close(estimate.ticksPerSecond(3_000_000_000L).orElseThrow(), 40.0), "server configured above 20 TPS is measurable");
        estimate.observe(282L, 4_000_000_000L);
        check(close(estimate.ticksPerSecond(4_000_000_000L).orElseThrow(), 100.0), "high configured server TPS is measurable");
        check(estimate.ticksPerSecond(5_000_000_000L).isPresent(), "TPS remains fresh within timeout");
        check(estimate.ticksPerSecond(9_000_000_001L).isEmpty(), "stale TPS is unavailable");
        estimate.observe(5L, 10_000_000_000L);
        check(estimate.ticksPerSecond(10_000_000_000L).isEmpty(), "server clock reset clears old TPS estimate");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000_001;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
