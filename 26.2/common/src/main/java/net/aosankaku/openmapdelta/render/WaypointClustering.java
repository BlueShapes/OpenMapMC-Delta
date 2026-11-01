// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.function.Function;

/** Camera-space grouping and label packing for world waypoint rendering. */
public final class WaypointClustering {
    private static final double ICON_RADIUS = 12.0;

    private WaypointClustering() {}

    /** A zero cutoff disables grouping by distance and keeps every waypoint individually visible. */
    public static boolean isWithinNameRange(double distance, double cutoff) {
        if (!Double.isFinite(distance) || distance < 0.0 || !Double.isFinite(cutoff) || cutoff < 0.0) return false;
        return cutoff == 0.0 || distance < cutoff;
    }

    /**
     * Builds far waypoint clusters and places their name stacks around fixed icon/badge bounds.
     * Near entries are always kept as individual stacks. Input order determines representatives.
     */
    public static <T> List<LayoutStack<T>> layout(List<Entry<T>> ordered,
                                                   double yawDegrees, double pitchDegrees,
                                                   double projectionDistance, int maxGroups,
                                                   int lineHeight, double labelGap,
                                                   IntUnaryOperator countTextWidth) {
        return layout(ordered, yawDegrees, pitchDegrees, projectionDistance, maxGroups,
                lineHeight, labelGap, countTextWidth, countTextWidth,
                named -> WaypointClusterLabels.select(named, entry -> false));
    }

    /** Builds clusters using a preselected label plan and the measured width of its summary row. */
    public static <T> List<LayoutStack<T>> layout(List<Entry<T>> ordered,
                                                   double yawDegrees, double pitchDegrees,
                                                   double projectionDistance, int maxGroups,
                                                   int lineHeight, double labelGap,
                                                   IntUnaryOperator countTextWidth,
                                                   IntUnaryOperator remainingTextWidth,
                                                   Function<List<Entry<T>>, WaypointClusterLabels.Selection<Entry<T>>> selectLabels) {
        if (maxGroups <= 0 || ordered.isEmpty()) return List.of();
        Objects.requireNonNull(countTextWidth, "countTextWidth");
        Objects.requireNonNull(remainingTextWidth, "remainingTextWidth");
        Objects.requireNonNull(selectLabels, "selectLabels");
        int safeLineHeight = Math.max(1, lineHeight);
        double safeGap = Double.isFinite(labelGap) ? Math.max(0.0, labelGap) : 0.0;
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
        List<MutableStack<T>> groups = new ArrayList<>();

        for (Entry<T> entry : ordered) {
            Footprint footprint = footprint(entry, sinYaw, cosYaw, sinPitch, cosPitch,
                    projectionDistance, safeLineHeight, safeGap);
            MutableStack<T> match = null;
            if (!entry.near() && footprint.point != null) {
                // Compare with each existing stack's fixed representative. This prevents chains
                // of barely touching labels from swallowing a large portion of the screen.
                for (MutableStack<T> group : groups) {
                    if (!group.near && group.anchor.point != null && group.anchor.overlaps(footprint)) {
                        match = group;
                        break;
                    }
                }
            }
            if (match != null) {
                match.entries.add(entry);
            } else if (groups.size() < maxGroups) {
                groups.add(new MutableStack<>(entry.near(), entry, footprint));
            }
        }

        List<LayoutStack<T>> result = new ArrayList<>(groups.size());
        for (MutableStack<T> group : groups) {
            boolean aggregated = !group.near && group.entries.size() > 1;
            WaypointRenderMath.ViewCandidate<T> anchor = aggregated
                    ? centeredAnchor(group.entries, sinYaw, cosYaw, sinPitch, cosPitch)
                    : group.entries.get(0).candidate();
            Footprint display = aggregated
                    ? projectedFootprint(anchor, sinYaw, cosYaw, sinPitch, cosPitch, projectionDistance)
                    : group.anchor;
            double badgeRadius = aggregated
                    ? WaypointClusterBadge.radius(Math.max(0, countTextWidth.applyAsInt(group.entries.size())), safeLineHeight)
                    : 0.0;
            boolean groupFocused = aggregated && group.entries.stream().anyMatch(entry -> entry.labelVisible()
                    && entry.textVisible()
                    && WaypointRenderMath.isLookingAt(entry.candidate().dx(), entry.candidate().dy(),
                    entry.candidate().dz(), yawDegrees, pitchDegrees,
                    WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES));
            List<Entry<T>> named = group.entries.stream().filter(Entry::textVisible)
                    .filter(entry -> groupFocused || entry.labelVisible()).toList();
            WaypointClusterLabels.Selection<Entry<T>> labelSelection = Objects.requireNonNull(
                    selectLabels.apply(named), "selectLabels result");
            result.add(new LayoutStack<>(List.copyOf(group.entries), group.near, aggregated,
                    anchor, display.point, display.factor, badgeRadius, named, labelSelection,
                    labelSelection.remaining() > 0
                            ? Math.max(0, remainingTextWidth.applyAsInt(labelSelection.remaining())) : 0));
        }

        // Name stacks may not cover another stack's icon or count badge. Packing moves text only;
        // each icon, beam, and badge stays at its individual or centered group anchor.
        List<Bounds> obstacles = new ArrayList<>(result.size());
        for (LayoutStack<T> stack : result) {
            if (stack.point == null || stack.members.stream().noneMatch(Entry::iconVisible)) {
                obstacles.add(null);
                continue;
            }
            double radius = (stack.aggregated ? stack.badgeRadius + 1.0 : ICON_RADIUS) + 2.0;
            obstacles.add(Bounds.around(stack.point.x, stack.point.y, radius * stack.factor));
        }

        List<Bounds> placedLabels = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            LayoutStack<T> stack = result.get(i);
            List<Entry<T>> named = stack.labelSelection.names();
            int rowCount = named.size() + (stack.labelSelection.remaining() > 0 ? 1 : 0);
            if (rowCount == 0 || stack.point == null || !(stack.factor > 0.0)) {
                stack.bounds = null;
                stack.verticalOffset = 0.0;
                continue;
            }

            double rowSpacing = safeLineHeight + safeGap;
            double baseCenterOffset = stack.aggregated
                    ? stack.badgeRadius + safeGap + safeLineHeight - 4.0
                    : labelAnchorOffset(named.get(0), safeLineHeight, safeGap);
            double maxWidth = Math.max(named.stream().mapToInt(Entry::textWidth).max().orElse(0),
                    stack.labelSelection.remaining() > 0 ? stack.remainingTextWidth : 0);
            double width = (Math.max(0, maxWidth) + 4.0) * stack.factor;
            double height = (rowCount * Math.max(safeLineHeight, 10)
                    + Math.max(0, rowCount - 1) * safeGap + 4.0) * stack.factor;
            double centerY = stack.point.y + (baseCenterOffset
                    + Math.max(0, rowCount - 1) * rowSpacing / 2.0) * stack.factor;
            double halfWidth = width / 2.0;
            double halfHeight = height / 2.0;

            double offset = 0.0;
            // Moving upward may meet each fixed icon and earlier label stack once.
            int attempts = obstacles.size() + placedLabels.size() + 1;
            for (int attempt = 0; attempt < attempts; attempt++) {
                Bounds current = new Bounds(stack.point.x - halfWidth, stack.point.x + halfWidth,
                        centerY + offset * stack.factor - halfHeight,
                        centerY + offset * stack.factor + halfHeight);
                double shift = 0.0;
                for (int obstacleIndex = 0; obstacleIndex < obstacles.size(); obstacleIndex++) {
                    Bounds obstacle = obstacles.get(obstacleIndex);
                    if (obstacle != null && obstacleIndex != i && current.overlaps(obstacle)) {
                        shift = Math.max(shift, obstacle.maxY - current.minY + 2.0 * stack.factor);
                    }
                }
                for (Bounds previous : placedLabels) {
                    if (current.overlaps(previous)) {
                        shift = Math.max(shift, previous.maxY - current.minY + 2.0 * stack.factor);
                    }
                }
                if (!(shift > 0.0)) {
                    stack.bounds = current;
                    break;
                }
                offset += shift / stack.factor;
            }
            if (stack.bounds == null) {
                double highestTop = obstacles.stream().filter(bounds -> bounds != null)
                        .mapToDouble(Bounds::maxY).max().orElse(stack.point.y);
                for (Bounds previous : placedLabels) highestTop = Math.max(highestTop, previous.maxY);
                offset = Math.max(offset, (highestTop + 2.0 * stack.factor
                        - (centerY - halfHeight)) / stack.factor);
                stack.bounds = new Bounds(stack.point.x - halfWidth, stack.point.x + halfWidth,
                        centerY + offset * stack.factor - halfHeight,
                        centerY + offset * stack.factor + halfHeight);
            }
            stack.verticalOffset = offset;
            if (stack.bounds != null) placedLabels.add(stack.bounds);
        }
        return List.copyOf(result);
    }

    private static Footprint footprint(Entry<?> entry, double sinYaw, double cosYaw,
                                       double sinPitch, double cosPitch,
                                       double projectionDistance, int lineHeight, double labelGap) {
        Footprint icon = projectedFootprint(entry.candidate(), sinYaw, cosYaw, sinPitch, cosPitch,
                projectionDistance);
        if (icon.point == null || !entry.textVisible()) return icon;
        Point point = icon.point;
        double factor = icon.factor;
        double halfWidth = (Math.max(0, entry.textWidth()) / 2.0 + 2.0) * factor;
        double halfHeight = (Math.max(lineHeight, 10) / 2.0 + 2.0) * factor;
        double textCenterY = point.y + labelAnchorOffset(entry, lineHeight, labelGap) * factor;
        Bounds text = new Bounds(point.x - halfWidth, point.x + halfWidth,
                textCenterY - halfHeight, textCenterY + halfHeight);
        return new Footprint(point, factor, text);
    }

    private static Footprint projectedFootprint(WaypointRenderMath.ViewCandidate<?> candidate,
                                                double sinYaw, double cosYaw,
                                                double sinPitch, double cosPitch,
                                                double projectionDistance) {
        double depth = cameraDepth(candidate, sinYaw, cosYaw, sinPitch, cosPitch);
        if (!(depth > 1.0e-6)) return new Footprint(null, 0.0, null);
        double x = (-candidate.dx() * cosYaw - candidate.dz() * sinYaw) / depth;
        double y = (-candidate.dx() * sinYaw * sinPitch + candidate.dy() * cosPitch
                + candidate.dz() * cosYaw * sinPitch) / depth;
        double factor = WaypointRenderMath.billboardScale(
                WaypointRenderMath.projectedDistance(candidate.distance(), projectionDistance)) / depth;
        double radius = ICON_RADIUS * factor;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(factor) || !(factor > 0.0)) {
            return new Footprint(null, 0.0, null);
        }
        Point point = new Point(x, y, radius);
        return new Footprint(point, factor, null);
    }

    private static double cameraDepth(WaypointRenderMath.ViewCandidate<?> candidate,
                                      double sinYaw, double cosYaw, double sinPitch, double cosPitch) {
        return -candidate.dx() * sinYaw * cosPitch - candidate.dy() * sinPitch
                + candidate.dz() * cosYaw * cosPitch;
    }

    /** Average screen positions, rather than letting a deeper or first member pull the badge. */
    private static <T> WaypointRenderMath.ViewCandidate<T> centeredAnchor(List<Entry<T>> members,
                                                                         double sinYaw, double cosYaw,
                                                                         double sinPitch, double cosPitch) {
        double x = 0.0, y = 0.0, z = 0.0, radius = 0.0, distance = 0.0;
        double weight = 1.0 / members.size();
        for (Entry<T> member : members) {
            WaypointRenderMath.ViewCandidate<T> candidate = member.candidate();
            // Only finite front-facing footprints can join a group, so depth is positive.
            double depth = cameraDepth(candidate, sinYaw, cosYaw, sinPitch, cosPitch);
            x += candidate.dx() / depth * weight;
            y += candidate.dy() / depth * weight;
            z += candidate.dz() / depth * weight;
            radius += Math.hypot(Math.hypot(candidate.dx(), candidate.dy()), candidate.dz()) * weight;
            distance += candidate.distance() * weight;
        }
        // Scaling the mean ray keeps its screen position and stays inside the members' render range.
        double scale = radius / Math.hypot(Math.hypot(x, y), z);
        return new WaypointRenderMath.ViewCandidate<>(members.get(0).value(),
                x * scale, y * scale, z * scale, distance);
    }

    private static double labelAnchorOffset(Entry<?> entry, int lineHeight, double labelGap) {
        if (Double.isFinite(entry.labelAnchorOffset()) && entry.labelAnchorOffset() >= 0.0) {
            return entry.labelAnchorOffset();
        }
        return 1.5 * lineHeight + labelGap - 4.0;
    }

    /** Camera-facing waypoint candidate plus measured text and visibility policy. */
    public record Entry<T>(WaypointRenderMath.ViewCandidate<T> candidate,
                           int textWidth, boolean near, boolean labelVisible, boolean iconVisible,
                           double labelAnchorOffset, boolean textVisible) {
        public T value() { return candidate.value(); }

        public Entry(WaypointRenderMath.ViewCandidate<T> candidate, int textWidth,
                     boolean near, boolean labelVisible, boolean iconVisible) {
            this(candidate, textWidth, near, labelVisible, iconVisible, Double.NaN, true);
        }
        public Entry(WaypointRenderMath.ViewCandidate<T> candidate, int textWidth,
                     boolean near, boolean labelVisible, boolean iconVisible, double labelAnchorOffset) {
            this(candidate, textWidth, near, labelVisible, iconVisible, labelAnchorOffset, true);
        }
    }

    /** One fixed icon anchor and its vertically packed name rows. */
    public static final class LayoutStack<T> {
        private final List<Entry<T>> members;
        private final boolean near;
        private final boolean aggregated;
        private final WaypointRenderMath.ViewCandidate<T> anchor;
        private final Point point;
        private final double factor;
        private final double badgeRadius;
        private final List<Entry<T>> namedEntries;
        private final WaypointClusterLabels.Selection<Entry<T>> labelSelection;
        private final int remainingTextWidth;
        private double verticalOffset;
        private Bounds bounds;

        private LayoutStack(List<Entry<T>> members, boolean near, boolean aggregated,
                            WaypointRenderMath.ViewCandidate<T> anchor,
                            Point point, double factor, double badgeRadius,
                            List<Entry<T>> namedEntries,
                            WaypointClusterLabels.Selection<Entry<T>> labelSelection,
                            int remainingTextWidth) {
            this.members = members;
            this.near = near;
            this.aggregated = aggregated;
            this.anchor = anchor;
            this.point = point;
            this.factor = factor;
            this.badgeRadius = badgeRadius;
            this.namedEntries = List.copyOf(namedEntries);
            this.labelSelection = labelSelection;
            this.remainingTextWidth = remainingTextWidth;
        }

        public WaypointRenderMath.ViewCandidate<T> representative() { return members.get(0).candidate(); }
        public WaypointRenderMath.ViewCandidate<T> anchor() { return anchor; }
        public List<Entry<T>> members() { return members; }
        public int count() { return members.size(); }
        public boolean near() { return near; }
        public boolean aggregated() { return aggregated; }
        public int beamColor(int markerColor) { return aggregated ? 0xFFFFFFFF : markerColor; }
        public double verticalOffset() { return verticalOffset; }
        public double badgeRadius() { return badgeRadius; }
        public Bounds labelBounds() { return bounds; }
        public WaypointClusterLabels.Selection<Entry<T>> labelSelection() { return labelSelection; }

        /** Grouping affects icons and counts; every name keeps its own gaze/highlight policy. */
        public List<Entry<T>> namedEntries() {
            return namedEntries;
        }
    }

    /** Screen-plane axis-aligned bounds, used by focused pure geometry tests. */
    public record Bounds(double minX, double maxX, double minY, double maxY) {
        public boolean overlaps(Bounds other) {
            return other != null && minX < other.maxX && maxX > other.minX
                    && minY < other.maxY && maxY > other.minY;
        }

        private static Bounds around(double x, double y, double radius) {
            return new Bounds(x - radius, x + radius, y - radius, y + radius);
        }
    }

    private record Point(double x, double y, double radius) {
        boolean overlaps(Point other) {
            double dx = x - other.x, dy = y - other.y, range = radius + other.radius;
            return Math.abs(dx) <= range && Math.abs(dy) <= range && dx * dx + dy * dy <= range * range;
        }

        boolean overlaps(Bounds bounds) {
            if (bounds == null) return false;
            double dx = Math.max(bounds.minX - x, Math.max(0.0, x - bounds.maxX));
            double dy = Math.max(bounds.minY - y, Math.max(0.0, y - bounds.maxY));
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private record Footprint(Point point, double factor, Bounds text) {
        boolean overlaps(Footprint other) {
            return point.overlaps(other.point) || point.overlaps(other.text)
                    || other.point.overlaps(text) || (text != null && text.overlaps(other.text));
        }
    }

    private static final class MutableStack<T> {
        final boolean near;
        final Footprint anchor;
        final List<Entry<T>> entries = new ArrayList<>();
        MutableStack(boolean near, Entry<T> first, Footprint anchor) {
            this.near = near;
            this.anchor = anchor;
            entries.add(first);
        }
    }
}
