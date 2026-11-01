// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

public final class MinimapLayerTransitionTest {
    private static final long MS = 1_000_000L;
    private static final int RED = 0xFFBB2211, BLUE = 0xFF1133CC, GREEN = 0xFF228844;
    private static int assertions;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();

    public static void main(String[] args) {
        partialArrivalAndKnownTransparency();
        worldCoordinatesAndZoom();
        boundedRetention();
        isolationAndRapidCrossings();
        zoomedMovementBetweenSamples();
        fractionalStrideUsesNearestRetainedSample();
        fractionalStrideChangeInvalidatesRetention();
        repeatedUnknownBandsKeepOriginalDeadline();
        enteringCoverKeepsSurfaceWhileCaveLoads();
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            throw new AssertionError("Minimap transition checks failed: " + failures.size());
        }
        System.out.println("Minimap transition tests passed (" + assertions + " assertions).");
    }

    private static void partialArrivalAndKnownTransparency() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 1, 0, RED, GREEN, true, true);
        int[] pending = frame(transition, world, 1, 72, 0, 0, 1, MS, 0, 0, false, false);
        check(pending[0] == RED && pending[1] == GREEN, "empty new band retains previous visible terrain");
        int[] partial = frame(transition, world, 1, 72, 0, 0, 1, 2 * MS, BLUE, 0, true, false);
        check(partial[0] == BLUE && partial[1] == GREEN, "arriving pixels replace terrain without blanking unfinished pixels");
        int[] ready = frame(transition, world, 1, 72, 0, 0, 1, 3 * MS, BLUE, 0, true, true);
        check(ready[0] == BLUE && ready[1] == 0, "observed transparent pixel clears old terrain");
        int[] empty = frame(transition, world, 1, 72, 0, 0, 1, 4 * MS, 0, 0, false, false);
        check(empty[0] == 0 && empty[1] == 0, "completed transition discards fallback");
    }

    private static void worldCoordinatesAndZoom() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, -2, -3, 2, 0, RED, GREEN, true, true);
        int[] moved = frame(transition, world, 1, 72, 0, -3, 2, MS, 0, 0, false, false);
        check(moved[0] == GREEN && moved[1] == 0, "previous terrain follows world coordinates during movement");
        int[] zoomed = frame(transition, world, 1, 72, 0, -3, 1, 2 * MS, 0, 0, false, false);
        check(zoomed[0] == 0 && zoomed[1] == 0, "zoom change rejects stale raster sampling");
    }

    private static void boundedRetention() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 1, 0, RED, GREEN, true, true);
        frame(transition, world, 1, 72, 0, 0, 1, MS, 0, 0, false, false);
        int[] held = frame(transition, world, 1, 72, 0, 0, 1, 1001 * MS, 0, 0, false, false);
        check(held[0] == RED, "fallback stays opaque while data loads");
        int[] faded = frame(transition, world, 1, 72, 0, 0, 1, 1101 * MS, 0, 0, false, false);
        check((faded[0] >>> 24) >= 126 && (faded[0] >>> 24) <= 128, "unavailable terrain fades after the bounded hold");
        check((faded[0] & 0xFFFFFF) == (RED & 0xFFFFFF), "fade preserves texture channel order");
        int[] expired = frame(transition, world, 1, 72, 0, 0, 1, 1201 * MS, 0, 0, false, false);
        check(expired[0] == 0 && expired[1] == 0, "unavailable band cannot retain old terrain indefinitely");
    }

    private static void isolationAndRapidCrossings() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 1, 0, RED, GREEN, true, true);
        frame(transition, world, 1, 72, 0, 0, 1, MS, BLUE, 0, true, false);
        int[] reversed = frame(transition, world, 1, 64, 0, 0, 1, 2 * MS, 0, 0, false, false);
        check(reversed[0] == BLUE && reversed[1] == GREEN, "rapid band reversal retains the currently visible image");
        int[] denied = frame(transition, world, 1, Integer.MIN_VALUE, 0, 0, 1, 3 * MS, 0, 0, false, false);
        check(denied[0] == 0 && denied[1] == 0, "Surface or policy denial never retains cave terrain");
        frame(transition, world, 1, 64, 0, 0, 1, 4 * MS, RED, GREEN, true, true);
        int[] session = frame(transition, world, 2, 72, 0, 0, 1, 5 * MS, 0, 0, false, false);
        check(session[0] == 0 && session[1] == 0, "session epoch change discards previous terrain");
        frame(transition, world, 2, 64, 0, 0, 1, 6 * MS, RED, GREEN, true, true);
        int[] dimension = frame(transition, new Object(), 2, 72, 0, 0, 1, 7 * MS, 0, 0, false, false);
        check(dimension[0] == 0 && dimension[1] == 0, "level identity change discards previous terrain");
        frame(transition, world, 2, 64, 0, 0, 1, 8 * MS, RED, GREEN, true, true);
        transition.clear();
        int[] cleared = frame(transition, world, 2, 72, 0, 0, 1, 9 * MS, 0, 0, false, false);
        check(cleared[0] == 0 && cleared[1] == 0, "explicit texture clear discards retention");
    }

    private static void zoomedMovementBetweenSamples() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 2, 0, RED, GREEN, true, true);
        int[] moved = frame(transition, world, 1, 72, 1, 0, 2, MS, 0, 0, false, false);
        check(moved[0] == GREEN && moved[1] == 0, "one-block movement at zoom 2 uses nearest world sample");
    }

    private static void fractionalStrideUsesNearestRetainedSample() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 1.5, 0, RED, GREEN, true, true);
        int[] panned = frame(transition, world, 1, 72, 1, 0, 1.5, MS, 0, 0, false, false);
        check(panned[0] == GREEN,
                "fractional physical sampling reprojects to the nearest retained world sample");
        check(panned[1] == 0,
                "fractional reprojection leaves samples beyond the retained world extent transparent");
    }

    private static void fractionalStrideChangeInvalidatesRetention() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 1.25, 0, RED, GREEN, true, true);
        int[] zoomed = frame(transition, world, 1, 72, 0, 0, 1.5, MS, 0, 0, false, false);
        check(zoomed[0] == 0 && zoomed[1] == 0,
                "a physical sample-step change rejects stale retained raster data");
    }

    private static void repeatedUnknownBandsKeepOriginalDeadline() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, 64, 0, 0, 1, 0, RED, GREEN, true, true);
        frame(transition, world, 1, 72, 0, 0, 1, MS, 0, 0, false, false);
        frame(transition, world, 1, 80, 0, 0, 1, 601 * MS, 0, 0, false, false);
        int[] faded = frame(transition, world, 1, 88, 0, 0, 1, 1101 * MS, 0, 0, false, false);
        check((faded[0] >>> 24) >= 126 && (faded[0] >>> 24) <= 128, "crossing bands does not restart retention time");
        int[] moreFade = frame(transition, world, 1, 96, 0, 0, 1, 1151 * MS, 0, 0, false, false);
        check((moreFade[0] >>> 24) >= 62 && (moreFade[0] >>> 24) <= 64, "crossing bands does not compound the fade alpha");
        int[] expired = frame(transition, world, 1, 104, 0, 0, 1, 1201 * MS, 0, 0, false, false);
        check(expired[0] == 0 && expired[1] == 0, "continuous crossings cannot retain stale terrain indefinitely");
    }

    private static void enteringCoverKeepsSurfaceWhileCaveLoads() {
        Object world = new Object();
        MinimapLayerTransition transition = new MinimapLayerTransition();
        frame(transition, world, 1, Integer.MIN_VALUE, 0, 0, 1, 0, RED, GREEN, true, true);
        int[] covered = frame(transition, world, 1, 64, 0, 0, 1, MS, 0, 0, false, false);
        check(covered[0] == RED && covered[1] == GREEN, "entering cover retains Surface until cave terrain arrives");
        int[] denied = frame(transition, world, 1, Integer.MIN_VALUE, 0, 0, 1, 2 * MS, 0, 0, false, false);
        check(denied[0] == 0 && denied[1] == 0, "return to Surface still drops retained cave pixels immediately");
    }

    private static int[] frame(MinimapLayerTransition transition, Object world, long epoch, int layer,
                               long x, long z, double stride, long time, int first, int second,
                               boolean observedFirst, boolean observedSecond) {
        transition.begin(world, epoch, layer, 2, 1, x, z, stride, time);
        int[] pixels = {transition.pixel(0, 0, first, observedFirst), transition.pixel(1, 0, second, observedSecond)};
        transition.end();
        return pixels;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) failures.add(message);
    }
}
