// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TransitRailReaderTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        transitRailIsImmutableAndValidatesItsGeometry();
        createReadsCurvesAndDeduplicatesReverseConnections();
        createSkipsInterdimensionalAndCrossDimensionConnections();
        createBoundsMalformedCurveSampling();
        createCacheTracksManagerGraphVersionAndDimension();
        mtr4FiltersModesAndReusesOnlyUnchangedGeometry();
        mtr4RejectsInvalidAndNonFinitePathsAndBoundsSampling();
        mtr3DeduplicatesReciprocalRailsAndPrunesRemovedEntries();
        System.out.println("Transit rail checks passed (" + assertions + " assertions).");
    }

    private static void transitRailIsImmutableAndValidatesItsGeometry() {
        List<TransitRail.Point> source = new ArrayList<>(List.of(point(1, 2, 3), point(4, 5, 6)));
        TransitRail rail = new TransitRail(TransitRail.Source.MTR, DIMENSION, source);
        source.clear();
        check(rail.points().size() == 2, "rail defensively copies the caller's mutable path");
        boolean immutable = false;
        try { rail.points().clear(); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "rail path is immutable after construction");
        expectIllegal(() -> new TransitRail(TransitRail.Source.CREATE, DIMENSION, List.of(point(0, 0, 0))),
                "one point is not a drawable path");
        expectIllegal(() -> new TransitRail(TransitRail.Source.CREATE, DIMENSION,
                List.of(point(0, 0, 0), point(Double.NaN, 1, 1))), "non-finite coordinates are rejected");
    }

    private static void createReadsCurvesAndDeduplicatesReverseConnections() throws Exception {
        FakeNode a = node(11, DIMENSION, point(0, 64, 0));
        FakeNode b = node(22, DIMENSION, point(12, 64, 0));
        FakeGraph graph = new FakeGraph(a, b);
        FakeEdge forwardCurve = FakeEdge.curve(12, 2);
        FakeEdge reverseCurve = FakeEdge.curve(12, 2);
        graph.connect(a, b, forwardCurve);
        graph.connect(b, a, reverseCurve);

        List<TransitRail> rails = TransitRailReader.readCreate(networks(graph), DIMENSION, TransitRailReaderTest::readPoint);
        check(rails.size() == 1, "reciprocal Create connections render once");
        TransitRail rail = rails.get(0);
        check(rail.source() == TransitRail.Source.CREATE && rail.dimension().equals(DIMENSION),
                "Create rail preserves its source and current dimension");
        check(rail.points().size() == 4, "Create curve sampling follows its length at roughly four-block intervals");
        check(rail.points().get(0).equals(point(0, 64, 0)) && rail.points().get(3).equals(point(12, 64, 0)),
                "Create curve starts and ends at graph geometry endpoints");
        check(rail.points().get(1).z() > 0 && rail.points().get(2).z() > 0,
                "Create curved track retains its middle geometry instead of drawing an endpoint chord");

        FakeNode c = node(33, DIMENSION, point(20, 70, -3));
        FakeGraph straightGraph = new FakeGraph(c, node(34, DIMENSION, point(24, 70, -3)));
        straightGraph.connect(c, straightGraph.nodes.get(1), FakeEdge.straight(4));
        List<TransitRail> straight = TransitRailReader.readCreate(networks(straightGraph), DIMENSION,
                TransitRailReaderTest::readPoint);
        check(straight.size() == 1 && straight.get(0).points().equals(
                        List.of(point(20, 70, -3), point(24, 70, -3))),
                "straight Create edges use only their two real node endpoints");
    }

    private static void createSkipsInterdimensionalAndCrossDimensionConnections() throws Exception {
        FakeNode a = node(1, DIMENSION, point(0, 0, 0));
        FakeNode b = node(2, DIMENSION, point(10, 0, 0));
        FakeNode nether = node(3, "minecraft:the_nether", point(0, 0, 0));
        FakeGraph graph = new FakeGraph(a, b, nether);
        graph.connect(a, b, FakeEdge.interdimensional());
        graph.connect(a, nether, FakeEdge.straight(10));
        List<TransitRail> rails = TransitRailReader.readCreate(networks(graph), DIMENSION,
                TransitRailReaderTest::readPoint);
        check(rails.isEmpty(), "interdimensional and cross-dimension Create connections are hidden");
        check(TransitRailReader.readCreate(networks(graph), "minecraft:the_nether", TransitRailReaderTest::readPoint)
                .isEmpty(), "a graph edge is not shown when its other endpoint belongs to another dimension");
    }

    private static void createBoundsMalformedCurveSampling() throws Exception {
        FakeNode a = node(1, DIMENSION, point(0, 0, 0));
        FakeNode b = node(2, DIMENSION, point(1_000_000_000, 0, 0));
        FakeGraph graph = new FakeGraph(a, b);
        graph.connect(a, b, FakeEdge.curve(1_000_000_000, 0));
        List<TransitRail> capped = TransitRailReader.readCreate(networks(graph), DIMENSION,
                TransitRailReaderTest::readPoint);
        check(capped.size() == 1 && capped.get(0).points().size() == TransitRailReader.MAX_SEGMENTS_PER_RAIL + 1,
                "a huge curve is clamped to the per-rail sample limit");

        FakeGraph invalid = new FakeGraph(a, node(4, DIMENSION, point(2, 0, 999)));
        invalid.connect(a, invalid.nodes.get(1), FakeEdge.straight(2));
        List<TransitRail> skipped = TransitRailReader.readCreate(networks(invalid), DIMENSION,
                value -> ((Vec) value).z == 999 ? null : readPoint(value));
        check(skipped.isEmpty(), "a path with a null sampled endpoint is skipped without hiding the provider");
    }

    private static void createCacheTracksManagerGraphVersionAndDimension() {
        TransitRailCreateCache cache = new TransitRailCreateCache();
        Object manager = new Object();
        Object graphMap = new Object();
        cache.update(manager, graphMap, 7, DIMENSION, List.of());
        check(cache.matches(manager, graphMap, 7, DIMENSION), "Create snapshot is reused for an unchanged manager and revision");
        check(!cache.matches(manager, graphMap, 8, DIMENSION), "Create revision invalidates geometry even with identical nodes");
        check(!cache.matches(manager, new Object(), 7, DIMENSION), "Create network map replacement invalidates geometry");
        check(!cache.matches(new Object(), graphMap, 7, DIMENSION), "Create manager replacement invalidates geometry");
        check(!cache.matches(manager, graphMap, 7, "minecraft:the_nether"), "dimension change invalidates geometry");
        cache.invalidate();
        check(!cache.matches(manager, graphMap, 7, DIMENSION) && cache.rails().isEmpty(),
                "session reset drops the Create snapshot");
    }

    private static void mtr4FiltersModesAndReusesOnlyUnchangedGeometry() throws Exception {
        FakeMtr4Data data = new FakeMtr4Data();
        FakeRailMath geometry = new FakeRailMath(8, 3);
        FakeMtr4Rail train = new FakeMtr4Rail("ab", Mode.TRAIN, true, geometry);
        data.rails.add(train);
        data.rails.add(new FakeMtr4Rail("ab", Mode.TRAIN, true, geometry)); // duplicate direction
        data.rails.add(new FakeMtr4Rail("boat", Mode.BOAT, true, new FakeRailMath(8, 9)));
        data.rails.add(new FakeMtr4Rail("invalid", Mode.TRAIN, false, new FakeRailMath(8, 9)));

        TransitRailReader.MtrSnapshot first = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, Map.of());
        check(first.rails().size() == 1, "MTR4 shows valid train geometry once and filters boats and invalid rails");
        check(first.rails().get(0).points().size() == 3 && first.rails().get(0).points().get(1).z() == 3,
                "MTR4 rail math provides the actual curve midpoint");
        check(geometry.samples == 3, "MTR4 samples each geometry at its bounded intervals");

        TransitRailReader.MtrSnapshot unchanged = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, first.geometryCache());
        check(geometry.samples == 3, "unchanged MTR4 RailMath reuses its cached points between polls");
        check(unchanged.geometryCache().size() == 1, "MTR4 cache includes only current valid train rails");

        FakeRailMath replacementGeometry = new FakeRailMath(8, 6);
        data.rails.clear();
        data.rails.add(new FakeMtr4Rail("ab", Mode.TRAIN, true, replacementGeometry));
        TransitRailReader.MtrSnapshot changed = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, unchanged.geometryCache());
        check(replacementGeometry.samples == 3 && changed.rails().get(0).points().get(1).z() == 6,
                "same-size MTR4 updates resample when rail geometry identity changes");

        FakeRailMath straightGeometry = new FakeRailMath(100, 0);
        data.rails.clear();
        data.rails.add(new FakeMtr4Rail("straight", Mode.TRAIN, true, straightGeometry));
        TransitRailReader.MtrSnapshot straight = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, Map.of());
        check(straight.rails().get(0).points().size() == 2,
                "sampled straight MTR4 geometry collapses to its endpoints for a lighter map overlay");

        data.rails.clear();
        TransitRailReader.MtrSnapshot deleted = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, changed.geometryCache());
        check(deleted.rails().isEmpty() && deleted.geometryCache().isEmpty(),
                "deleted MTR4 rails disappear and their cache entries are pruned");
    }

    private static void mtr4RejectsInvalidAndNonFinitePathsAndBoundsSampling() throws Exception {
        FakeMtr4Data data = new FakeMtr4Data();
        data.rails.add(new FakeMtr4Rail("bad", Mode.TRAIN, true, new FakeRailMath(0, 1)));
        data.rails.add(new FakeMtr4Rail("nan", Mode.TRAIN, true, new FakeRailMath(8, Double.NaN)));
        data.rails.add(new FakeMtr4Rail("null", Mode.TRAIN, true, null));
        TransitRailReader.MtrSnapshot invalid = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, Map.of());
        check(invalid.rails().isEmpty() && invalid.geometryCache().isEmpty(),
                "zero-length, non-finite, and missing MTR geometry are skipped");

        FakeRailMath huge = new FakeRailMath(1e12, 1);
        data.rails.clear();
        data.rails.add(new FakeMtr4Rail("huge", Mode.TRAIN, true, huge));
        TransitRailReader.MtrSnapshot capped = TransitRailReader.readMtr4(data, DIMENSION,
                TransitRailReaderTest::readPoint, Map.of());
        check(capped.rails().get(0).points().size() == TransitRailReader.MAX_SEGMENTS_PER_RAIL + 1
                        && huge.samples == TransitRailReader.MAX_SEGMENTS_PER_RAIL + 1,
                "MTR4 huge lengths cannot cause unbounded curve sampling");
    }

    private static void mtr3DeduplicatesReciprocalRailsAndPrunesRemovedEntries() throws Exception {
        FakeMtr3Data.RAILS.clear();
        FakeEndpoint start = new FakeEndpoint(10.5, 65, 20.5);
        FakeEndpoint end = new FakeEndpoint(30.5, 65, 20.5);
        FakeMtr3Rail rail = new FakeMtr3Rail(20, 2);
        FakeMtr3Data.connect(start, end, rail);
        FakeMtr3Data.connect(end, start, rail);
        TransitRailReader.MtrSnapshot first = TransitRailReader.readMtr3(FakeMtr3Data.class, DIMENSION,
                TransitRailReaderTest::readPoint, TransitRailReaderTest::readEndpoint, Map.of());
        check(first.rails().size() == 1, "MTR3 reciprocal endpoint entries render once");
        check(first.rails().get(0).points().get(0).x() == 10.5
                        && first.rails().get(0).points().get(0).z() == 20.5,
                "MTR3 position preserves its native half-block coordinate offset");
        check(first.geometryCache().size() == 1, "MTR3 geometry cache is keyed by canonical endpoint pair");

        FakeMtr3Rail replacement = new FakeMtr3Rail(20, 4);
        FakeMtr3Data.connect(start, end, replacement);
        FakeMtr3Data.RAILS.remove(end);
        TransitRailReader.MtrSnapshot changed = TransitRailReader.readMtr3(FakeMtr3Data.class, DIMENSION,
                TransitRailReaderTest::readPoint, TransitRailReaderTest::readEndpoint, first.geometryCache());
        check(replacement.samples == 6, "same-size MTR3 endpoint updates sample replacement rail: " + replacement.samples);
        check(Math.abs(changed.rails().get(0).points().get(3).z() - 24.34) < 1e-9,
                "replacement MTR3 curve uses its own geometry: " + changed.rails().get(0).points());

        FakeMtr3Data.RAILS.clear();
        TransitRailReader.MtrSnapshot removed = TransitRailReader.readMtr3(FakeMtr3Data.class, DIMENSION,
                TransitRailReaderTest::readPoint, TransitRailReaderTest::readEndpoint, changed.geometryCache());
        check(removed.rails().isEmpty() && removed.geometryCache().isEmpty(),
                "removed MTR3 nearby rails and cache entries are discarded on the next poll");
    }

    private static Map<UUID, FakeGraph> networks(FakeGraph... graphs) {
        Map<UUID, FakeGraph> result = new HashMap<>();
        for (int i = 0; i < graphs.length; i++) result.put(new UUID(0, i + 1), graphs[i]);
        return result;
    }

    private static FakeNode node(int id, String dimension, TransitRail.Point point) {
        return new FakeNode(id, new FakeTrackNodeLocation(dimension, new Vec(point.x(), point.y(), point.z())));
    }

    private static TransitRail.Point readPoint(Object value) throws NoSuchFieldException {
        if (value instanceof Vec vec) return point(vec.x, vec.y, vec.z);
        if (value instanceof TransitRail.Point result) return result;
        throw new NoSuchFieldException("Unexpected fixture position");
    }

    private static TransitRail.Point readEndpoint(Object value) throws NoSuchFieldException {
        if (!(value instanceof FakeEndpoint endpoint)) throw new NoSuchFieldException("Unexpected MTR endpoint");
        return point(endpoint.x, endpoint.y, endpoint.z);
    }

    private static TransitRail.Point point(double x, double y, double z) { return new TransitRail.Point(x, y, z); }

    private static void expectIllegal(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static final class Vec {
        public final double x, y, z;
        public Vec(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static final class FakeTrackNodeLocation {
        private final String dimension;
        private final Vec location;
        public FakeTrackNodeLocation(String dimension, Vec location) { this.dimension = dimension; this.location = location; }
        public String getDimension() { return dimension; }
        public Vec getLocation() { return location; }
    }

    public static final class FakeNode {
        private final int netId;
        private final FakeTrackNodeLocation location;
        private FakeNode(int netId, FakeTrackNodeLocation location) { this.netId = netId; this.location = location; }
        public int getNetId() { return netId; }
        public FakeTrackNodeLocation getLocation() { return location; }
    }

    public static final class FakeGraph {
        private final Map<FakeTrackNodeLocation, FakeNode> byLocation = new HashMap<>();
        private final List<FakeNode> nodes = new ArrayList<>();
        private final Map<FakeNode, Map<FakeNode, FakeEdge>> connections = new HashMap<>();
        public FakeGraph(FakeNode... nodes) {
            for (FakeNode node : nodes) { this.nodes.add(node); byLocation.put(node.location, node); }
        }
        public Set<FakeTrackNodeLocation> getNodes() { return byLocation.keySet(); }
        public FakeNode locateNode(FakeTrackNodeLocation location) { return byLocation.get(location); }
        public Map<FakeNode, FakeEdge> getConnectionsFrom(FakeNode node) {
            return connections.computeIfAbsent(node, ignored -> new HashMap<>());
        }
        public void connect(FakeNode from, FakeNode to, FakeEdge edge) { getConnectionsFrom(from).put(to, edge); }
    }

    public static final class FakeEdge {
        private final boolean turn;
        private final boolean interdimensional;
        private final double length;
        private final double curve;
        private FakeEdge(boolean turn, boolean interdimensional, double length, double curve) {
            this.turn = turn; this.interdimensional = interdimensional; this.length = length; this.curve = curve;
        }
        public static FakeEdge straight(double length) { return new FakeEdge(false, false, length, 0); }
        public static FakeEdge curve(double length, double lift) { return new FakeEdge(true, false, length, lift); }
        public static FakeEdge interdimensional() { return new FakeEdge(false, true, 10, 0); }
        public boolean isTurn() { return turn; }
        public boolean isInterDimensional() { return interdimensional; }
        public double getLength() { return length; }
        public Vec getPosition(Object graph, double t) { return new Vec(length * t, 64, curve * 4 * t * (1 - t)); }
    }

    public enum Mode { TRAIN, BOAT }

    public static final class FakeMtr4Data {
        public final Set<FakeMtr4Rail> rails = new HashSet<>();
    }

    public static final class FakeMtr4Rail {
        private final String hexId;
        private final Mode mode;
        private final boolean valid;
        public final FakeRailMath railMath;
        public FakeMtr4Rail(String hexId, Mode mode, boolean valid, FakeRailMath railMath) {
            this.hexId = hexId; this.mode = mode; this.valid = valid; this.railMath = railMath;
        }
        public Mode getTransportMode() { return mode; }
        public boolean isValid() { return valid; }
        public String getHexId() { return hexId; }
    }

    public static final class FakeRailMath {
        private final double length;
        private final double curve;
        public int samples;
        public FakeRailMath(double length, double curve) { this.length = length; this.curve = curve; }
        public double getLength() { return length; }
        public Vec getPosition(double distance, boolean reverse) {
            samples++;
            double t = length == 0 ? 0 : distance / length;
            if (reverse) t = 1 - t;
            return new Vec(10 + length * t, 65, curve * 4 * t * (1 - t));
        }
    }

    public static final class FakeEndpoint {
        private final double x, y, z;
        public FakeEndpoint(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        @Override public int hashCode() { return java.util.Objects.hash(x, y, z); }
        @Override public boolean equals(Object other) {
            return other instanceof FakeEndpoint point && point.x == x && point.y == y && point.z == z;
        }
    }

    public static final class FakeMtr3Data {
        public static final Map<FakeEndpoint, Map<FakeEndpoint, FakeMtr3Rail>> RAILS = new HashMap<>();
        public static void connect(FakeEndpoint from, FakeEndpoint to, FakeMtr3Rail rail) {
            RAILS.computeIfAbsent(from, ignored -> new HashMap<>()).put(to, rail);
        }
    }

    public static final class FakeMtr3Rail {
        public final Mode transportMode = Mode.TRAIN;
        public final String railType = "NONE";
        private final double length;
        private final double curve;
        public int samples;
        public FakeMtr3Rail(double length, double curve) { this.length = length; this.curve = curve; }
        public boolean isValid() { return true; }
        public double getLength() { return length; }
        public Vec getPosition(double distance) {
            samples++;
            double t = length == 0 ? 0 : distance / length;
            return new Vec(10.5 + length * t, 65, 20.5 + curve * 4 * t * (1 - t));
        }
    }
}
