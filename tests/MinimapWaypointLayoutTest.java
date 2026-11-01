// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.List;

public final class MinimapWaypointLayoutTest {
    private static int assertions;

    public static void main(String[] args) {
        var mixed = MinimapWaypointLayout.group(List.of(
                point("near-a", 0, 0, 149), point("far-a", 0, 0, 150),
                point("near-b", 0, 0, 10), point("far-b", 1, 0, 10000)), 150);
        check(mixed.size() == 3, "nearby icons remain individual even when directly overlapping a far cluster");
        check(mixed.get(0).count() == 1 && mixed.get(2).count() == 1, "near members never become count badges");
        check(mixed.get(1).count() == 2, "all overlapping distant members are counted, including cutoff boundary");
        check(mixed.get(1).representative().value().equals("far-a"), "input priority determines cluster icon anchor");
        check(MinimapWaypointLayout.group(List.of(point("a", 0, 0, 10000), point("b", 0, 0, 10000)), 0).size() == 2,
                "zero cutoff disables aggregation with unlimited individual names");
        check(MinimapWaypointLayout.group(List.of(point("a", 0, 0, Double.NaN), point("b", 0, 0, 200)), 150).size() == 2,
                "unknown distances remain individual");
        check(MinimapWaypointLayout.group(List.of(point("a", -1, -1, 200), point("b", 1, 1, 300)), 150).size() == 1,
                "groups find overlaps across negative and positive spatial cell boundaries");
        check(MinimapWaypointLayout.group(List.of(point("a", 0, 0, 200), point("b", 30, 0, 200)), 150).size() == 2,
                "visibly separated distant icons retain their own positions");
        var chain = MinimapWaypointLayout.group(List.of(point("a", 0, 0, 200), point("b", 19, 0, 200),
                point("c", 38, 0, 200)), 150);
        check(chain.size() == 2, "an overlapping chain does not pull arbitrarily distant icons into one badge");
        var many = new ArrayList<MinimapWaypointLayout.Point<String>>();
        for (int i = 0; i < 1001; i++) many.add(point("p-" + i, 128, 0, 1000));
        var counted = MinimapWaypointLayout.group(many, 150);
        check(counted.size() == 1 && counted.get(0).count() == 1001, "dense crowds retain an exact count and every name");
        check(counted.get(0).members().get(1000).value().equals("p-1000"), "names retain stable vertical ordering");

        var labels = List.of(new MinimapWaypointLayout.Label<>("near-a", 0, 0, 90, 10),
                new MinimapWaypointLayout.Label<>("far-stack", 30, 0, 100, 40),
                new MinimapWaypointLayout.Label<>("near-b", -20, -20, 90, 10),
                new MinimapWaypointLayout.Label<>("separate", 300, 0, 90, 10));
        var placed = MinimapWaypointLayout.placeLabels(labels);
        check(placed.get(0).top() == 0 && placed.get(3).top() == 0, "non-colliding labels preserve ordinary placement");
        check(placed.get(1).top() == 0, "overlapping multiline names stay attached to their waypoint");
        for (int i = 0; i < placed.size(); i++) {
            check(placed.get(i).label().x() == labels.get(i).x(), "horizontal alignment stays at the waypoint");
            check(placed.get(i).top() == labels.get(i).top(), "crowded labels are never pushed away from their own point");
        }
        check(MinimapWaypointLayout.placeLabels(labels).equals(placed), "anchoring is deterministic across frames");
        var denseLabels = new ArrayList<MinimapWaypointLayout.Label<String>>();
        for (int i = 0; i < 1000; i++) denseLabels.add(new MinimapWaypointLayout.Label<>("name-"+i, 12, 79, 60, 8));
        check(MinimapWaypointLayout.placeLabels(denseLabels).stream().allMatch(label -> label.top() == 79),
                "even dense crowds cannot pull text to distant rows");
        System.out.println("Minimap waypoint layout tests passed (" + assertions + " assertions).");
    }

    private static MinimapWaypointLayout.Point<String> point(String name, double x, double y, double distance) {
        return new MinimapWaypointLayout.Point<>(name, x, y, 10, distance);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
