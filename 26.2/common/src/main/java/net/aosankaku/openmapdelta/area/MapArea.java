// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One named surface feature. Its outline, label anchor, hierarchy and storage scope are independent. */
public record MapArea(UUID id, String dimension, String name, Kind kind, Scope scope,
                      UUID ownerId, String ownerName, UUID parentId, long revision,
                      Geometry geometry, Point label, Style style) {
    public static final int MAX_NAME_CODE_POINTS = 128;
    public static final int MAX_OWNER_NAME_CODE_POINTS = 128;
    public static final double WORLD_LIMIT = 30_000_000.0;

    public MapArea {
        Objects.requireNonNull(id, "id");
        dimension = validateDimension(dimension);
        name = validateText(name, MAX_NAME_CODE_POINTS, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(ownerId, "ownerId");
        ownerName = validateText(ownerName, MAX_OWNER_NAME_CODE_POINTS, "ownerName");
        if (parentId != null && parentId.equals(id)) throw new IllegalArgumentException("An area cannot parent itself");
        if (revision < 0) throw new IllegalArgumentException("Area revision cannot be negative");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(style, "style");
    }

    public MapArea withGeometry(Geometry value) {
        return new MapArea(id, dimension, name, kind, scope, ownerId, ownerName, parentId, revision, value, label, style);
    }

    public MapArea withRevision(long value) {
        return new MapArea(id, dimension, name, kind, scope, ownerId, ownerName, parentId, value, geometry, label, style);
    }

    public MapArea withOwner(UUID value, String valueName) {
        return new MapArea(id, dimension, name, kind, scope, value, valueName, parentId, revision, geometry, label, style);
    }

    public MapArea withScope(Scope value) {
        return new MapArea(id, dimension, name, kind, value, ownerId, ownerName, parentId, revision, geometry, label, style);
    }

    public MapArea withParent(UUID value) {
        return new MapArea(id, dimension, name, kind, scope, ownerId, ownerName, value, revision, geometry, label, style);
    }

    public MapArea withName(String value) {
        return new MapArea(id, dimension, value, kind, scope, ownerId, ownerName, parentId, revision, geometry, label, style);
    }

    public MapArea withLabel(Point value) {
        return new MapArea(id, dimension, name, kind, scope, ownerId, ownerName, parentId, revision, geometry, value, style);
    }

    public MapArea withStyle(Style value) {
        return new MapArea(id, dimension, name, kind, scope, ownerId, ownerName, parentId, revision, geometry, label, value);
    }

    public static String validateDimension(String dimension) {
        if (dimension == null || dimension.length() > 256) throw new IllegalArgumentException("Invalid area dimension");
        int separator = dimension.indexOf(':');
        if (separator < 1 || separator != dimension.lastIndexOf(':') || separator == dimension.length() - 1) {
            throw new IllegalArgumentException("Invalid area dimension");
        }
        if (!dimension.substring(0, separator).matches("[a-z0-9_.-]{1,64}")
                || !dimension.substring(separator + 1).matches("[a-z0-9/._-]{1,191}")) {
            throw new IllegalArgumentException("Invalid area dimension");
        }
        return dimension;
    }

    private static String validateText(String text, int maximumCodePoints, String field) {
        if (text == null) throw new IllegalArgumentException("Area " + field + " is required");
        String trimmed = text.strip();
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count == 0 || count > maximumCodePoints) throw new IllegalArgumentException("Invalid area " + field + " length");
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= trimmed.length() || !Character.isLowSurrogate(trimmed.charAt(index + 1))) {
                    throw new IllegalArgumentException("Unpaired surrogate in area " + field);
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Unpaired surrogate in area " + field);
            }
        }
        if (trimmed.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid control character in area " + field);
        return trimmed;
    }

    public enum Kind { POND, RIVER, SEA, LAND, COUNTRY, REGION, OTHER }
    public enum Scope { PERSONAL, SHARED }
    public enum Shape { LABEL, LINE, POLYGON }

    public record Point(double x, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > WORLD_LIMIT || Math.abs(z) > WORLD_LIMIT) {
                throw new IllegalArgumentException("Area point is outside supported world coordinates");
            }
        }
    }

    /** Each ring is open: the edge from its final point back to its first point is implied. */
    public record Polygon(List<Point> outer, List<List<Point>> holes) {
        public Polygon {
            outer = immutableRing(outer, "outer ring");
            holes = immutableRings(holes, "hole");
            validatePolygon(outer, holes);
        }

        private static List<Point> immutableRing(List<Point> points, String name) {
            Objects.requireNonNull(points, name);
            if (points.size() > AreaDocument.MAX_POINTS_PER_AREA) throw new IllegalArgumentException("Too many points in area " + name);
            ArrayList<Point> ring = new ArrayList<>(points.size());
            for (Point point : points) ring.add(Objects.requireNonNull(point, name + " point"));
            if (ring.size() > 1 && ring.get(0).equals(ring.get(ring.size() - 1))) ring.remove(ring.size() - 1);
            if (ring.size() < 3) throw new IllegalArgumentException("Area " + name + " needs at least three distinct points");
            for (int index = 0; index < ring.size(); index++) {
                if (ring.get(index).equals(ring.get((index + 1) % ring.size()))) throw new IllegalArgumentException("Duplicate adjacent area points");
            }
            if (Math.abs(signedRingArea(ring)) <= AreaGeometryOps.GEOMETRY_EPSILON) throw new IllegalArgumentException("Area ring has no surface");
            if (selfIntersects(ring)) throw new IllegalArgumentException("Area ring intersects itself");
            return List.copyOf(ring);
        }

        private static List<List<Point>> immutableRings(List<List<Point>> values, String name) {
            Objects.requireNonNull(values, "holes");
            if (values.size() > AreaDocument.MAX_PARTS) throw new IllegalArgumentException("Too many area " + name + "s");
            ArrayList<List<Point>> result = new ArrayList<>(values.size());
            for (List<Point> ring : values) result.add(immutableRing(ring, name));
            return List.copyOf(result);
        }
    }

    public record Geometry(Shape shape, List<List<Point>> paths, List<Polygon> polygons) {
        public Geometry {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(paths, "paths");
            Objects.requireNonNull(polygons, "polygons");
            if (paths.size() > AreaDocument.MAX_PARTS || polygons.size() > AreaDocument.MAX_PARTS) {
                throw new IllegalArgumentException("Too many area parts");
            }
            ArrayList<List<Point>> copiedPaths = new ArrayList<>(paths.size());
            for (List<Point> path : paths) {
                Objects.requireNonNull(path, "path");
                if (path.size() < 2 || path.size() > AreaDocument.MAX_POINTS_PER_AREA) throw new IllegalArgumentException("Invalid area path point count");
                ArrayList<Point> copied = new ArrayList<>(path.size());
                for (Point point : path) copied.add(Objects.requireNonNull(point, "path point"));
                for (int index = 1; index < copied.size(); index++) {
                    if (copied.get(index - 1).equals(copied.get(index))) throw new IllegalArgumentException("Duplicate adjacent area path points");
                }
                copiedPaths.add(List.copyOf(copied));
            }
            paths = List.copyOf(copiedPaths);
            ArrayList<Polygon> copiedPolygons = new ArrayList<>(polygons.size());
            for (Polygon polygon : polygons) copiedPolygons.add(Objects.requireNonNull(polygon, "polygon"));
            polygons = List.copyOf(copiedPolygons);
            switch (shape) {
                case LABEL -> {
                    if (!paths.isEmpty() || !polygons.isEmpty()) throw new IllegalArgumentException("A label-only area has no geometry");
                }
                case LINE -> {
                    if (paths.isEmpty() || !polygons.isEmpty()) throw new IllegalArgumentException("A line area needs paths only");
                }
                case POLYGON -> {
                    if (polygons.isEmpty() || !paths.isEmpty()) throw new IllegalArgumentException("A polygon area needs polygons only");
                }
            }
            if (pointCount(paths, polygons) > AreaDocument.MAX_POINTS_PER_AREA) throw new IllegalArgumentException("Area exceeds its total point limit");
        }

        public static Geometry label() { return new Geometry(Shape.LABEL, List.of(), List.of()); }
        public static Geometry line(List<List<Point>> paths) { return new Geometry(Shape.LINE, paths, List.of()); }
        public static Geometry polygon(List<Polygon> polygons) { return new Geometry(Shape.POLYGON, List.of(), polygons); }

        public int pointCount() { return pointCount(paths, polygons); }

        private static int pointCount(List<List<Point>> paths, List<Polygon> polygons) {
            int count = 0;
            for (List<Point> path : paths) count = Math.addExact(count, path.size());
            for (Polygon polygon : polygons) {
                count = Math.addExact(count, polygon.outer().size());
                for (List<Point> hole : polygon.holes()) count = Math.addExact(count, hole.size());
            }
            return count;
        }
    }

    public record Style(int color, boolean visible, boolean labels, boolean border, boolean fill,
                        double minBpp, double maxBpp, int priority) {
        public Style {
            if (!Double.isFinite(minBpp) || !Double.isFinite(maxBpp) || minBpp <= 0.0 || maxBpp < minBpp || maxBpp > 4096.0) {
                throw new IllegalArgumentException("Invalid area display zoom range");
            }
        }

        public static Style defaults(int color) { return new Style(color, true, true, true, false, 0.25, 4096.0, 0); }
    }

    private static void validatePolygon(List<Point> outer, List<List<Point>> holes) {
        for (int holeIndex = 0; holeIndex < holes.size(); holeIndex++) {
            List<Point> hole = holes.get(holeIndex);
            if (!pointInRing(outer, interiorSample(hole))) throw new IllegalArgumentException("Area hole is outside its outer ring");
            if (ringsIntersect(outer, hole)) throw new IllegalArgumentException("Area hole touches or crosses its outer ring");
            for (int otherIndex = 0; otherIndex < holeIndex; otherIndex++) {
                List<Point> other = holes.get(otherIndex);
                if (ringsIntersect(other, hole) || pointInRing(other, interiorSample(hole)) || pointInRing(hole, interiorSample(other))) {
                    throw new IllegalArgumentException("Area holes overlap");
                }
            }
        }
    }

    static double signedRingArea(List<Point> ring) {
        double twiceArea = 0;
        for (int index = 0; index < ring.size(); index++) {
            Point current = ring.get(index), next = ring.get((index + 1) % ring.size());
            twiceArea += current.x * next.z - next.x * current.z;
        }
        return twiceArea / 2.0;
    }

    static boolean selfIntersects(List<Point> ring) {
        for (int first = 0; first < ring.size(); first++) {
            Point a = ring.get(first), b = ring.get((first + 1) % ring.size());
            for (int second = first + 1; second < ring.size(); second++) {
                if (second == first || second == (first + 1) % ring.size() || (second + 1) % ring.size() == first) continue;
                if (segmentsIntersect(a, b, ring.get(second), ring.get((second + 1) % ring.size()))) return true;
            }
        }
        return false;
    }

    static boolean ringsIntersect(List<Point> first, List<Point> second) {
        for (int i = 0; i < first.size(); i++) {
            Point a = first.get(i), b = first.get((i + 1) % first.size());
            for (int j = 0; j < second.size(); j++) {
                if (segmentsIntersect(a, b, second.get(j), second.get((j + 1) % second.size()))) return true;
            }
        }
        return false;
    }

    static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        double abC = orientation(a, b, c), abD = orientation(a, b, d);
        double cdA = orientation(c, d, a), cdB = orientation(c, d, b);
        if (((abC > AreaGeometryOps.GEOMETRY_EPSILON && abD < -AreaGeometryOps.GEOMETRY_EPSILON)
                || (abC < -AreaGeometryOps.GEOMETRY_EPSILON && abD > AreaGeometryOps.GEOMETRY_EPSILON))
                && ((cdA > AreaGeometryOps.GEOMETRY_EPSILON && cdB < -AreaGeometryOps.GEOMETRY_EPSILON)
                || (cdA < -AreaGeometryOps.GEOMETRY_EPSILON && cdB > AreaGeometryOps.GEOMETRY_EPSILON))) return true;
        return Math.abs(abC) <= AreaGeometryOps.GEOMETRY_EPSILON && onSegment(a, b, c)
                || Math.abs(abD) <= AreaGeometryOps.GEOMETRY_EPSILON && onSegment(a, b, d)
                || Math.abs(cdA) <= AreaGeometryOps.GEOMETRY_EPSILON && onSegment(c, d, a)
                || Math.abs(cdB) <= AreaGeometryOps.GEOMETRY_EPSILON && onSegment(c, d, b);
    }

    static boolean pointInRing(List<Point> ring, Point point) {
        boolean inside = false;
        for (int index = 0, previous = ring.size() - 1; index < ring.size(); previous = index++) {
            Point currentPoint = ring.get(index), previousPoint = ring.get(previous);
            if (Math.abs(orientation(previousPoint, currentPoint, point)) <= AreaGeometryOps.GEOMETRY_EPSILON
                    && onSegment(previousPoint, currentPoint, point)) return true;
            if ((currentPoint.z > point.z) != (previousPoint.z > point.z)
                    && point.x < (previousPoint.x - currentPoint.x) * (point.z - currentPoint.z)
                    / (previousPoint.z - currentPoint.z) + currentPoint.x) inside = !inside;
        }
        return inside;
    }

    static Point interiorSample(List<Point> ring) {
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Point point : ring) { minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z); }
        for (int sampleIndex = 1; sampleIndex < 16; sampleIndex++) {
            double z = minZ + (maxZ - minZ) * sampleIndex / 16.0;
            ArrayList<Double> crosses = new ArrayList<>();
            for (int index = 0; index < ring.size(); index++) {
                Point current = ring.get(index), next = ring.get((index + 1) % ring.size());
                if ((current.z > z) != (next.z > z)) crosses.add(current.x + (next.x - current.x) * (z - current.z) / (next.z - current.z));
            }
            crosses.sort(Double::compare);
            for (int index = 1; index < crosses.size(); index += 2) {
                Point candidate = new Point((crosses.get(index - 1) + crosses.get(index)) / 2.0, z);
                if (pointInRing(ring, candidate)) return candidate;
            }
        }
        throw new IllegalArgumentException("Cannot find an interior point for area ring");
    }

    private static double orientation(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
    }

    private static boolean onSegment(Point a, Point b, Point point) {
        return point.x >= Math.min(a.x, b.x) - AreaGeometryOps.GEOMETRY_EPSILON
                && point.x <= Math.max(a.x, b.x) + AreaGeometryOps.GEOMETRY_EPSILON
                && point.z >= Math.min(a.z, b.z) - AreaGeometryOps.GEOMETRY_EPSILON
                && point.z <= Math.max(a.z, b.z) + AreaGeometryOps.GEOMETRY_EPSILON;
    }
}
