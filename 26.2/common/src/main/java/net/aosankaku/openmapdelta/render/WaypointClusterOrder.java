// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Orders only the text rows in a rendered waypoint cluster. */
public final class WaypointClusterOrder {
    private static final Comparator<UUID> UUID_ORDER = Comparator.nullsFirst(Comparator.naturalOrder());

    private WaypointClusterOrder() {}

    public enum Order { DISTANCE_ASCENDING, DISTANCE_DESCENDING, NAME }

    public static <T> List<T> sorted(List<T> values, Order order,
                                    ToDoubleFunction<? super T> distance,
                                    Function<? super T, String> name,
                                    Function<? super T, UUID> id) {
        if (values == null || values.isEmpty()) return List.of();
        List<T> result = new ArrayList<>(values);
        Order selected = order == null ? Order.DISTANCE_ASCENDING : order;
        Comparator<T> comparator = switch (selected) {
            case DISTANCE_DESCENDING -> (left, right) -> {
                int compare = compareDistanceDescending(distance.applyAsDouble(left), distance.applyAsDouble(right));
                return compare != 0 ? compare : UUID_ORDER.compare(id.apply(left), id.apply(right));
            };
            case NAME -> Comparator
                    .comparing((T value) -> safeName(name.apply(value)), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(value -> id.apply(value), UUID_ORDER);
            case DISTANCE_ASCENDING -> (left, right) -> {
                int compare = compareDistance(distance.applyAsDouble(left), distance.applyAsDouble(right));
                return compare != 0 ? compare : UUID_ORDER.compare(id.apply(left), id.apply(right));
            };
        };
        result.sort(comparator);
        return List.copyOf(result);
    }

    private static int compareDistance(double left, double right) {
        boolean leftFinite = Double.isFinite(left);
        boolean rightFinite = Double.isFinite(right);
        if (leftFinite != rightFinite) return leftFinite ? -1 : 1;
        return leftFinite ? Double.compare(left, right) : 0;
    }

    private static int compareDistanceDescending(double left, double right) {
        boolean leftFinite = Double.isFinite(left);
        boolean rightFinite = Double.isFinite(right);
        if (leftFinite != rightFinite) return leftFinite ? -1 : 1;
        return leftFinite ? Double.compare(right, left) : 0;
    }

    private static String safeName(String name) { return name == null ? "" : name; }
}
