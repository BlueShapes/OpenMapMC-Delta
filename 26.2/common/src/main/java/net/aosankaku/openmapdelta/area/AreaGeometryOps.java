// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded, headless geometry operations for surface features and administrative borders. */
public final class AreaGeometryOps {
    public static final double GEOMETRY_EPSILON = 1.0e-8;
    private static final double CUT_BOUND = MapArea.WORLD_LIMIT;

    private AreaGeometryOps() {}

    public static boolean contains(MapArea.Geometry geometry, MapArea.Point point) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(point, "point");
        if (geometry.shape() != MapArea.Shape.POLYGON) return false;
        for (MapArea.Polygon polygon : geometry.polygons()) {
            if (!MapArea.pointInRing(polygon.outer(), point)) continue;
            if (polygon.holes().stream().noneMatch(hole -> MapArea.pointInRing(hole, point))) return true;
        }
        return false;
    }

    /** Picks a stable point on the longest path or a scan-line point inside a polygon. */
    public static MapArea.Point anchor(MapArea.Geometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (geometry.shape() == MapArea.Shape.POLYGON) return polygonAnchor(geometry);
        if (geometry.shape() == MapArea.Shape.LINE) {
            List<MapArea.Point> longest = geometry.paths().stream()
                    .max(Comparator.comparingDouble(AreaGeometryOps::pathLength)).orElseThrow();
            double total = pathLength(longest), target = total / 2.0;
            for (int index = 1; index < longest.size(); index++) {
                MapArea.Point from = longest.get(index - 1), to = longest.get(index);
                double segment = distance(from, to);
                if (target <= segment) {
                    double ratio = segment == 0.0 ? 0.0 : target / segment;
                    return pointLerp(from, to, ratio);
                }
                target -= segment;
            }
            return longest.get(longest.size() / 2);
        }
        throw new IllegalArgumentException("A label-only area needs its explicit label position");
    }

    /** Splits polygon geometry by an infinite line and conserves every disconnected piece and hole. */
    public static List<MapArea.Geometry> split(MapArea.Geometry geometry, MapArea.Point first, MapArea.Point second) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (geometry.shape() != MapArea.Shape.POLYGON) throw new IllegalArgumentException("Only areas can be split");
        if (distance(first, second) <= GEOMETRY_EPSILON) throw new IllegalArgumentException("Area split line needs two distinct points");
        Area original = toArea(geometry);
        Area firstSide = new Area(original);
        firstSide.intersect(halfPlane(first, second, false));
        Area secondSide = new Area(original);
        secondSide.intersect(halfPlane(first, second, true));
        if (firstSide.isEmpty() || secondSide.isEmpty()) throw new IllegalArgumentException("Area split line must divide the polygon into two sides");
        List<MapArea.Geometry> results = List.of(fromArea(firstSide), fromArea(secondSide));
        Area cover = toArea(results.get(0));
        cover.add(toArea(results.get(results.size() - 1)));
        Area difference = new Area(original);
        difference.exclusiveOr(cover);
        if (!difference.isEmpty()) throw new IllegalArgumentException("Area split did not conserve the source geometry");
        return results;
    }

    /** Combines polygons into one normalized polygon geometry. All sources must be polygons. */
    public static MapArea.Geometry union(List<MapArea.Geometry> geometries) {
        Objects.requireNonNull(geometries, "geometries");
        if (geometries.isEmpty() || geometries.size() > AreaDocument.MAX_PARTS) throw new IllegalArgumentException("Invalid polygon union input count");
        Area combined = new Area();
        for (MapArea.Geometry geometry : geometries) combined.add(toArea(Objects.requireNonNull(geometry, "geometry")));
        if (combined.isEmpty()) throw new IllegalArgumentException("Cannot union empty area geometry");
        return fromArea(combined);
    }

    /** Moves a vertex and any identical shared border vertices in sibling administrative regions. */
    public static AreaDocument movePoint(AreaDocument document, UUID areaId, MapArea.Point from, MapArea.Point to) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        MapArea selected = document.find(areaId).orElseThrow(() -> new IllegalArgumentException("Unknown area"));
        if (!hasVertex(selected.geometry(), from)) throw new IllegalArgumentException("Selected area has no vertex at that point");
        Map<UUID, MapArea> changed = new HashMap<>();
        for (MapArea current : document.areas()) {
            if (current.id().equals(selected.id())) {
                MapArea updated = current.withGeometry(replacePoint(current.geometry(), from, to));
                changed.put(updated.id(), updated);
            } else if (sharesAdministrativeBorder(selected, current, from)) {
                MapArea.Geometry geometry = hasVertex(current.geometry(), from)
                        ? current.geometry() : insertSharedBoundaryVertex(current.geometry(), selected.geometry(), from);
                MapArea updated = current.withGeometry(replacePoint(geometry, from, to));
                changed.put(updated.id(), updated);
            }
        }
        AreaDocument result = replaceAll(document, changed);
        validateEditedAdministrativeChildren(result, changed.keySet());
        return result;
    }

    /** Inserts a vertex along an edge and the corresponding edge of every sibling region. */
    public static AreaDocument insertPoint(AreaDocument document, UUID areaId, MapArea.Point first,
                                           MapArea.Point second, MapArea.Point inserted) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(inserted, "inserted");
        if (first.equals(second) || !strictlyOnSegment(first, second, inserted)) throw new IllegalArgumentException("Inserted area point must lie inside its border edge");
        MapArea selected = document.find(areaId).orElseThrow(() -> new IllegalArgumentException("Unknown area"));
        if (!hasEdge(selected.geometry(), first, second)) throw new IllegalArgumentException("Selected area has no edge at those points");
        Map<UUID, MapArea> changed = new HashMap<>();
        for (MapArea current : document.areas()) {
            if (current.id().equals(selected.id())) {
                changed.put(current.id(), current.withGeometry(insertPoint(current.geometry(), first, second, inserted)));
            } else if (sharesAdministrativeEdge(selected, current, first, second)) {
                MapArea.Geometry geometry = insertOnSharedEdge(current.geometry(), first, second, first);
                geometry = insertOnSharedEdge(geometry, first, second, second);
                geometry = insertOnSharedEdge(geometry, first, second, inserted);
                if (!geometry.equals(current.geometry())) changed.put(current.id(), current.withGeometry(geometry));
            }
        }
        AreaDocument result = replaceAll(document, changed);
        validateEditedAdministrativeChildren(result, changed.keySet());
        return result;
    }

    /** Retains a country/region and creates two child regions whose shapes exactly cover its area. */
    public static AreaDocument splitIntoChildren(AreaDocument document, UUID areaId, MapArea.Point first,
                                                 MapArea.Point second, String firstName, String secondName) {
        Objects.requireNonNull(document, "document");
        MapArea parent = document.find(areaId).orElseThrow(() -> new IllegalArgumentException("Unknown area"));
        if (!AreaDocument.isAdministrative(parent.kind()) || parent.geometry().shape() != MapArea.Shape.POLYGON) {
            throw new IllegalArgumentException("Only a polygon country or region can create child regions");
        }
        if (firstName == null || secondName == null || firstName.strip().equalsIgnoreCase(secondName.strip())) {
            throw new IllegalArgumentException("Child regions need distinct names");
        }
        List<MapArea.Geometry> halves = split(parent.geometry(), first, second);
        MapArea firstChild = new MapArea(UUID.randomUUID(), parent.dimension(), firstName, MapArea.Kind.REGION,
                parent.scope(), parent.ownerId(), parent.ownerName(), parent.id(), 0,
                halves.get(0), anchor(halves.get(0)), parent.style());
        MapArea secondChild = new MapArea(UUID.randomUUID(), parent.dimension(), secondName, MapArea.Kind.REGION,
                parent.scope(), parent.ownerId(), parent.ownerName(), parent.id(), 0,
                halves.get(halves.size() - 1), anchor(halves.get(halves.size() - 1)), parent.style());
        return document.upsert(firstChild).upsert(secondChild);
    }

    private static boolean sharesAdministrativeBorder(MapArea selected, MapArea other, MapArea.Point point) {
        return areAdministrativeSiblings(selected, other)
                && (hasVertex(other.geometry(), point) || hasMatchingEdgeInterior(selected.geometry(), other.geometry(), point));
    }

    private static boolean sharesAdministrativeEdge(MapArea selected, MapArea other, MapArea.Point first, MapArea.Point second) {
        return areAdministrativeSiblings(selected, other)
                && (hasEdge(other.geometry(), first, second) || hasOverlappingEdge(other.geometry(), first, second));
    }

    private static boolean areAdministrativeSiblings(MapArea first, MapArea second) {
        return first.parentId() != null && first.kind() == MapArea.Kind.REGION
                && second.kind() == MapArea.Kind.REGION && first.scope() == second.scope()
                && first.dimension().equals(second.dimension()) && Objects.equals(first.parentId(), second.parentId());
    }

    private static boolean hasMatchingEdgeInterior(MapArea.Geometry selected, MapArea.Geometry other, MapArea.Point point) {
        List<MapArea.Point> neighbors = neighborsAt(selected, point);
        if (neighbors.isEmpty()) return false;
        return forEachEdge(other, (first, second) -> strictlyOnSegment(first, second, point)
                && neighbors.stream().anyMatch(neighbor -> collinearAt(point, neighbor, first, second)));
    }

    private static MapArea.Geometry insertSharedBoundaryVertex(MapArea.Geometry geometry, MapArea.Geometry reference,
                                                               MapArea.Point point) {
        List<MapArea.Point> neighbors = neighborsAt(reference, point);
        if (neighbors.isEmpty()) return geometry;
        return insertOnMatchingEdges(geometry, neighbors, point);
    }

    private static MapArea.Geometry insertOnSharedEdge(MapArea.Geometry geometry, MapArea.Point referenceFirst,
                                                       MapArea.Point referenceSecond, MapArea.Point point) {
        if (point.equals(referenceFirst) || point.equals(referenceSecond)) {
            // Endpoints can still be interior points of a differently segmented sibling edge.
        } else if (!strictlyOnSegment(referenceFirst, referenceSecond, point)) {
            return geometry;
        }
        return insertOnMatchingEdges(geometry, List.of(referenceFirst, referenceSecond), point);
    }

    private static MapArea.Geometry insertOnMatchingEdges(MapArea.Geometry geometry, List<MapArea.Point> neighbors,
                                                          MapArea.Point point) {
        List<List<MapArea.Point>> paths = geometry.paths().stream()
                .map(path -> insertOnMatchingEdges(path, false, neighbors, point)).toList();
        List<MapArea.Polygon> polygons = geometry.polygons().stream().map(polygon -> new MapArea.Polygon(
                insertOnMatchingEdges(polygon.outer(), true, neighbors, point),
                polygon.holes().stream().map(hole -> insertOnMatchingEdges(hole, true, neighbors, point)).toList())).toList();
        if (paths.equals(geometry.paths()) && polygons.equals(geometry.polygons())) return geometry;
        return new MapArea.Geometry(geometry.shape(), paths, polygons);
    }

    private static List<MapArea.Point> insertOnMatchingEdges(List<MapArea.Point> path, boolean closed,
                                                             List<MapArea.Point> neighbors, MapArea.Point point) {
        int edgeCount = closed ? path.size() : path.size() - 1;
        ArrayList<MapArea.Point> changed = new ArrayList<>(path.size() + 1);
        boolean inserted = false;
        for (int index = 0; index < path.size(); index++) {
            MapArea.Point current = path.get(index);
            changed.add(current);
            if (index >= edgeCount) continue;
            MapArea.Point next = path.get((index + 1) % path.size());
            if (!inserted && strictlyOnSegment(current, next, point)
                    && neighbors.stream().anyMatch(neighbor -> collinearAt(point, neighbor, current, next))) {
                changed.add(point);
                inserted = true;
            }
        }
        return inserted ? List.copyOf(changed) : path;
    }

    private static List<MapArea.Point> neighborsAt(MapArea.Geometry geometry, MapArea.Point target) {
        ArrayList<MapArea.Point> result = new ArrayList<>();
        for (List<MapArea.Point> path : geometry.paths()) {
            for (int index = 0; index < path.size(); index++) {
                if (!path.get(index).equals(target)) continue;
                if (index > 0) result.add(path.get(index - 1));
                if (index + 1 < path.size()) result.add(path.get(index + 1));
            }
        }
        for (MapArea.Polygon polygon : geometry.polygons()) {
            addNeighbors(polygon.outer(), target, result);
            for (List<MapArea.Point> hole : polygon.holes()) addNeighbors(hole, target, result);
        }
        return result.stream().distinct().toList();
    }

    private static void addNeighbors(List<MapArea.Point> ring, MapArea.Point target, List<MapArea.Point> result) {
        for (int index = 0; index < ring.size(); index++) {
            if (!ring.get(index).equals(target)) continue;
            result.add(ring.get((index + ring.size() - 1) % ring.size()));
            result.add(ring.get((index + 1) % ring.size()));
        }
    }

    private static boolean collinearAt(MapArea.Point point, MapArea.Point neighbor,
                                       MapArea.Point edgeFirst, MapArea.Point edgeSecond) {
        double firstX = neighbor.x() - point.x(), firstZ = neighbor.z() - point.z();
        double secondX = edgeSecond.x() - edgeFirst.x(), secondZ = edgeSecond.z() - edgeFirst.z();
        double lengths = Math.hypot(firstX, firstZ) * Math.hypot(secondX, secondZ);
        return lengths > 0.0 && Math.abs(cross(firstX, firstZ, secondX, secondZ)) <= lengths * 1.0e-10;
    }

    private static boolean hasOverlappingEdge(MapArea.Geometry geometry, MapArea.Point first, MapArea.Point second) {
        return forEachEdge(geometry, (edgeFirst, edgeSecond) -> collinearSegmentsOverlap(first, second, edgeFirst, edgeSecond));
    }

    private static boolean forEachEdge(MapArea.Geometry geometry, EdgePredicate predicate) {
        for (List<MapArea.Point> path : geometry.paths()) {
            for (int index = 1; index < path.size(); index++) {
                if (predicate.test(path.get(index - 1), path.get(index))) return true;
            }
        }
        for (MapArea.Polygon polygon : geometry.polygons()) {
            if (forEachRingEdge(polygon.outer(), predicate)) return true;
            for (List<MapArea.Point> hole : polygon.holes()) if (forEachRingEdge(hole, predicate)) return true;
        }
        return false;
    }

    private static boolean forEachRingEdge(List<MapArea.Point> ring, EdgePredicate predicate) {
        for (int index = 0; index < ring.size(); index++) {
            if (predicate.test(ring.get(index), ring.get((index + 1) % ring.size()))) return true;
        }
        return false;
    }

    private static boolean collinearSegmentsOverlap(MapArea.Point first, MapArea.Point second,
                                                    MapArea.Point edgeFirst, MapArea.Point edgeSecond) {
        double dx = second.x() - first.x(), dz = second.z() - first.z();
        double length = Math.hypot(dx, dz);
        if (length <= GEOMETRY_EPSILON) return false;
        if (Math.abs(cross(dx, dz, edgeFirst.x() - first.x(), edgeFirst.z() - first.z())) / length > GEOMETRY_EPSILON
                || Math.abs(cross(dx, dz, edgeSecond.x() - first.x(), edgeSecond.z() - first.z())) / length > GEOMETRY_EPSILON) {
            return false;
        }
        double firstProjection = ((edgeFirst.x() - first.x()) * dx + (edgeFirst.z() - first.z()) * dz) / (length * length);
        double secondProjection = ((edgeSecond.x() - first.x()) * dx + (edgeSecond.z() - first.z()) * dz) / (length * length);
        double overlap = Math.min(1.0, Math.max(firstProjection, secondProjection))
                - Math.max(0.0, Math.min(firstProjection, secondProjection));
        return overlap * length > GEOMETRY_EPSILON;
    }

    private static AreaDocument replaceAll(AreaDocument document, Map<UUID, MapArea> replacements) {
        ArrayList<MapArea> changed = new ArrayList<>(document.areas().size());
        for (MapArea area : document.areas()) changed.add(replacements.getOrDefault(area.id(), area));
        return new AreaDocument(document.revision(), changed);
    }

    private static boolean hasVertex(MapArea.Geometry geometry, MapArea.Point target) {
        return geometry.paths().stream().anyMatch(path -> path.contains(target))
                || geometry.polygons().stream().anyMatch(polygon -> polygon.outer().contains(target)
                || polygon.holes().stream().anyMatch(hole -> hole.contains(target)));
    }

    private static boolean hasEdge(MapArea.Geometry geometry, MapArea.Point first, MapArea.Point second) {
        return geometry.paths().stream().anyMatch(path -> hasEdge(path, first, second))
                || geometry.polygons().stream().anyMatch(polygon -> hasEdge(polygon.outer(), first, second)
                || polygon.holes().stream().anyMatch(hole -> hasEdge(hole, first, second)));
    }

    private static boolean hasEdge(List<MapArea.Point> path, MapArea.Point first, MapArea.Point second) {
        for (int index = 0; index < path.size(); index++) {
            MapArea.Point current = path.get(index), next = path.get((index + 1) % path.size());
            if (current.equals(first) && next.equals(second) || current.equals(second) && next.equals(first)) return true;
        }
        return false;
    }

    private static MapArea.Geometry replacePoint(MapArea.Geometry geometry, MapArea.Point from, MapArea.Point to) {
        if (from.equals(to)) return geometry;
        List<List<MapArea.Point>> paths = geometry.paths().stream().map(path -> path.stream()
                .map(point -> point.equals(from) ? to : point).toList()).toList();
        List<MapArea.Polygon> polygons = geometry.polygons().stream().map(polygon -> new MapArea.Polygon(
                polygon.outer().stream().map(point -> point.equals(from) ? to : point).toList(),
                polygon.holes().stream().map(hole -> hole.stream().map(point -> point.equals(from) ? to : point).toList()).toList())).toList();
        return new MapArea.Geometry(geometry.shape(), paths, polygons);
    }

    private static MapArea.Geometry insertPoint(MapArea.Geometry geometry, MapArea.Point first,
                                                MapArea.Point second, MapArea.Point inserted) {
        List<List<MapArea.Point>> paths = geometry.paths().stream().map(path -> insertPoint(path, first, second, inserted)).toList();
        List<MapArea.Polygon> polygons = geometry.polygons().stream().map(polygon -> new MapArea.Polygon(
                insertPoint(polygon.outer(), first, second, inserted),
                polygon.holes().stream().map(hole -> insertPoint(hole, first, second, inserted)).toList())).toList();
        return new MapArea.Geometry(geometry.shape(), paths, polygons);
    }

    private static List<MapArea.Point> insertPoint(List<MapArea.Point> path, MapArea.Point first,
                                                   MapArea.Point second, MapArea.Point inserted) {
        ArrayList<MapArea.Point> changed = new ArrayList<>(path.size() + 1);
        for (int index = 0; index < path.size(); index++) {
            MapArea.Point current = path.get(index), next = path.get((index + 1) % path.size());
            changed.add(current);
            if (current.equals(first) && next.equals(second) || current.equals(second) && next.equals(first)) changed.add(inserted);
        }
        return List.copyOf(changed);
    }

    private static void validateEditedAdministrativeChildren(AreaDocument document, Iterable<UUID> changedIds) {
        for (UUID id : changedIds) {
            MapArea child = document.find(id).orElseThrow();
            if (child.kind() != MapArea.Kind.REGION || child.parentId() == null) continue;
            MapArea parent = document.find(child.parentId()).orElseThrow();
            if (parent.geometry().shape() == MapArea.Shape.POLYGON && child.geometry().shape() == MapArea.Shape.POLYGON
                    && !geometryInside(child.geometry(), parent.geometry())) {
                throw new IllegalArgumentException("Edited region crosses outside its parent");
            }
        }
    }

    private static boolean geometryInside(MapArea.Geometry child, MapArea.Geometry parent) {
        for (MapArea.Polygon polygon : child.polygons()) {
            if (!ringInside(polygon.outer(), parent)) return false;
            for (List<MapArea.Point> hole : polygon.holes()) if (!ringInside(hole, parent)) return false;
        }
        return true;
    }

    private static boolean ringInside(List<MapArea.Point> ring, MapArea.Geometry parent) {
        for (int index = 0; index < ring.size(); index++) {
            MapArea.Point first = ring.get(index), second = ring.get((index + 1) % ring.size());
            if (!contains(parent, first) || !contains(parent, second)) return false;
            ArrayList<Double> boundaries = new ArrayList<>();
            boundaries.add(0.0);
            boundaries.add(1.0);
            for (MapArea.Polygon polygon : parent.polygons()) {
                collectIntersectionFractions(first, second, polygon.outer(), boundaries);
                for (List<MapArea.Point> hole : polygon.holes()) collectIntersectionFractions(first, second, hole, boundaries);
            }
            boundaries.sort(Double::compare);
            for (int boundary = 1; boundary < boundaries.size(); boundary++) {
                double from = boundaries.get(boundary - 1), to = boundaries.get(boundary);
                if (to - from > GEOMETRY_EPSILON && !contains(parent, pointLerp(first, second, (from + to) / 2.0))) return false;
            }
        }
        return true;
    }

    private static void collectIntersectionFractions(MapArea.Point a, MapArea.Point b, List<MapArea.Point> boundary,
                                                     List<Double> fractions) {
        double rx = b.x() - a.x(), rz = b.z() - a.z();
        for (int index = 0; index < boundary.size(); index++) {
            MapArea.Point c = boundary.get(index), d = boundary.get((index + 1) % boundary.size());
            double sx = d.x() - c.x(), sz = d.z() - c.z();
            double denominator = cross(rx, rz, sx, sz);
            if (Math.abs(denominator) <= GEOMETRY_EPSILON) continue;
            double qx = c.x() - a.x(), qz = c.z() - a.z();
            double t = cross(qx, qz, sx, sz) / denominator;
            double u = cross(qx, qz, rx, rz) / denominator;
            if (t >= -GEOMETRY_EPSILON && t <= 1.0 + GEOMETRY_EPSILON
                    && u >= -GEOMETRY_EPSILON && u <= 1.0 + GEOMETRY_EPSILON) fractions.add(Math.max(0.0, Math.min(1.0, t)));
        }
    }

    private static boolean strictlyOnSegment(MapArea.Point first, MapArea.Point second, MapArea.Point inserted) {
        double dx = second.x() - first.x(), dz = second.z() - first.z();
        double length = Math.hypot(dx, dz);
        if (length <= GEOMETRY_EPSILON) return false;
        double px = inserted.x() - first.x(), pz = inserted.z() - first.z();
        if (Math.abs(cross(dx, dz, px, pz)) / length > GEOMETRY_EPSILON) return false;
        double projection = (px * dx + pz * dz) / (length * length);
        double endpointMargin = GEOMETRY_EPSILON / length;
        return projection > endpointMargin && projection < 1.0 - endpointMargin;
    }

    private static Area toArea(MapArea.Geometry geometry) {
        if (geometry.shape() != MapArea.Shape.POLYGON) throw new IllegalArgumentException("Only polygon geometry supports area operations");
        Area result = new Area();
        for (MapArea.Polygon polygon : geometry.polygons()) {
            Area component = new Area(path(polygon.outer(), true));
            for (List<MapArea.Point> hole : polygon.holes()) component.subtract(new Area(path(hole, true)));
            result.add(component);
        }
        return result;
    }

    private static Area halfPlane(MapArea.Point a, MapArea.Point b, boolean positive) {
        List<MapArea.Point> bounds = List.of(point(-CUT_BOUND, -CUT_BOUND), point(CUT_BOUND, -CUT_BOUND),
                point(CUT_BOUND, CUT_BOUND), point(-CUT_BOUND, CUT_BOUND));
        ArrayList<MapArea.Point> clipped = new ArrayList<>();
        MapArea.Point previous = bounds.get(bounds.size() - 1);
        double previousSide = side(a, b, previous) * (positive ? 1.0 : -1.0);
        for (MapArea.Point current : bounds) {
            double currentSide = side(a, b, current) * (positive ? 1.0 : -1.0);
            boolean previousInside = previousSide >= -GEOMETRY_EPSILON;
            boolean currentInside = currentSide >= -GEOMETRY_EPSILON;
            if (previousInside != currentInside) {
                double fraction = previousSide / (previousSide - currentSide);
                clipped.add(pointLerp(previous, current, fraction));
            }
            if (currentInside) clipped.add(current);
            previous = current;
            previousSide = currentSide;
        }
        if (clipped.size() < 3) throw new IllegalArgumentException("Cannot construct area split line");
        return new Area(path(clipped, true));
    }

    private static MapArea.Geometry fromArea(Area area) {
        RingCollector ringCollector = new RingCollector();
        double[] coordinates = new double[6];
        ArrayList<MapArea.Point> active = null;
        PathIterator iterator = area.getPathIterator(null, 0.05);
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coordinates);
            if (type == PathIterator.SEG_MOVETO) {
                ringCollector.finish(active);
                active = new ArrayList<>();
                active.add(point(coordinates[0], coordinates[1]));
            } else if (type == PathIterator.SEG_LINETO) {
                if (active == null) throw new IllegalArgumentException("Invalid polygon path");
                MapArea.Point next = point(coordinates[0], coordinates[1]);
                if (!active.get(active.size() - 1).equals(next)) active.add(next);
            } else if (type == PathIterator.SEG_CLOSE) {
                ringCollector.finish(active);
                active = null;
            } else throw new IllegalArgumentException("Expected linear polygon geometry");
            iterator.next();
        }
        ringCollector.finish(active);
        List<List<MapArea.Point>> rings = ringCollector.rings;
        if (rings.isEmpty()) throw new IllegalArgumentException("Area operation produced an empty polygon");

        ArrayList<RingNode> nodes = new ArrayList<>(rings.size());
        for (List<MapArea.Point> ring : rings) nodes.add(new RingNode(ring, Math.abs(MapArea.signedRingArea(ring)), MapArea.interiorSample(ring)));
        for (int index = 0; index < nodes.size(); index++) {
            RingNode node = nodes.get(index);
            RingNode smallestParent = null;
            for (int otherIndex = 0; otherIndex < nodes.size(); otherIndex++) {
                if (index == otherIndex) continue;
                RingNode candidate = nodes.get(otherIndex);
                if (candidate.area > node.area && (smallestParent == null || candidate.area < smallestParent.area)
                        && MapArea.pointInRing(candidate.ring, node.interior)) smallestParent = candidate;
            }
            node.parent = smallestParent;
        }
        for (RingNode node : nodes) node.depth = depth(node);
        ArrayList<MapArea.Polygon> polygons = new ArrayList<>();
        Map<RingNode, Integer> outerIndex = new HashMap<>();
        for (RingNode node : nodes) {
            if ((node.depth & 1) == 0) {
                if (polygons.size() >= AreaDocument.MAX_PARTS) throw new IllegalArgumentException("Area result has too many disconnected parts");
                outerIndex.put(node, polygons.size());
                polygons.add(new MapArea.Polygon(node.ring, List.of()));
            }
        }
        for (RingNode node : nodes) {
            if ((node.depth & 1) == 0) continue;
            RingNode outer = node.parent;
            while (outer != null && (outer.depth & 1) != 0) outer = outer.parent;
            Integer index = outer == null ? null : outerIndex.get(outer);
            if (index == null) throw new IllegalArgumentException("Polygon hole has no containing outer ring");
            MapArea.Polygon polygon = polygons.get(index);
            ArrayList<List<MapArea.Point>> holes = new ArrayList<>(polygon.holes());
            holes.add(node.ring);
            polygons.set(index, new MapArea.Polygon(polygon.outer(), holes));
        }
        return MapArea.Geometry.polygon(polygons);
    }

    private static int depth(RingNode node) {
        int depth = 0;
        for (RingNode parent = node.parent; parent != null; parent = parent.parent) depth++;
        return depth;
    }

    private static final class RingCollector {
        private final List<List<MapArea.Point>> rings = new ArrayList<>();
        private int pointCount;

        private void finish(List<MapArea.Point> active) {
            if (active == null) return;
            if (active.size() > 1 && active.get(0).equals(active.get(active.size() - 1))) active.remove(active.size() - 1);
            if (active.size() < 3 || Math.abs(MapArea.signedRingArea(active)) <= GEOMETRY_EPSILON) return;
            int nextPointCount = Math.addExact(pointCount, active.size());
            if (nextPointCount > AreaDocument.MAX_POINTS_PER_AREA) {
                throw new IllegalArgumentException("Area operation exceeds its point limit");
            }
            rings.add(List.copyOf(active));
            pointCount = nextPointCount;
        }
    }

    private static Path2D.Double path(List<MapArea.Point> points, boolean closed) {
        if (points.size() < (closed ? 3 : 2)) throw new IllegalArgumentException("Too few polygon path points");
        Path2D.Double result = new Path2D.Double(Path2D.WIND_NON_ZERO, points.size());
        result.moveTo(points.get(0).x(), points.get(0).z());
        for (int index = 1; index < points.size(); index++) result.lineTo(points.get(index).x(), points.get(index).z());
        if (closed) result.closePath();
        return result;
    }

    private static MapArea.Point polygonAnchor(MapArea.Geometry geometry) {
        MapArea.Point best = null;
        double bestScore = -1.0;
        for (MapArea.Polygon polygon : geometry.polygons()) {
            ArrayList<Double> zLevels = new ArrayList<>();
            addZLevels(polygon.outer(), zLevels);
            for (List<MapArea.Point> hole : polygon.holes()) addZLevels(hole, zLevels);
            zLevels.sort(Double::compare);
            for (int level = 1; level < zLevels.size(); level++) {
                double lower = zLevels.get(level - 1), upper = zLevels.get(level);
                if (upper <= lower) continue;
                double z = interiorMidpoint(lower, upper);
                if (!Double.isFinite(z)) continue;
                ArrayList<Double> crosses = new ArrayList<>(crossings(polygon.outer(), z));
                for (List<MapArea.Point> hole : polygon.holes()) crosses.addAll(crossings(hole, z));
                crosses.sort(Double::compare);
                for (int index = 1; index < crosses.size(); index += 2) {
                    double left = crosses.get(index - 1), right = crosses.get(index);
                    if (right <= left || right - left <= bestScore) continue;
                    double x = interiorMidpoint(left, right);
                    if (!Double.isFinite(x)) continue;
                    MapArea.Point candidate = point(x, z);
                    if (polygonContains(polygon, candidate)) { best = candidate; bestScore = right - left; }
                }
            }
        }
        if (best == null) throw new IllegalArgumentException("Cannot find a label anchor inside polygon");
        return best;
    }

    private static void addZLevels(List<MapArea.Point> ring, List<Double> levels) {
        for (MapArea.Point point : ring) levels.add(point.z());
    }

    private static double interiorMidpoint(double lower, double upper) {
        double midpoint = lower + (upper - lower) / 2.0;
        if (!(midpoint > lower && midpoint < upper)) midpoint = Math.nextUp(lower);
        return midpoint > lower && midpoint < upper ? midpoint : Double.NaN;
    }

    private static boolean polygonContains(MapArea.Polygon polygon, MapArea.Point point) {
        return MapArea.pointInRing(polygon.outer(), point)
                && polygon.holes().stream().noneMatch(hole -> MapArea.pointInRing(hole, point));
    }

    private static List<Double> crossings(List<MapArea.Point> ring, double z) {
        ArrayList<Double> crosses = new ArrayList<>();
        for (int index = 0; index < ring.size(); index++) {
            MapArea.Point first = ring.get(index), second = ring.get((index + 1) % ring.size());
            if ((first.z() > z) != (second.z() > z)) {
                crosses.add(first.x() + (second.x() - first.x()) * (z - first.z()) / (second.z() - first.z()));
            }
        }
        crosses.sort(Double::compare);
        return crosses;
    }

    private static double pathLength(List<MapArea.Point> path) {
        double length = 0.0;
        for (int index = 1; index < path.size(); index++) length += distance(path.get(index - 1), path.get(index));
        return length;
    }

    private static MapArea.Point pointLerp(MapArea.Point from, MapArea.Point to, double fraction) {
        return point(from.x() + (to.x() - from.x()) * fraction, from.z() + (to.z() - from.z()) * fraction);
    }

    private static double distance(MapArea.Point a, MapArea.Point b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private static double side(MapArea.Point a, MapArea.Point b, MapArea.Point point) {
        return cross(b.x() - a.x(), b.z() - a.z(), point.x() - a.x(), point.z() - a.z());
    }

    private static double cross(double ax, double az, double bx, double bz) { return ax * bz - az * bx; }
    private static MapArea.Point point(double x, double z) { return new MapArea.Point(x, z); }

    @FunctionalInterface
    private interface EdgePredicate {
        boolean test(MapArea.Point first, MapArea.Point second);
    }

    private static final class RingNode {
        private final List<MapArea.Point> ring;
        private final double area;
        private final MapArea.Point interior;
        private RingNode parent;
        private int depth;

        private RingNode(List<MapArea.Point> ring, double area, MapArea.Point interior) {
            this.ring = ring;
            this.area = area;
            this.interior = interior;
        }
    }
}
