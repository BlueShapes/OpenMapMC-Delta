// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.List;
import java.util.UUID;

public final class WaypointClusterOrderTest {
    private static int assertions;

    public static void main(String[] args) {
        Item nearA = item(4, 2, "Zulu");
        Item nearB = item(2, 5, "Zulu");
        Item nearC = item(3, 5, "beta");
        Item far = item(1, 20, "Alpha");
        List<Item> source = List.of(far, nearC, nearA, nearB);

        check(ids(sorted(source, WaypointClusterOrder.Order.DISTANCE_ASCENDING))
                        .equals(List.of(4L, 2L, 3L, 1L)),
                "default order puts nearest rows first, with stable UUID ties");
        check(ids(sorted(source, WaypointClusterOrder.Order.DISTANCE_DESCENDING))
                        .equals(List.of(1L, 2L, 3L, 4L)),
                "descending distance reverses distance only and preserves UUID tie order");
        check(ids(sorted(source, WaypointClusterOrder.Order.NAME))
                        .equals(List.of(1L, 3L, 2L, 4L)),
                "name mode compares raw names instead of rendered distance suffixes");
        check(ids(sorted(List.of(item(10, 1, "alpha"), item(9, 2, "ALPHA")), WaypointClusterOrder.Order.NAME))
                        .equals(List.of(9L, 10L)),
                "case-insensitive name ties use UUID order");
        check(source.equals(List.of(far, nearC, nearA, nearB)), "ordering leaves the cluster input unchanged");
        List<Item> invalid = List.of(item(8, Double.NaN, "Unknown"), item(7, 9, "Known"));
        check(ids(sorted(invalid, WaypointClusterOrder.Order.DISTANCE_DESCENDING)).equals(List.of(7L, 8L)),
                "unknown distances remain at the end in descending mode");
        System.out.println("Waypoint cluster order tests passed (" + assertions + " assertions).");
    }

    private static List<Item> sorted(List<Item> source, WaypointClusterOrder.Order order) {
        return WaypointClusterOrder.sorted(source, order, Item::distance, Item::name, Item::id);
    }

    private static List<Long> ids(List<Item> values) {
        return values.stream().map(item -> item.id().getLeastSignificantBits()).toList();
    }

    private static Item item(long id, double distance, String name) {
        return new Item(new UUID(0, id), distance, name, name + " (" + (int) distance + "m)");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private record Item(UUID id, double distance, String name, String renderedLabel) {}
}
