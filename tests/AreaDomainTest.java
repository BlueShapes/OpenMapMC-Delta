package net.aosankaku.openmapdelta.area;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pure geometry and storage acceptance tests for the 26.2 place-area model. */
public final class AreaDomainTest {
    private static int assertions;

    private AreaDomainTest() {}

    public static void main(String[] args) throws Exception {
        immutableBranchGeometryAndHoleContainment();
        rejectInvalidRingsAndCoordinates();
        preserveHierarchicalScopeAndRejectCycles();
        splitAndUnionAreaExactly();
        editSharedAdministrativeBorderAtomically();
        draftHistoryRestoresWholeDocuments();
        persistenceIsBoundedVersionedAndIdentityScoped();
        geometryReviewRegressions();
        System.out.println("PASS AreaDomainTest: " + assertions + " assertions");
    }

    private static void immutableBranchGeometryAndHoleContainment() {
        List<MapArea.Point> branchOne = new ArrayList<>(List.of(point(0, 0), point(4, 0), point(8, 3)));
        List<MapArea.Point> branchTwo = new ArrayList<>(List.of(point(4, 0), point(4, 8)));
        MapArea.Geometry branching = MapArea.Geometry.line(List.of(branchOne, branchTwo));
        branchOne.clear();
        equal(2, branching.paths().size(), "line branches are copied");
        equal(3, branching.paths().get(0).size(), "branch vertices are copied");
        throwsType(UnsupportedOperationException.class,
                () -> branching.paths().get(0).add(point(12, 9)), "geometry path is immutable");

        MapArea.Polygon withLakeHole = new MapArea.Polygon(
                ring(0, 0, 16, 16), List.of(ring(5, 5, 11, 11)));
        MapArea.Geometry region = MapArea.Geometry.polygon(List.of(withLakeHole));
        check(AreaGeometryOps.contains(region, point(2, 2)), "outer region contains an ordinary point");
        check(!AreaGeometryOps.contains(region, point(8, 8)), "a hole is excluded from region containment");
        check(AreaGeometryOps.contains(region, AreaGeometryOps.anchor(region)), "automatic label anchor remains outside holes");

        MapArea.Geometry islands = MapArea.Geometry.polygon(List.of(
                new MapArea.Polygon(ring(30, 0, 40, 10), List.of()),
                new MapArea.Polygon(ring(60, 0, 70, 10), List.of())));
        check(AreaGeometryOps.contains(islands, point(35, 5)), "first disconnected component is retained");
        check(AreaGeometryOps.contains(islands, point(65, 5)), "second disconnected component is retained");
        check(!AreaGeometryOps.contains(islands, point(50, 5)), "space between components is not filled");
    }

    private static void rejectInvalidRingsAndCoordinates() {
        throwsType(IllegalArgumentException.class, () -> point(Double.NaN, 0), "nonfinite coordinates are rejected");
        throwsType(IllegalArgumentException.class, () -> point(30_000_001, 0), "coordinates beyond world bounds are rejected");
        throwsType(IllegalArgumentException.class,
                () -> MapArea.Geometry.polygon(List.of(new MapArea.Polygon(
                        List.of(point(0, 0), point(8, 8), point(0, 8), point(8, 0)), List.of()))),
                "self-intersecting polygons are rejected");
        throwsType(IllegalArgumentException.class,
                () -> MapArea.Geometry.polygon(List.of(new MapArea.Polygon(
                        ring(0, 0, 10, 10), List.of(ring(12, 12, 14, 14))))),
                "a hole outside its outer ring is rejected");
        throwsType(IllegalArgumentException.class,
                () -> area("  \u0001bad\nname  ", MapArea.Kind.LAND, MapArea.Scope.PERSONAL, null,
                        MapArea.Geometry.label(), point(0, 0), UUID.randomUUID()),
                "control characters in place names are rejected");
        throwsType(IllegalArgumentException.class,
                () -> area("bad" + (char) 0xD800, MapArea.Kind.LAND, MapArea.Scope.PERSONAL, null,
                        MapArea.Geometry.label(), point(0, 0), UUID.randomUUID()),
                "unpaired high surrogates in place names are rejected");
        throwsType(IllegalArgumentException.class,
                () -> area("bad" + (char) 0xDC00, MapArea.Kind.LAND, MapArea.Scope.PERSONAL, null,
                        MapArea.Geometry.label(), point(0, 0), UUID.randomUUID()),
                "unpaired low surrogates in place names are rejected");
    }

    private static void preserveHierarchicalScopeAndRejectCycles() {
        UUID owner = UUID.randomUUID();
        MapArea country = area("国", MapArea.Kind.COUNTRY, MapArea.Scope.PERSONAL, null,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(0, 0, 100, 100), List.of()))),
                point(50, 50), owner);
        MapArea region = area("北部", MapArea.Kind.REGION, MapArea.Scope.PERSONAL, country.id(),
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(0, 50, 100, 100), List.of()))),
                point(50, 75), owner);
        MapArea municipality = area("港", MapArea.Kind.REGION, MapArea.Scope.PERSONAL, region.id(),
                MapArea.Geometry.label(), point(10, 55), owner);
        AreaDocument initial = new AreaDocument(3, List.of(country, region, municipality));

        throwsType(IllegalArgumentException.class,
                () -> new AreaDocument(4, List.of(country.withParent(municipality.id()), region, municipality)),
                "parent cycles are rejected");
        throwsType(IllegalArgumentException.class,
                () -> new AreaDocument(4, List.of(country,
                        area("Shared child", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                                MapArea.Geometry.label(), point(20, 60), owner))),
                "a private area cannot be attached to a server scope parent");
        equal(Optional.of(region), initial.find(region.id()), "hierarchy is addressable by stable ID");

        AreaDocument renamed = initial.upsert(region.withName("雪国北部").withRevision(6));
        equal("北部", initial.find(region.id()).orElseThrow().name(), "document edits do not mutate the previous value");
        equal("雪国北部", renamed.find(region.id()).orElseThrow().name(), "updated value is visible in the new document");
        equal(3L, initial.revision(), "document edits preserve caller-owned collection revision");
        equal(3, initial.areas().size(), "document exposes an immutable member snapshot");
        throwsType(UnsupportedOperationException.class,
                () -> initial.areas().clear(), "document member list is immutable");
    }

    private static void splitAndUnionAreaExactly() {
        MapArea.Geometry concave = MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                point(0, 0), point(12, 0), point(12, 12), point(8, 12),
                point(8, 4), point(4, 4), point(4, 12), point(0, 12)), List.of())));
        double originalArea = signedOuterArea(concave);
        List<MapArea.Geometry> halves = AreaGeometryOps.split(concave, point(6, -2), point(6, 14));
        equal(2, halves.size(), "cut across a concave polygon creates two sides");
        close(originalArea, halves.stream().mapToDouble(AreaDomainTest::signedOuterArea).sum(),
                "split conserves concave area");
        MapArea.Geometry rebuilt = AreaGeometryOps.union(halves);
        close(originalArea, signedOuterArea(rebuilt), "union restores the original area");
        check(AreaGeometryOps.contains(rebuilt, point(2, 10)), "union keeps first arm of concave shape");
        check(!AreaGeometryOps.contains(rebuilt, point(6, 10)), "union preserves notch outside the source shape");

        MapArea.Geometry ringShape = MapArea.Geometry.polygon(List.of(new MapArea.Polygon(
                ring(0, 0, 20, 20), List.of(ring(6, 6, 14, 14)))));
        double areaWithHole = signedArea(ring(0, 0, 20, 20)) - signedArea(ring(6, 6, 14, 14));
        List<MapArea.Geometry> ringHalves = AreaGeometryOps.split(ringShape, point(10, -1), point(10, 21));
        equal(2, ringHalves.size(), "cut through a hole still creates two valid sides");
        close(areaWithHole, ringHalves.stream().mapToDouble(AreaDomainTest::signedAreaWithHoles).sum(),
                "split conserves an area with a hole");
        MapArea.Geometry restoredRing = AreaGeometryOps.union(ringHalves);
        check(!AreaGeometryOps.contains(restoredRing, point(10, 10)), "union restores the hole");
        close(areaWithHole, signedAreaWithHoles(restoredRing), "union conserves holes");

        List<MapArea.Geometry> disconnected = List.of(
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(-20, 0, -10, 10), List.of()))),
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(10, 0, 20, 10), List.of()))));
        check(AreaGeometryOps.contains(AreaGeometryOps.union(disconnected), point(15, 5)),
                "union retains every disconnected component");
    }

    private static void editSharedAdministrativeBorderAtomically() {
        UUID owner = UUID.randomUUID();
        MapArea country = area("State", MapArea.Kind.COUNTRY, MapArea.Scope.SHARED, null,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(0, 0, 20, 10), List.of()))), point(10, 5), owner);
        MapArea west = area("West", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                        point(0, 0), point(10, 0), point(10, 5), point(10, 10), point(0, 10)), List.of()))),
                point(5, 5), owner);
        MapArea east = area("East", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                        point(10, 0), point(20, 0), point(20, 10), point(10, 10), point(10, 5)), List.of()))),
                point(15, 5), owner);
        AreaDocument document = new AreaDocument(9, List.of(country, west, east));

        AreaDocument moved = AreaGeometryOps.movePoint(document, west.id(), point(10, 5), point(12, 5));
        check(hasOuterVertex(moved.find(west.id()).orElseThrow(), point(12, 5)),
                "selected region's shared border vertex moves");
        check(hasOuterVertex(moved.find(east.id()).orElseThrow(), point(12, 5)),
                "sibling region's shared border vertex moves in the same transaction");
        check(hasOuterVertex(document.find(east.id()).orElseThrow(), point(10, 5)),
                "previous document remains unmodified when edit succeeds");

        AreaDocument withVertex = AreaGeometryOps.insertPoint(document, west.id(), point(10, 0), point(10, 5), point(10, 2));
        check(hasOuterVertex(withVertex.find(west.id()).orElseThrow(), point(10, 2)),
                "new vertex is inserted into selected shared border");
        check(hasOuterVertex(withVertex.find(east.id()).orElseThrow(), point(10, 2)),
                "same vertex is inserted into adjacent shared border atomically");
        throwsType(IllegalArgumentException.class,
                () -> AreaGeometryOps.movePoint(document, west.id(), point(10, 5), point(11, 11)),
                "a topology edit that leaves its original bounds is rejected atomically");
        check(hasOuterVertex(document.find(east.id()).orElseThrow(), point(10, 5)),
                "failed topology edit preserves every original region");
    }

    private static void draftHistoryRestoresWholeDocuments() {
        UUID owner = UUID.randomUUID();
        MapArea first = area("One", MapArea.Kind.LAND, MapArea.Scope.PERSONAL, null,
                MapArea.Geometry.label(), point(0, 0), owner);
        AreaDocument empty = new AreaDocument(0, List.of());
        AreaDocument one = empty.upsert(first);
        AreaDocument two = one.upsert(area("Two", MapArea.Kind.LAND, MapArea.Scope.PERSONAL, null,
                MapArea.Geometry.label(), point(1, 1), owner));
        AreaEditHistory history = new AreaEditHistory(empty);
        history.apply(one);
        history.apply(two);
        check(history.canUndo(), "draft history exposes undo after a change");
        equal(2, history.current().areas().size(), "draft contains all staged changes");
        history.undo();
        equal(1, history.current().areas().size(), "one undo reverts a complete document transaction");
        history.redo();
        equal(2, history.current().areas().size(), "redo restores the complete transaction");
        history.undo();
        history.undo();
        check(!history.canUndo(), "undo ends at the initial document");
        equal(0, history.current().areas().size(), "initial document is restorable");
        check(history.canRedo(), "redo remains available after undo");
    }

    private static void persistenceIsBoundedVersionedAndIdentityScoped() throws IOException {
        Path output = Path.of(System.getProperty("user.dir"), ".task-work", "areas-20261028", "store-tests");
        Files.createDirectories(output);
        Path store = output.resolve("areas.json");
        Files.deleteIfExists(store);

        UUID owner = UUID.randomUUID();
        MapArea feature = area("雪国の湖🌊", MapArea.Kind.POND, MapArea.Scope.PERSONAL, null,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(-12, -6, 18, 22), List.of()))),
                point(3, 8), owner);
        AreaDocument original = new AreaDocument(17, List.of(feature));
        AreaStore.save(store, "world:alpha", original);
        AreaStore.LoadResult loaded = AreaStore.load(store, "world:alpha");
        check(loaded.exists() && loaded.valid(), "valid versioned area data loads");
        equal(original, loaded.document(), "Unicode names and geometry survive store round-trip");

        byte[] validBytes = Files.readAllBytes(store);
        AreaStore.LoadResult otherWorld = AreaStore.load(store, "world:beta");
        check(otherWorld.exists() && !otherWorld.valid(), "data for another world identity is rejected");
        bytesEqual(validBytes, Files.readAllBytes(store), "wrong identity does not modify the source file");
        throwsType(IOException.class, () -> AreaStore.save(store, "world:beta", original),
                "saving over a different existing identity is rejected");
        bytesEqual(validBytes, Files.readAllBytes(store), "failed cross-world save preserves original bytes");

        String validJson = Files.readString(store, StandardCharsets.UTF_8);
        Path futureVersion = output.resolve("future.json");
        Files.writeString(futureVersion, validJson.replace("\"version\":1", "\"version\":2"), StandardCharsets.UTF_8);
        check(!AreaStore.load(futureVersion, "world:alpha").valid(), "future schema versions fail closed");
        byte[] futureBytes = Files.readAllBytes(futureVersion);
        check(!AreaStore.load(futureVersion, "world:alpha").valid(), "failed future-version reads remain failed");
        bytesEqual(futureBytes, Files.readAllBytes(futureVersion), "future-version data is never rewritten");

        Path truncated = output.resolve("truncated.json");
        Files.writeString(truncated, "{\"version\":1,\"identity\":\"world:alpha\",\"areas\":[", StandardCharsets.UTF_8);
        byte[] truncatedBytes = Files.readAllBytes(truncated);
        check(!AreaStore.load(truncated, "world:alpha").valid(), "truncated JSON fails closed");
        bytesEqual(truncatedBytes, Files.readAllBytes(truncated), "malformed data remains byte-for-byte preserved");

        Path trailing = output.resolve("trailing.json");
        Files.writeString(trailing, validJson + " true", StandardCharsets.UTF_8);
        check(!AreaStore.load(trailing, "world:alpha").valid(), "trailing JSON tokens are rejected");
        Files.writeString(output.resolve("oversized.json"), " ".repeat(8 * 1024 * 1024 + 1), StandardCharsets.UTF_8);
        check(!AreaStore.load(output.resolve("oversized.json"), "world:alpha").valid(), "oversized documents are rejected before parsing");

        Path duplicateIds = output.resolve("duplicates.json");
        Files.deleteIfExists(duplicateIds);
        MapArea secondFeature = area("Second", MapArea.Kind.LAND, MapArea.Scope.PERSONAL, null,
                MapArea.Geometry.label(), point(40, 50), owner);
        AreaStore.save(duplicateIds, "world:alpha", new AreaDocument(18, List.of(feature, secondFeature)));
        String duplicateJson = Files.readString(duplicateIds, StandardCharsets.UTF_8)
                .replace(secondFeature.id().toString(), feature.id().toString());
        Files.writeString(duplicateIds, duplicateJson, StandardCharsets.UTF_8);
        byte[] duplicateBytes = Files.readAllBytes(duplicateIds);
        check(!AreaStore.load(duplicateIds, "world:alpha").valid(), "duplicate feature IDs are rejected");
        bytesEqual(duplicateBytes, Files.readAllBytes(duplicateIds), "duplicate-ID data is never silently repaired or rewritten");

        Path root = output.toAbsolutePath().normalize();
        check(store.toAbsolutePath().normalize().startsWith(root), "all filesystem fixtures remain inside the task-owned temp directory");
    }

    private static void geometryReviewRegressions() {
        ArrayList<String> failures = new ArrayList<>();
        regression("union preserves all 63 holes in 66 valid rings", failures, () -> {
            MapArea.Geometry source = polygonWithManyHoles();
            MapArea.Geometry combined = AreaGeometryOps.union(List.of(source));
            int holes = combined.polygons().stream().mapToInt(polygon -> polygon.holes().size()).sum();
            if (holes != 63) throw new AssertionError("expected 63 holes, got " + holes);
        });

        regression("anchor finds a label point in a thin band around a large hole", failures, () -> {
            MapArea.Geometry narrowBand = MapArea.Geometry.polygon(List.of(new MapArea.Polygon(
                    ring(0, 0, 1000, 1000), List.of(ring(1, 1, 999, 999)))));
            MapArea.Point anchor = AreaGeometryOps.anchor(narrowBand);
            if (!AreaGeometryOps.contains(narrowBand, anchor)) {
                throw new AssertionError("anchor lies outside the polygon fill");
            }
        });

        regression("moving a boundary vertex also splits and moves its unsplit sibling edge", failures, () -> {
            UUID owner = UUID.randomUUID();
            MapArea country = area("Boundary", MapArea.Kind.COUNTRY, MapArea.Scope.SHARED, null,
                    MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(0, 0, 20, 10), List.of()))),
                    point(10, 5), owner);
            MapArea west = area("West", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                    MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                            point(0, 0), point(10, 0), point(10, 10), point(0, 10)), List.of()))),
                    point(5, 5), owner);
            MapArea east = area("East", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                    MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                            point(10, 0), point(20, 0), point(20, 10), point(10, 10), point(10, 5)), List.of()))),
                    point(15, 5), owner);
            AreaDocument edited = AreaGeometryOps.movePoint(new AreaDocument(1, List.of(country, west, east)),
                    east.id(), point(10, 5), point(9, 5));
            MapArea.Geometry westAfter = edited.find(west.id()).orElseThrow().geometry();
            MapArea.Geometry eastAfter = edited.find(east.id()).orElseThrow().geometry();
            if (AreaGeometryOps.contains(westAfter, point(9.5, 5))
                    || !AreaGeometryOps.contains(eastAfter, point(9.5, 5))) {
                throw new AssertionError("sibling border was not canonicalized before moving; edited regions overlap");
            }
        });

        regression("inserting on a split edge also updates an unsplit sibling edge", failures, () -> {
            UUID owner = UUID.randomUUID();
            MapArea country = area("Insertion boundary", MapArea.Kind.COUNTRY, MapArea.Scope.SHARED, null,
                    MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring(0, 0, 20, 10), List.of()))),
                    point(10, 5), owner);
            MapArea west = area("West", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                    MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                            point(0, 0), point(10, 0), point(10, 5), point(10, 10), point(0, 10)), List.of()))),
                    point(5, 5), owner);
            MapArea east = area("East", MapArea.Kind.REGION, MapArea.Scope.SHARED, country.id(),
                    MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(
                            point(10, 0), point(20, 0), point(20, 10), point(10, 10)), List.of()))),
                    point(15, 5), owner);
            AreaDocument edited = AreaGeometryOps.insertPoint(new AreaDocument(1, List.of(country, west, east)),
                    west.id(), point(10, 0), point(10, 5), point(10, 2));
            MapArea movedEast = edited.find(east.id()).orElseThrow();
            if (!hasOuterVertex(movedEast, point(10, 2)) || !hasOuterVertex(movedEast, point(10, 5))) {
                throw new AssertionError("unsplit sibling edge was not split at both the shared endpoint and insertion point");
            }
        });

        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    private static MapArea.Geometry polygonWithManyHoles() {
        ArrayList<MapArea.Polygon> polygons = new ArrayList<>();
        for (int component = 0; component < 3; component++) {
            double minX = component * 200.0;
            ArrayList<List<MapArea.Point>> holes = new ArrayList<>();
            for (int hole = 0; hole < 21; hole++) {
                double minHoleX = minX + 10 + (hole % 7) * 12;
                double minHoleZ = 10 + (hole / 7) * 20;
                holes.add(ring(minHoleX, minHoleZ, minHoleX + 2, minHoleZ + 2));
            }
            polygons.add(new MapArea.Polygon(ring(minX, 0, minX + 100, 100), holes));
        }
        return MapArea.Geometry.polygon(polygons);
    }

    private static void regression(String description, List<String> failures, Runnable operation) {
        try {
            operation.run();
            assertions++;
        } catch (RuntimeException | AssertionError failure) {
            failures.add(description + " failed: " + failure);
        }
    }

    private static boolean hasOuterVertex(MapArea area, MapArea.Point expected) {
        return area.geometry().polygons().stream()
                .anyMatch(polygon -> polygon.outer().contains(expected));
    }

    private static double signedOuterArea(MapArea.Geometry geometry) {
        return geometry.polygons().stream().mapToDouble(polygon -> signedArea(polygon.outer())).sum();
    }

    private static double signedAreaWithHoles(MapArea.Geometry geometry) {
        return geometry.polygons().stream().mapToDouble(polygon -> {
            double result = signedArea(polygon.outer());
            for (List<MapArea.Point> hole : polygon.holes()) result -= Math.abs(signedArea(hole));
            return result;
        }).sum();
    }

    private static double signedArea(List<MapArea.Point> points) {
        double area = 0;
        for (int index = 0; index < points.size(); index++) {
            MapArea.Point current = points.get(index);
            MapArea.Point next = points.get((index + 1) % points.size());
            area += current.x() * next.z() - next.x() * current.z();
        }
        return Math.abs(area / 2.0);
    }

    private static List<MapArea.Point> ring(double minX, double minZ, double maxX, double maxZ) {
        return List.of(point(minX, minZ), point(maxX, minZ), point(maxX, maxZ), point(minX, maxZ));
    }

    private static MapArea.Point point(double x, double z) { return new MapArea.Point(x, z); }

    private static MapArea area(String name, MapArea.Kind kind, MapArea.Scope scope, UUID parent,
                                MapArea.Geometry geometry, MapArea.Point label, UUID owner) {
        return new MapArea(UUID.randomUUID(), "minecraft:overworld", name, kind, scope, owner,
                "Tester", parent, 1, geometry, label, MapArea.Style.defaults(0xFFB0D0FF));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(java.util.Objects.equals(expected, actual), message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void equal(long expected, long actual, String message) {
        check(expected == actual, message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void close(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1.0e-6, message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void bytesEqual(byte[] expected, byte[] actual, String message) {
        check(java.util.Arrays.equals(expected, actual), message);
    }

    private static void throwsType(Class<? extends Throwable> expected, CheckedRunnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError(message + " (expected " + expected.getSimpleName() + ", got " + actual + ")", actual);
        }
        throw new AssertionError(message + " (expected " + expected.getSimpleName() + ")");
    }

    @FunctionalInterface
    private interface CheckedRunnable { void run() throws Exception; }
}
