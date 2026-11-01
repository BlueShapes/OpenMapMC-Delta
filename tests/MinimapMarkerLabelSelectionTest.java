// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.List;

public final class MinimapMarkerLabelSelectionTest {
    private static int assertions;

    public static void main(String[] args) {
        List<MinimapMarkerLabelSelection.Candidate<String>> candidates = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            candidates.add(candidate("waypoint-" + i, "id-" + i, i, true, true, false));
        }
        candidates.add(candidate("near-offscreen", "id-offscreen", 0.1, false, true, false));
        candidates.add(candidate("destination", "id-destination", 0.0, true, true, true));
        candidates.add(candidate("notify", "id-notify", 0.0, true, true, true));

        List<String> five = MinimapMarkerLabelSelection.select(candidates, 5);
        check(five.equals(List.of("waypoint-1", "waypoint-2", "waypoint-3", "waypoint-4", "waypoint-5")),
                "nearest five visible ordinary labels are selected; offscreen and emphasized markers do not use slots");
        check(MinimapMarkerLabelSelection.select(candidates, -1).size() == 8,
                "negative limit shows every visible ordinary label");
        check(MinimapMarkerLabelSelection.select(candidates, 0).isEmpty(),
                "zero limit hides every ordinary label");

        List<MinimapMarkerLabelSelection.Candidate<String>> tied = List.of(
                candidate("z", "z-id", 1, true, true, false),
                candidate("a", "a-id", 1, true, true, false));
        check(MinimapMarkerLabelSelection.select(tied, 1).equals(List.of("a")),
                "equal distances have deterministic stable-id ordering");
        distanceLabels();
        distanceLabelColors();
        System.out.println("Minimap marker label selection tests passed (" + assertions + " assertions).");
    }

    private static void distanceLabels() {
        check(label(149.999, 150, true).equals("Home 149m"), "name remains below the cutoff");
        check(label(150, 150, true).equals("150m"), "exactly 150m shows only distance");
        check(label(151, 150, true).equals("151m"), "farther waypoints show only distance");
        check(label(5000, 150, true).equals("5000m"), "distant clamped icons get compact distance text");
        check(label(149, 150, false).equals("Home"), "near names survive disabling distances");
        check(label(150, 150, false).isEmpty(), "distance toggle leaves only the far icon");
        check(label(5000, 0, true).equals("Home 5000m"), "zero cutoff allows every name");
        check(label(100, 100, true).equals("100m"), "custom cutoffs use the same inclusive boundary");
        check(MinimapWaypointLabel.format("Home", "?", Double.NaN, 150, true).equals("Home ?"),
                "unknown distances keep identifying text");
        check(MinimapWaypointLabel.format("Home", "→ 150 m", 150, 150, true).equals("→ 150 m"),
                "highlighted destinations keep localized distance text without the far name");
        check(MinimapWaypointLabel.format("", "200m", 200, 0, true).equals("200m"),
                "empty names do not add leading spaces");
        check(MinimapWaypointLabel.format(null, "100m", 100, 150, true).equals("100m"),
                "older markers with no name still display their distance safely");
        List<MinimapMarkerLabelSelection.Candidate<String>> far = List.of(
                candidate("a", "a", 150, true, true, false),
                candidate("b", "b", 200, true, true, false),
                candidate("destination", "destination", 300, true, true, true));
        check(MinimapMarkerLabelSelection.select(far, 1).equals(List.of("a")),
                "far distance labels still honor the ordinary limit and emphasized exemption");
    }

    private static void distanceLabelColors() {
        check(!MinimapWaypointLabel.label("Home", "149m", 149, 150, true).distanceOnly(),
                "near names keep their normal text color");
        check(MinimapWaypointLabel.label("Home", "150m", 150, 150, true).distanceOnly(),
                "the inclusive distance-only boundary also controls color");
        check(!MinimapWaypointLabel.label("Home", "500m", 500, 0, true).distanceOnly(),
                "unlimited names do not activate distance-only coloring");
        check(!MinimapWaypointLabel.label("Home", "150m", 150, 150, false).distanceOnly(),
                "hidden distance text does not recolor icon-only markers");
        check(!MinimapWaypointLabel.label("Home", "?", Double.NaN, 150, true).distanceOnly(),
                "unknown distances with names keep normal coloring");
        check(MinimapWaypointLabel.label(null, "100m", 100, 150, true).distanceOnly(),
                "anonymous distance labels use the readable color too");
        check(MinimapWaypointLabel.distanceColor(0xFF000000) == 0xFF808080,
                "black blended equally with white becomes visible gray");
        check(MinimapWaypointLabel.distanceColor(0xFFFFFFFF) == 0xFFFFFFFF,
                "white remains white");
        check(MinimapWaypointLabel.distanceColor(0xFFFF0000) == 0xFFFF8080,
                "red hue survives the white blend");
        check(MinimapWaypointLabel.distanceColor(0xFF0000FF) == 0xFF8080FF,
                "blue hue survives the white blend");
        check(MinimapWaypointLabel.distanceColor(0xFF808080) == 0xFFC0C0C0,
                "half-channel results round consistently");
        check(MinimapWaypointLabel.distanceColor(0x0012AB56) == 0xFF89D5AB,
                "all RGB channels blend independently and text is opaque");
        for (int color : new int[]{0x000000, 0x0000FF, 0x00FF00, 0xFF0000, 0x330066, 0x12AB56}) {
            int mixed = MinimapWaypointLabel.distanceColor(color);
            check((mixed >>> 16 & 0xFF) >= 128 && (mixed >>> 8 & 0xFF) >= 128
                            && (mixed & 0xFF) >= 128 && (mixed >>> 24) == 255,
                    "distance colors keep a bright opaque floor against black backing");
        }
    }

    private static String label(double distance, double cutoff, boolean showDistance) {
        return MinimapWaypointLabel.format("Home", (int) distance + "m", distance, cutoff, showDistance);
    }

    private static MinimapMarkerLabelSelection.Candidate<String> candidate(
            String value, String id, double distance, boolean visible, boolean eligible, boolean emphasized) {
        return new MinimapMarkerLabelSelection.Candidate<>(value, id, distance, visible, eligible, emphasized);
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
