// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.List;

public final class WaypointClusterLabelsTest {
    private static int assertions;

    public static void main(String[] args) {
        boundariesAndOverflow();
        emphasizedSelectionPreservesSourceOrder();
        equalValuesRemainDistinctMembers();
        resultIsImmutable();
        System.out.println("Waypoint cluster label tests passed (" + assertions + " assertions).");
    }

    private static void boundariesAndOverflow() {
        for (int count : new int[]{0, 1, 5, 6, 20}) {
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < count; i++) values.add(i);
            var selection = WaypointClusterLabels.select(values, ignored -> false);
            int shown = Math.min(count, WaypointClusterLabels.MAX_NAMES);
            check(selection.names().size() == shown, count + " eligible labels select at most five names");
            check(selection.remaining() == count - shown, count + " eligible labels report exact overflow");
            check(selection.names().equals(values.subList(0, shown)), "default selection keeps source order");
        }
    }

    private static void emphasizedSelectionPreservesSourceOrder() {
        List<Integer> source = List.of(0, 1, 2, 3, 4, 5, 6, 7);
        var selection = WaypointClusterLabels.select(source, value -> value == 5 || value == 7);
        check(selection.names().equals(List.of(0, 1, 2, 5, 7)),
                "selection takes emphasized entries first, then returns chosen rows in source order");
        check(selection.remaining() == 3, "unselected eligible names are counted once");

        var exactlyFive = WaypointClusterLabels.select(List.of(0, 1, 2, 3, 4), value -> true);
        check(exactlyFive.names().equals(List.of(0, 1, 2, 3, 4)) && exactlyFive.remaining() == 0,
                "emphasis does not reorder the displayed five rows");
    }

    private static void equalValuesRemainDistinctMembers() {
        String duplicate = new String("same");
        List<String> source = List.of(duplicate, duplicate, "third", "fourth", "fifth", "sixth");
        var selection = WaypointClusterLabels.select(source, value -> value == duplicate);
        check(selection.names().size() == 5 && selection.names().get(0) == duplicate
                        && selection.names().get(1) == duplicate,
                "equal-valued members are selected independently by their positions");
        check(selection.remaining() == 1, "duplicate names still count as separate waypoints");
    }

    private static void resultIsImmutable() {
        var selection = WaypointClusterLabels.select(List.of("one"), ignored -> false);
        boolean immutable = false;
        try {
            selection.names().add("two");
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "selection names are immutable");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
