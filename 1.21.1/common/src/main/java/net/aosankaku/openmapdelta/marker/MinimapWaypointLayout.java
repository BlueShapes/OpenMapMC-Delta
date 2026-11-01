// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Camera-independent minimap icon grouping and anchored label placement. */
public final class MinimapWaypointLayout {
    private static final double CELL_SIZE = 32.0;

    private MinimapWaypointLayout() {}

    /** Near icons always remain independent; only overlapping distant icons are combined. */
    public static <T> List<Group<T>> group(List<Point<T>> ordered, double nameDistance) {
        List<MutableGroup<T>> groups = new ArrayList<>();
        Map<Long, List<MutableGroup<T>>> cells = new HashMap<>();
        double maximumRadius = ordered.stream().mapToDouble(Point::radius).max().orElse(0.0);
        for (Point<T> point : ordered) {
            MutableGroup<T> match = null;
            boolean distant = !MinimapWaypointLabel.nameVisible(point.distance(), nameDistance);
            if (distant) {
                double reach = point.radius() + maximumRadius;
                int minX = cell(point.x() - reach), maxX = cell(point.x() + reach);
                int minY = cell(point.y() - reach), maxY = cell(point.y() + reach);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (MutableGroup<T> group : cells.getOrDefault(key(x, y), List.of())) {
                            Point<T> first = group.members.get(0);
                            double dx = point.x() - first.x(), dy = point.y() - first.y();
                            double radii = point.radius() + first.radius();
                            if (dx * dx + dy * dy <= radii * radii
                                    && (match == null || group.order < match.order)) match = group;
                        }
                    }
                }
            }
            if (match != null) {
                match.members.add(point);
            } else {
                MutableGroup<T> group = new MutableGroup<>(groups.size(), point);
                groups.add(group);
                if (distant) cells.computeIfAbsent(key(cell(point.x()), cell(point.y())),
                        ignored -> new ArrayList<>()).add(group);
            }
        }
        return groups.stream().map(group -> new Group<>(List.copyOf(group.members))).toList();
    }

    /** Labels remain anchored to their own icon; foreground rendering handles icon overlaps. */
    public static <T> List<PlacedLabel<T>> placeLabels(List<Label<T>> ordered) {
        return ordered.stream().map(label -> new PlacedLabel<>(label, label.top())).toList();
    }

    private static int cell(double position) { return (int) Math.floor(position / CELL_SIZE); }
    private static long key(int x, int y) { return (long) x << 32 | y & 0xFFFFFFFFL; }

    public record Point<T>(T value, double x, double y, double radius, double distance) {}
    public record Group<T>(List<Point<T>> members) {
        public Point<T> representative() { return members.get(0); }
        public int count() { return members.size(); }
    }
    public record Label<T>(T value, double x, double top, double width, double height) {}
    public record PlacedLabel<T>(Label<T> label, double top) {}

    private static final class MutableGroup<T> {
        final int order;
        final List<Point<T>> members = new ArrayList<>();
        MutableGroup(int order, Point<T> first) { this.order = order; members.add(first); }
    }
}
