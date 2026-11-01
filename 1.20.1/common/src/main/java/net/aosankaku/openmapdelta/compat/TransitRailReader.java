// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads only client-synchronized rail geometry through the public APIs of optional transit mods. */
public final class TransitRailReader {
    public static final int MAX_RAILS = 16_384;
    public static final int MAX_POINTS = 262_144;
    public static final int MAX_CANDIDATES = 65_536;
    public static final int MAX_SEGMENTS_PER_RAIL = 128;
    private static final double BLOCKS_PER_SAMPLE = 4.0;

    @FunctionalInterface
    public interface PositionReader {
        TransitRail.Point read(Object position) throws ReflectiveOperationException;
    }

    @FunctionalInterface
    public interface EndpointReader {
        TransitRail.Point read(Object endpoint) throws ReflectiveOperationException;
    }

    /** Cached geometry is reused only while the owning upstream geometry object is identical. */
    public record CachedGeometry(Object geometryIdentity, List<TransitRail.Point> points) {
        public CachedGeometry {
            Objects.requireNonNull(geometryIdentity, "geometryIdentity");
            points = List.copyOf(points);
            if (points.size() < 2 || points.size() > MAX_SEGMENTS_PER_RAIL + 1
                    || points.stream().anyMatch(point -> point == null || !point.isFinite())) {
                throw new IllegalArgumentException("Invalid cached rail geometry");
            }
        }
    }

    public record MtrSnapshot(List<TransitRail> rails, Map<String, CachedGeometry> geometryCache) {
        public MtrSnapshot {
            rails = List.copyOf(rails);
            geometryCache = Map.copyOf(geometryCache);
        }
    }

    private TransitRailReader() {}

    public static List<TransitRail> readCreate(Object trackNetworks, Object dimension, PositionReader positions)
            throws ReflectiveOperationException {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(positions, "positions");
        List<TransitRail> rails = new ArrayList<>();
        int[] pointCount = {0};
        int[] candidates = {0};
        int[] nodesVisited = {0};
        int[] graphsVisited = {0};
        if (trackNetworks == null) return List.of();
        for (Object graph : collectionValues(trackNetworks)) {
            if (++graphsVisited[0] > MAX_CANDIDATES || candidates[0] >= MAX_CANDIDATES
                    || rails.size() >= MAX_RAILS || pointCount[0] + 2 > MAX_POINTS) break;
            Object rawNodes = TransitStationReader.call(graph, "getNodes");
            if (!(rawNodes instanceof Collection<?> nodes)) throw new NoSuchFieldException("Create graph nodes");
            Set<Long> seenConnections = new HashSet<>();
            for (Object nodeLocationKey : nodes) {
                if (++nodesVisited[0] > MAX_CANDIDATES || candidates[0] >= MAX_CANDIDATES
                        || rails.size() >= MAX_RAILS || pointCount[0] + 2 > MAX_POINTS) break;
                Object node = TransitStationReader.call(graph, "locateNode", nodeLocationKey);
                if (node == null) continue;
                Object nodeLocation = TransitStationReader.call(node, "getLocation");
                Object nodeDimension = TransitStationReader.call(nodeLocation, "getDimension");
                if (!dimension.equals(nodeDimension)) continue;
                Object rawConnections = TransitStationReader.call(graph, "getConnectionsFrom", node);
                if (!(rawConnections instanceof Map<?, ?> connections)) throw new NoSuchFieldException("Create graph connections");
                Object rawFirstId = TransitStationReader.call(node, "getNetId");
                if (!(rawFirstId instanceof Number firstNumber)) throw new NoSuchFieldException("Create node network ID");
                int firstId = firstNumber.intValue();
                for (Map.Entry<?, ?> connection : connections.entrySet()) {
                    if (++candidates[0] > MAX_CANDIDATES || rails.size() >= MAX_RAILS
                            || pointCount[0] + 2 > MAX_POINTS) break;
                    Object otherNode = connection.getKey();
                    Object edge = connection.getValue();
                    if (otherNode == null || edge == null) continue;
                    if (Boolean.TRUE.equals(TransitStationReader.call(edge, "isInterDimensional"))) continue;
                    Object otherLocation = TransitStationReader.call(otherNode, "getLocation");
                    Object otherDimension = TransitStationReader.call(otherLocation, "getDimension");
                    if (!dimension.equals(otherDimension)) continue;
                    Object rawOtherId = TransitStationReader.call(otherNode, "getNetId");
                    if (!(rawOtherId instanceof Number otherNumber)) throw new NoSuchFieldException("Create node network ID");
                    long pair = pairKey(firstId, otherNumber.intValue());
                    if (firstId == otherNumber.intValue() || seenConnections.contains(pair)) continue;

                    try {
                        List<TransitRail.Point> path = createPath(graph, edge, nodeLocation, otherLocation, positions);
                        if (path == null || pointCount[0] + path.size() > MAX_POINTS) continue;
                        rails.add(new TransitRail(TransitRail.Source.CREATE, dimension, path));
                        pointCount[0] += path.size();
                        seenConnections.add(pair);
                    } catch (InvalidRailDataException ignored) {
                        // A broken or non-finite edge should not hide the remaining synchronized network.
                    }
                }
            }
        }
        return List.copyOf(rails);
    }

    public static MtrSnapshot readMtr4(Object clientData, Object dimension, PositionReader positions,
                                       Map<String, CachedGeometry> previousGeometry)
            throws ReflectiveOperationException {
        Object rawRails = TransitStationReader.field(clientData, "rails");
        if (!(rawRails instanceof Collection<?> railCollection)) throw new NoSuchFieldException("MTR 4 rails");
        List<TransitRail> rails = new ArrayList<>();
        Map<String, CachedGeometry> currentGeometry = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int pointCount = 0;
        int candidates = 0;
        for (Object rail : railCollection) {
            if (++candidates > MAX_CANDIDATES || rails.size() >= MAX_RAILS || pointCount + 2 > MAX_POINTS) break;
            if (rail == null || !isTrain(TransitStationReader.call(rail, "getTransportMode"))
                    || !Boolean.TRUE.equals(TransitStationReader.call(rail, "isValid"))) continue;
            Object rawId = TransitStationReader.call(rail, "getHexId");
            Object railMath = TransitStationReader.field(rail, "railMath");
            if (rawId == null || railMath == null) continue;
            String key = "mtr4:" + rawId;
            if (!seen.add(key)) continue;
            CachedGeometry cached = previousGeometry == null ? null : previousGeometry.get(key);
            List<TransitRail.Point> path;
            if (cached != null && cached.geometryIdentity() == railMath) {
                path = cached.points();
            } else {
                try {
                    double length = number(TransitStationReader.call(railMath, "getLength"));
                    path = sample(length, fraction -> positions.read(
                            TransitStationReader.call(railMath, "getPosition", fraction, false)));
                } catch (InvalidRailDataException ignored) {
                    continue;
                }
            }
            if (path == null || pointCount + path.size() > MAX_POINTS) continue;
            rails.add(new TransitRail(TransitRail.Source.MTR, dimension, path));
            currentGeometry.put(key, new CachedGeometry(railMath, path));
            pointCount += path.size();
        }
        return new MtrSnapshot(rails, currentGeometry);
    }

    public static MtrSnapshot readMtr3(Class<?> clientData, Object dimension, PositionReader positions,
                                       EndpointReader endpoints, Map<String, CachedGeometry> previousGeometry)
            throws ReflectiveOperationException {
        Object rawRails = TransitStationReader.field(clientData, "RAILS");
        if (!(rawRails instanceof Map<?, ?> outer)) throw new NoSuchFieldException("MTR 3 rails");
        List<TransitRail> rails = new ArrayList<>();
        Map<String, CachedGeometry> currentGeometry = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int pointCount = 0;
        int candidates = 0;
        outerLoop:
        for (Map.Entry<?, ?> outerEntry : outer.entrySet()) {
            if (!(outerEntry.getValue() instanceof Map<?, ?> inner)) continue;
            for (Map.Entry<?, ?> innerEntry : inner.entrySet()) {
                if (++candidates > MAX_CANDIDATES || rails.size() >= MAX_RAILS || pointCount + 2 > MAX_POINTS) break outerLoop;
                Object rail = innerEntry.getValue();
                if (rail == null || !isTrain(TransitStationReader.field(rail, "transportMode"))
                        || !Boolean.TRUE.equals(TransitStationReader.call(rail, "isValid"))) continue;
                TransitRail.Point first = endpoints.read(outerEntry.getKey());
                TransitRail.Point second = endpoints.read(innerEntry.getKey());
                if (!first.isFinite() || !second.isFinite()) continue;
                String key = "mtr3:" + canonicalEndpoints(first, second);
                if (seen.contains(key)) continue;
                CachedGeometry cached = previousGeometry == null ? null : previousGeometry.get(key);
                List<TransitRail.Point> path;
                if (cached != null && cached.geometryIdentity() == rail) {
                    path = cached.points();
                } else {
                    try {
                        double length = number(TransitStationReader.call(rail, "getLength"));
                        path = sample(length, distance -> positions.read(
                                TransitStationReader.call(rail, "getPosition", distance)));
                    } catch (InvalidRailDataException ignored) {
                        continue;
                    }
                }
                if (path == null || pointCount + path.size() > MAX_POINTS) continue;
                rails.add(new TransitRail(TransitRail.Source.MTR, dimension, path));
                currentGeometry.put(key, new CachedGeometry(rail, path));
                pointCount += path.size();
                seen.add(key);
            }
        }
        return new MtrSnapshot(rails, currentGeometry);
    }

    private static List<TransitRail.Point> createPath(Object graph, Object edge, Object firstLocation,
                                                       Object secondLocation, PositionReader positions)
            throws ReflectiveOperationException, InvalidRailDataException {
        if (!Boolean.TRUE.equals(TransitStationReader.call(edge, "isTurn"))) {
            List<TransitRail.Point> endpoints = new ArrayList<>(2);
            endpoints.add(positions.read(TransitStationReader.call(firstLocation, "getLocation")));
            endpoints.add(positions.read(TransitStationReader.call(secondLocation, "getLocation")));
            return validPath(endpoints);
        }
        double length = number(TransitStationReader.call(edge, "getLength"));
        int segments = segmentCount(length);
        List<TransitRail.Point> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            points.add(positions.read(TransitStationReader.call(edge, "getPosition", graph, t)));
        }
        return validPath(points);
    }

    @FunctionalInterface
    private interface SamplePosition { TransitRail.Point at(double distance) throws ReflectiveOperationException; }

    private static List<TransitRail.Point> sample(double length, SamplePosition sampler)
            throws ReflectiveOperationException, InvalidRailDataException {
        int segments = segmentCount(length);
        List<TransitRail.Point> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double distance = i == segments ? length : length * i / segments;
            points.add(sampler.at(distance));
        }
        return simplifyStraightPath(validPath(points));
    }

    /** Long straight MTR tracks need only their endpoints; keep every sample on an actual curve. */
    private static List<TransitRail.Point> simplifyStraightPath(List<TransitRail.Point> points) {
        if (points.size() <= 2) return points;
        TransitRail.Point first = points.get(0);
        TransitRail.Point last = points.get(points.size() - 1);
        double dx = last.x() - first.x();
        double dz = last.z() - first.z();
        double length = Math.hypot(dx, dz);
        for (int i = 1; i < points.size() - 1; i++) {
            TransitRail.Point point = points.get(i);
            double deviation = length == 0
                    ? Math.hypot(point.x() - first.x(), point.z() - first.z())
                    : Math.abs((point.x() - first.x()) * dz - (point.z() - first.z()) * dx) / length;
            if (deviation > 0.001) return points;
        }
        return List.of(first, last);
    }

    private static int segmentCount(double length) throws InvalidRailDataException {
        if (!Double.isFinite(length) || length <= 0) throw new InvalidRailDataException();
        if (length >= BLOCKS_PER_SAMPLE * MAX_SEGMENTS_PER_RAIL) return MAX_SEGMENTS_PER_RAIL;
        return Math.max(1, Math.min(MAX_SEGMENTS_PER_RAIL, (int) Math.ceil(length / BLOCKS_PER_SAMPLE)));
    }

    private static List<TransitRail.Point> validPath(List<TransitRail.Point> points) throws InvalidRailDataException {
        if (points == null || points.size() < 2 || points.size() > MAX_SEGMENTS_PER_RAIL + 1) {
            throw new InvalidRailDataException();
        }
        for (TransitRail.Point point : points) if (point == null || !point.isFinite()) throw new InvalidRailDataException();
        return List.copyOf(points);
    }

    private static double number(Object value) throws InvalidRailDataException {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new InvalidRailDataException();
        return number.doubleValue();
    }

    private static boolean isTrain(Object mode) {
        return mode instanceof Enum<?> value && value.name().equals("TRAIN");
    }

    private static long pairKey(int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return ((long) min << 32) ^ (max & 0xFFFF_FFFFL);
    }

    private static String canonicalEndpoints(TransitRail.Point first, TransitRail.Point second) {
        String a = coordinateKey(first);
        String b = coordinateKey(second);
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String coordinateKey(TransitRail.Point point) {
        return Double.toHexString(point.x()) + "," + Double.toHexString(point.y()) + "," + Double.toHexString(point.z());
    }

    private static Collection<?> collectionValues(Object value) throws NoSuchFieldException {
        if (value instanceof Map<?, ?> map) return map.values();
        if (value instanceof Collection<?> collection) return collection;
        throw new NoSuchFieldException("Expected Create track network collection");
    }

    private static final class InvalidRailDataException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
