// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TransitStationReaderTest {
    private static final TransitStationReader.PositionReader POSITIONS = value -> (TransitStation.Position) value;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        createUsesSynchronizedEdgeDimensionAndRealBlock();
        createRejectsInvalidStations();
        createGroupsNearbyStationsAndAppliesInclusiveRadius();
        createUsesThreeDimensionalDistanceAndExactGroupKeys();
        createGroupsTransitiveComponentsDeterministically();
        createSpatialIndexHandlesDenseSparseAndExtremeCoordinates();
        createCentersComponentsAndDeduplicatesStationIds();
        createRefreshesCentersAndIdentitiesAfterChanges();
        createGroupingRadiusCanBeConfigured();
        createZeroRadiusDeduplicatesIdentitiesWithoutGrouping();
        createGroupingRadiusRejectsInvalidValuesAndAvoidsOverflow();
        mtrAreasIgnoreCreateGroupingRadius();
        mtrNamesRespectLanguageSeparators();
        mtr3RequiresPlatformInAreaAndRealRail();
        mtr4RequiresPlatformInAreaAndRealRail();
        mtr4ValidatesRailsWithSynchronizedData();
        centerRejectsEmptyAndHandlesOneOrTwoPlatforms();
        centerHandlesCollinearPlatforms();
        centerFindsGeometricMedianInsteadOfMean();
        mtr3GroupsPlatformsByStationArea();
        mtr4NoArgumentApiGroupsPlatformsByStationArea();
        mtr4DataArgumentApiGroupsPlatformsByStationArea();
        catalogUsesStableStationAreaIdentity();
        unsupportedApiDoesNotInventStationPositions();
        System.out.println("Transit station checks passed (" + assertions + " assertions).");
    }

    private static void createUsesSynchronizedEdgeDimensionAndRealBlock() throws Exception {
        CreateStation station = new CreateStation("Central", "overworld", new TransitStation.Position(42, 70, 93));
        station.blockDimension = null; // Create's client packet omits this field in both 0.5 and 6.
        List<TransitStation> result = TransitStationReader.readCreate(new Railway(station), "station", POSITIONS,
                (entry, dimension, block) -> true);
        check(result.size() == 1, "synchronized station exists");
        check(result.get(0).dimension().equals("overworld"), "edge node dimension is used");
        check(result.get(0).position().equals(station.blockPosition), "station block coordinate used");
        check(result.get(0).waypointIdentity().equals(station.id.toString()), "Create identity comes from station UUID");
    }

    private static void createRejectsInvalidStations() throws Exception {
        CreateStation unattached = new CreateStation("Unattached", "overworld", null);
        CreateStation crossDimension = new CreateStation("Bad edge", "overworld", new TransitStation.Position(1, 2, 3));
        crossDimension.edgeLocation.second = new Node("nether");
        CreateStation valid = new CreateStation("Central", "overworld", new TransitStation.Position(10, 65, 30));
        CreateStation removedBlock = new CreateStation("Central", "overworld", new TransitStation.Position(11, 65, 30));
        List<TransitStation> result = TransitStationReader.readCreate(
                new Railway(unattached, crossDimension, removedBlock, valid), "station", POSITIONS,
                (station, dimension, block) -> station != removedBlock);
        check(result.size() == 1 && result.get(0).waypointIdentity().equals(valid.id.toString()),
                "unattached, cross-dimension and removed Create stations are filtered before clustering");
        List<TransitStation> grouped = TransitStationCatalog.unique(result, Object::toString);
        check(grouped.size() == 1 && grouped.get(0).position().equals(valid.blockPosition),
                "invalid nearby stations do not shift a valid station center");
    }

    private static void createGroupsNearbyStationsAndAppliesInclusiveRadius() {
        List<TransitStation> nearby = createWaypoints(
                createWaypoint(1, "Central", "overworld", 0, 64, 0),
                createWaypoint(2, "Central", "overworld", 40, 64, 0));
        TransitStation pair = only(nearby, "near same-name Create stations form one cluster");
        check(pair.position().equals(new TransitStation.Position(20, 64, 0)),
                "near Create stations use their shared geometric center");
        check(pair.waypointIdentity().equals(uuid(1).toString()), "cluster representative is its smallest station UUID");

        TransitStation atLimit = only(createWaypoints(
                createWaypoint(3, "Central", "overworld", 0, 64, 0),
                createWaypoint(4, "Central", "overworld", 50, 64, 0)),
                "stations exactly 50 blocks apart remain connected");
        check(atLimit.position().equals(new TransitStation.Position(25, 64, 0)),
                "inclusive 50-block boundary contributes both station blocks to the center");

        List<TransitStation> beyondLimit = createWaypoints(
                createWaypoint(5, "Central", "overworld", 0, 64, 0),
                createWaypoint(6, "Central", "overworld", 51, 64, 0));
        check(beyondLimit.size() == 2, "stations 51 blocks apart remain separate components");
        check(findByIdentity(beyondLimit, uuid(5)).position().x() == 0
                        && findByIdentity(beyondLimit, uuid(6)).position().x() == 51,
                "separate out-of-radius components keep their own positions");
    }

    private static void createUsesThreeDimensionalDistanceAndExactGroupKeys() {
        TransitStation diagonal = only(createWaypoints(
                createWaypoint(10, "Central", "overworld", 0, 0, 0),
                createWaypoint(11, "Central", "overworld", 30, 0, 40)),
                "horizontal diagonal distance uses Euclidean length");
        check(diagonal.position().equals(new TransitStation.Position(15, 0, 20)),
                "horizontal diagonal pair at distance 50 clusters at its midpoint");
        check(createWaypoints(createWaypoint(12, "Central", "overworld", 0, 0, 0),
                        createWaypoint(13, "Central", "overworld", 30, 30, 30)).size() == 2,
                "three-dimensional diagonal distance over 50 blocks keeps stations separate");

        TransitStation verticalLimit = only(createWaypoints(
                createWaypoint(14, "Central", "overworld", 10, 0, 10),
                createWaypoint(15, "Central", "overworld", 10, 50, 10)),
                "vertical difference of exactly 50 blocks is included");
        check(verticalLimit.position().equals(new TransitStation.Position(10, 25, 10)),
                "vertical Create station pairs use a three-dimensional center");
        check(createWaypoints(createWaypoint(16, "Central", "overworld", 10, 0, 10),
                        createWaypoint(17, "Central", "overworld", 10, 51, 10)).size() == 2,
                "vertical difference over 50 blocks keeps stations separate");

        List<TransitStation> boundaries = createWaypoints(
                createWaypoint(20, "Central", "overworld", 0, 64, 0),
                createWaypoint(21, "Central", "nether", 0, 64, 0),
                createWaypoint(22, "central", "overworld", 0, 64, 0),
                createWaypoint(23, "Central", new StringBuilder("overworld"), 40, 64, 0));
        check(boundaries.size() == 3,
                "Create groups require exact station name and canonical dimension string");
        TransitStation canonicalDimensionPair = boundaries.stream()
                .filter(station -> station.name().equals("Central") && station.dimension().toString().equals("overworld"))
                .findFirst().orElseThrow();
        check(canonicalDimensionPair.position().equals(new TransitStation.Position(20, 64, 0)),
                "dimension objects with the same canonical string share a grouping key");

        List<TransitStation> remote = createWaypoints(
                createWaypoint(30, "Remote", "overworld", 0, 64, 0),
                createWaypoint(31, "Remote", "overworld", 100, 64, 0),
                createWaypoint(32, "Remote", "overworld", 110, 64, 0));
        check(remote.size() == 2, "remote same-name stations form separate connected components");
        check(findByIdentity(remote, uuid(30)).position().equals(new TransitStation.Position(0, 64, 0))
                        && findByIdentity(remote, uuid(31)).position().equals(new TransitStation.Position(105, 64, 0)),
                "remote components each calculate their own center");
    }

    private static void createGroupsTransitiveComponentsDeterministically() {
        TransitStation a = createWaypoint(40, "Central", "overworld", 0, 64, 0);
        TransitStation b = createWaypoint(41, "Central", "overworld", 40, 64, 0);
        TransitStation c = createWaypoint(42, "Central", "overworld", 80, 64, 0);
        TransitStation forward = only(createWaypoints(a, b, c), "connected Create neighbors form a transitive component");
        TransitStation reversed = only(createWaypoints(c, a, b), "component membership is independent of input order");
        check(forward.position().equals(new TransitStation.Position(40, 64, 0)),
                "0/40/80 transitive chain centers at its middle station");
        check(forward.equals(reversed), "transitive component representative and center are deterministic");
        check(forward.waypointIdentity().equals(uuid(40).toString()),
                "transitive component uses the lexicographically smallest station identity");
    }

    private static void createSpatialIndexHandlesDenseSparseAndExtremeCoordinates() {
        List<TransitStation> dense = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) dense.add(createWaypoint(20_000 + i, "Dense", "overworld", 7, -12, 19));
        TransitStation denseCenter = only(createWaypoints(dense.toArray(TransitStation[]::new)),
                "dense same-cell stations are emitted as one component");
        check(denseCenter.position().equals(new TransitStation.Position(7, -12, 19)),
                "a dense spatial bucket retains all coincident station positions");

        List<TransitStation> sparse = new ArrayList<>();
        for (int i = 0; i < 100; i++) sparse.add(createWaypoint(30_000 + i, "Sparse", "overworld", i * 100, 0, -i * 100));
        check(createWaypoints(sparse.toArray(TransitStation[]::new)).size() == 100,
                "spatial search keeps distant same-name stations in separate components");

        TransitStation edge = only(createWaypoints(
                createWaypoint(40_001, "Edge", "overworld", Integer.MIN_VALUE, 0, 0),
                createWaypoint(40_002, "Edge", "overworld", Integer.MIN_VALUE + 50, 0, 0)),
                "negative extreme-coordinate cell boundary remains inclusive");
        check(edge.position().equals(new TransitStation.Position(Integer.MIN_VALUE + 25, 0, 0)),
                "negative extreme-coordinate midpoint is overflow safe");
        check(createWaypoints(createWaypoint(40_003, "Extreme", "overworld", Integer.MIN_VALUE, 0, 0),
                        createWaypoint(40_004, "Extreme", "overworld", Integer.MAX_VALUE, 0, 0)).size() == 2,
                "opposite integer extremes do not overflow into a false spatial neighbor");
    }

    private static void createCentersComponentsAndDeduplicatesStationIds() throws Exception {
        TransitStation median = only(createWaypoints(
                createWaypoint(50, "Triangle", "overworld", 0, 0, 0),
                createWaypoint(51, "Triangle", "overworld", 12, 0, 0),
                createWaypoint(52, "Triangle", "overworld", 0, 0, 12)),
                "nearby non-collinear Create stations form one component");
        check(median.position().equals(new TransitStation.Position(3, 0, 3)),
                "Create component uses the geometric median instead of the arithmetic mean");

        UUID firstId = uuid(60);
        CreateStation first = new CreateStation(firstId, "Central", "overworld", new TransitStation.Position(0, 64, 0));
        CreateStation repeatedGraphRecord = new CreateStation(firstId, "Central", "overworld",
                new TransitStation.Position(0, 64, 0));
        CreateStation second = new CreateStation(uuid(61), "Central", "overworld", new TransitStation.Position(10, 64, 0));
        CreateStation third = new CreateStation(uuid(62), "Central", "overworld", new TransitStation.Position(30, 64, 0));
        List<TransitStation> raw = TransitStationReader.readCreate(new Railway(first, repeatedGraphRecord, second, third),
                "station", POSITIONS, (station, dimension, block) -> true);
        check(raw.size() == 4, "Create reader supplies repeated graph records for identity deduplication");
        TransitStation deduplicated = only(TransitStationCatalog.unique(raw, Object::toString),
                "duplicate upstream UUIDs do not become extra stations");
        check(deduplicated.position().equals(new TransitStation.Position(10, 64, 0)),
                "duplicate UUID does not add weight to the component center");
        check(deduplicated.waypointIdentity().equals(firstId.toString()),
                "deduplicated component retains the smallest upstream UUID");
    }

    private static void createRefreshesCentersAndIdentitiesAfterChanges() {
        TransitStation first = createWaypoint(70, "Central", "overworld", 0, 64, 0);
        TransitStation middle = createWaypoint(71, "Central", "overworld", 40, 64, 0);
        TransitStation last = createWaypoint(72, "Central", "overworld", 80, 64, 0);
        TransitStation original = only(createWaypoints(first, middle, last), "initial connected component exists");
        UUID originalMarkerId = TransitStationCatalog.markerId(original, "overworld");

        TransitStation renamedFirst = createWaypoint(70, "Renamed Central", "overworld", 0, 64, 0);
        TransitStation movedMiddle = createWaypoint(71, "Renamed Central", "overworld", 45, 64, 0);
        TransitStation movedLast = createWaypoint(72, "Renamed Central", "overworld", 90, 64, 0);
        TransitStation moved = only(createWaypoints(movedLast, renamedFirst, movedMiddle),
                "renamed and moved stations remain one component");
        check(moved.position().equals(new TransitStation.Position(45, 64, 0)),
                "next snapshot recalculates the center after stations move");
        check(moved.name().equals("Renamed Central"), "next snapshot updates the component label after rename");
        check(moved.waypointIdentity().equals(original.waypointIdentity())
                        && TransitStationCatalog.markerId(moved, "overworld").equals(originalMarkerId),
                "representative station keeps its marker ID across ordering, movement, and rename");

        List<TransitStation> split = createWaypoints(renamedFirst, movedLast);
        check(split.size() == 2, "deleting a connecting station refreshes into two separated components");
        check(findByIdentity(split, uuid(70)).position().equals(renamedFirst.position())
                        && findByIdentity(split, uuid(72)).position().equals(movedLast.position()),
                "split components recalculate centers from their remaining members");

        TransitStation rejoined = only(createWaypoints(renamedFirst, movedMiddle, movedLast),
                "restoring a connecting station rejoins the components in a fresh snapshot");
        check(rejoined.position().equals(moved.position())
                        && rejoined.waypointIdentity().equals(original.waypointIdentity()),
                "rejoined component restores its geometric center and representative identity");

        TransitStation withoutRepresentative = only(createWaypoints(movedMiddle, movedLast),
                "component remains after its former representative is deleted");
        check(!TransitStationCatalog.markerId(withoutRepresentative, "overworld").equals(originalMarkerId)
                        && withoutRepresentative.waypointIdentity().equals(uuid(71).toString()),
                "marker identity changes when membership removes the representative station");
    }

    private static void createGroupingRadiusCanBeConfigured() {
        check(TransitStationCatalog.unique(List.of(
                        createWaypoint(79_001, "Odd Radius", "overworld", -50, 64, -50),
                        createWaypoint(79_002, "Odd Radius", "overworld", -25, 64, -50)), Object::toString, 25).size() == 1,
                "negative-coordinate stations exactly 25 blocks apart connect across two spatial cells");
        List<TransitStation> sameInput = List.of(
                createWaypoint(80, "Central", "overworld", 0, 64, 0),
                createWaypoint(81, "Central", "overworld", 40, 64, 0));
        check(TransitStationCatalog.unique(sameInput, Object::toString, 25).size() == 2,
                "smaller configured radius keeps stations 40 blocks apart separate");
        List<TransitStation> groupedSnapshot = TransitStationCatalog.unique(sameInput, Object::toString, 50);
        TransitStation groupedAtDefault = only(groupedSnapshot, "default radius groups the same 40-block pair");
        check(groupedAtDefault.position().equals(new TransitStation.Position(20, 64, 0)),
                "recomputing the same input with a larger radius calculates its shared center");
        List<TransitStation> sameSnapshot = TransitStationCatalog.unique(sameInput, Object::toString, 50);
        check(sameSnapshot == groupedSnapshot, "an unchanged Create snapshot reuses its immutable clustered result");
        List<TransitStation> renamedInput = sameInput.stream().map(station -> new TransitStation(station.source(),
                station.dimension(), station.waypointIdentity(), "Renamed", station.position())).toList();
        List<TransitStation> renamedSnapshot = TransitStationCatalog.unique(renamedInput, Object::toString, 50);
        check(renamedSnapshot != groupedSnapshot && renamedSnapshot.get(0).name().equals("Renamed"),
                "a label change invalidates the snapshot and cannot return stale station text");

        check(TransitStationCatalog.unique(List.of(
                        createWaypoint(82, "Central", "overworld", 0, 64, 0),
                        createWaypoint(83, "Central", "overworld", 10, 64, 0)), Object::toString, 10).size() == 1,
                "smaller configurable radius includes its exact boundary");
        TransitStation atLargerLimit = only(TransitStationCatalog.unique(List.of(
                        createWaypoint(84, "Central", "overworld", 0, 64, 0),
                        createWaypoint(85, "Central", "overworld", 100, 64, 0)), Object::toString, 100),
                "larger configurable radius includes its exact boundary");
        check(atLargerLimit.position().equals(new TransitStation.Position(50, 64, 0)),
                "larger radius places one waypoint at the component center");
        check(TransitStationCatalog.unique(List.of(
                        createWaypoint(86, "Central", "overworld", 0, 64, 0),
                        createWaypoint(87, "Central", "overworld", 101, 64, 0)), Object::toString, 100).size() == 2,
                "stations beyond the configured larger radius remain separate");
    }

    private static void createZeroRadiusDeduplicatesIdentitiesWithoutGrouping() {
        TransitStation first = createWaypoint(90, "Central", "overworld", 10, 64, 10);
        TransitStation repeatedId = createWaypoint(90, "Central", "overworld", 10, 64, 10);
        TransitStation distinctIdSamePosition = createWaypoint(91, "Central", "overworld", 10, 64, 10);
        List<TransitStation> result = TransitStationCatalog.unique(
                List.of(first, repeatedId, distinctIdSamePosition), Object::toString, 0);
        check(result.size() == 2, "zero radius disables Create grouping but still deduplicates upstream UUIDs");
        check(result.stream().allMatch(station -> station.position().equals(first.position())),
                "zero-radius distinct stations remain independent even at coincident coordinates");
        check(result.stream().map(TransitStation::waypointIdentity).distinct().count() == 2,
                "zero-radius results preserve both distinct upstream UUIDs");
    }

    private static void createGroupingRadiusRejectsInvalidValuesAndAvoidsOverflow() {
        try {
            TransitStationCatalog.unique(List.of(createWaypoint(100, "Central", "overworld", 0, 64, 0)),
                    Object::toString, -1);
            throw new AssertionError("negative Create grouping radius must be rejected");
        } catch (IllegalArgumentException expected) { check(true, "negative Create grouping radius is rejected"); }

        TransitStation atMaximum = only(TransitStationCatalog.unique(List.of(
                        createWaypoint(101, "Large", "overworld", 0, 64, 0),
                        createWaypoint(102, "Large", "overworld", 1_000_000, 64, 0)),
                Object::toString, 1_000_000), "maximum radius uses overflow-safe 3D distance arithmetic");
        check(atMaximum.position().equals(new TransitStation.Position(500_000, 64, 0)),
                "maximum-radius boundary stations share their exact midpoint");
        check(TransitStationCatalog.unique(List.of(
                        createWaypoint(103, "Large", "overworld", 0, 64, 0),
                        createWaypoint(104, "Large", "overworld", 1_000_001, 64, 0)),
                Object::toString, 1_000_000).size() == 2,
                "maximum-radius arithmetic still separates the first distance beyond its boundary");
    }

    private static void mtrAreasIgnoreCreateGroupingRadius() {
        TransitStation firstArea = new TransitStation(TransitStation.Source.MTR, "overworld", "area-501",
                "Central", new TransitStation.Position(0, 64, 0));
        TransitStation secondArea = new TransitStation(TransitStation.Source.MTR, "overworld", "area-502",
                "Central", new TransitStation.Position(0, 64, 0));
        for (int radius : new int[]{0, 1_000_000}) {
            List<TransitStation> result = TransitStationCatalog.unique(
                    List.of(firstArea, secondArea), Object::toString, radius);
            check(result.size() == 2, "MTR area IDs remain separate at Create grouping radius " + radius);
            check(!TransitStationCatalog.markerId(result.get(0), "overworld")
                            .equals(TransitStationCatalog.markerId(result.get(1), "overworld")),
                    "MTR marker IDs remain independent at Create grouping radius " + radius);
        }
    }

    private static void mtrNamesRespectLanguageSeparators() {
        check(TransitStationReader.formatMtrName("上南|Kaminami||private").equals("上南（Kaminami）"),
                "MTR display names combine language aliases and hide metadata after the double separator");
        check(TransitStationReader.formatMtrName(" Foo | Foo | Bar ||private|hidden").equals("Foo（Bar）"),
                "MTR aliases are trimmed, empty values removed, and duplicates omitted");
        check(TransitStationReader.formatMtrName("Main||hidden").equals("Main"),
                "MTR names without aliases omit parentheses");
        check(TransitStationReader.formatMtrName("||private").isEmpty(),
                "metadata-only MTR names do not produce a visible label");
    }

    private static TransitStation createWaypoint(long id, String name, Object dimension, int x, int y, int z) {
        return new TransitStation(TransitStation.Source.CREATE, dimension, uuid(id).toString(), name,
                new TransitStation.Position(x, y, z));
    }

    private static List<TransitStation> createWaypoints(TransitStation... stations) {
        return TransitStationCatalog.unique(List.of(stations), Object::toString);
    }

    private static TransitStation only(List<TransitStation> stations, String message) {
        check(stations.size() == 1, message);
        return stations.get(0);
    }

    private static TransitStation findByIdentity(List<TransitStation> stations, UUID identity) {
        return stations.stream().filter(station -> station.waypointIdentity().equals(identity.toString()))
                .findFirst().orElseThrow(() -> new AssertionError("missing station identity " + identity));
    }

    private static UUID uuid(long id) {
        return new UUID(0, id);
    }

    private static void mtr3RequiresPlatformInAreaAndRealRail() throws Exception {
        Mtr3.PLATFORMS.clear();
        Mtr3.DATA_CACHE.platformIdToStation.clear();
        Area3 area = new Area3("Central");
        Platform3 valid = new Platform3(1, new TransitStation.Position(20, 65, 30), false);
        Platform3 invalidRail = new Platform3(2, new TransitStation.Position(21, 65, 30), true);
        Platform3 outside = new Platform3(3, new TransitStation.Position(300, 65, 30), false);
        Platform3 unassigned = new Platform3(4, new TransitStation.Position(22, 65, 30), false);
        Mtr3.PLATFORMS.addAll(List.of(valid, invalidRail, outside, unassigned));
        for (Platform3 platform : List.of(valid, invalidRail, outside)) Mtr3.DATA_CACHE.platformIdToStation.put(platform.id, area);
        List<TransitStation> result = TransitStationReader.readMtr3(Mtr3.class, "nether", POSITIONS);
        check(result.size() == 1, "MTR3 rejects missing rails, out-of-area and unassigned platforms");
        check(result.get(0).position().equals(valid.position), "MTR3 uses actual platform node midpoint");
        check(result.get(0).dimension().equals("nether"), "MTR3 uses active client dimension");
        check(result.get(0).waypointIdentity().equals(Long.toString(area.id)), "MTR3 identity comes from station area id");
    }

    private static void mtr4RequiresPlatformInAreaAndRealRail() throws Exception {
        Area4 area = new Area4("Central");
        Platform4 valid = new Platform4(area, new TransitStation.Position(20, 65, 30), false);
        Mtr4 data = new Mtr4(List.of(valid,
                new Platform4(area, new TransitStation.Position(21, 65, 30), true),
                new Platform4(area, new TransitStation.Position(20, -50, 30), false),
                new Platform4(null, new TransitStation.Position(22, 65, 30), false)));
        List<TransitStation> result = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(result.size() == 1, "MTR4 rejects missing rails, vertical area mismatch and unassigned platforms");
        check(result.get(0).position().equals(valid.position), "MTR4 uses actual platform node midpoint");
        check(result.get(0).waypointIdentity().equals(Long.toString(area.id)), "MTR4 identity comes from station area id");
    }

    private static void centerRejectsEmptyAndHandlesOneOrTwoPlatforms() {
        try {
            TransitStationCenter.center(List.of());
            throw new AssertionError("empty station area must not have a center");
        } catch (IllegalArgumentException expected) { check(true, "empty station area is rejected"); }

        TransitStation.Position single = new TransitStation.Position(-13, 72, 29);
        check(TransitStationCenter.center(List.of(single)).equals(single), "one platform centers on its midpoint");
        check(TransitStationCenter.center(List.of(new TransitStation.Position(-8, -10, -4),
                        new TransitStation.Position(0, 0, 0)))
                        .equals(new TransitStation.Position(-4, -5, -2)),
                "two platform midpoints center at their negative-coordinate midpoint");
        check(TransitStationCenter.center(List.of(new TransitStation.Position(-1_000_000_000, -1_000_000_000, 1_000_000_000),
                        new TransitStation.Position(1_000_000_000, 1_000_000_000, -1_000_000_000)))
                        .equals(new TransitStation.Position(0, 0, 0)),
                "two platform midpoints center safely at large coordinates");
    }

    private static void centerHandlesCollinearPlatforms() {
        check(TransitStationCenter.center(List.of(position(0, 0), position(4, 0), position(20, 0)))
                        .equals(position(4, 0)),
                "odd collinear platform counts center at the middle point");
        check(TransitStationCenter.center(List.of(position(0, 0), position(4, 0), position(20, 0), position(40, 0)))
                        .equals(position(12, 0)),
                "even collinear platform counts center at the middle pair midpoint");
        TransitStation.Position repeated = position(7, -3);
        check(TransitStationCenter.center(List.of(repeated, repeated, position(19, -3))).equals(repeated),
                "coincident platform midpoints retain their weight in the geometric median");
    }

    private static void centerFindsGeometricMedianInsteadOfMean() {
        centerHandlesDifficultLayouts();
        List<TransitStation.Position> rightTriangle = List.of(
                new TransitStation.Position(0, 65, 0), new TransitStation.Position(12, 65, 0),
                new TransitStation.Position(0, 65, 12));
        TransitStation.Position center = TransitStationCenter.center(rightTriangle);
        check(center.equals(new TransitStation.Position(3, 65, 3)),
                "non-collinear platforms use the geometric median rather than arithmetic mean");
        check(TransitStationCenter.center(List.of(rightTriangle.get(2), rightTriangle.get(0), rightTriangle.get(1))).equals(center),
                "platform ordering does not change the rounded geometric median");

        List<TransitStation.Position> obtuse = List.of(
                new TransitStation.Position(0, 65, 0), new TransitStation.Position(4, 65, 0),
                new TransitStation.Position(2, 65, 1));
        check(TransitStationCenter.center(obtuse).equals(new TransitStation.Position(2, 65, 1)),
                "a triangle with an angle over 120 degrees centers at that vertex");
    }

    private static void centerHandlesDifficultLayouts() {
        var cusp = TransitStationCenter.center(List.of(position(-12_000, 10), position(-6000, 4),
                position(12_000, -10), position(54_000, -36)));
        check(cusp.equals(position(0, 0)), "near-input stagnation continues to the antipodal-ray median: " + cusp);
        check(TransitStationCenter.center(List.of(position(-200, -1), position(-200, 1),
                position(200, -10), position(200, 10))).equals(position(-164, 0)),
                "narrow platform layout converges to its analytic median at block precision");
        check(TransitStationCenter.center(List.of(position(-201, -199), position(-199, -201),
                position(190, 210), position(210, 190))).equals(position(-164, -164)),
                "rotating a narrow layout preserves the geometric median");
        check(TransitStationCenter.center(List.of(position(100, 1), position(-200, -2),
                position(300, -3), position(-400, 4))).equals(position(0, 0)),
                "asymmetric radii on opposite rays converge to their common origin");
        check(TransitStationCenter.center(List.of(position(100, 101), position(-200, -202),
                position(300, 297), position(-400, -396))).equals(position(0, 0)),
                "rotated asymmetric narrow layout has the same analytic median");
        check(TransitStationCenter.center(List.of(position(-10_000, 0), position(10_000, 0), position(0, 5773)))
                .equals(position(0, 5773)), "near-120-degree obtuse triangle selects its exact vertex");
        check(TransitStationCenter.center(List.of(position(-10_000, 0), position(10_000, 0), position(0, 5774)))
                .equals(position(0, 5774)), "near-120-degree acute triangle converges before rounding");
        var coincidentMean = List.of(position(0, 0), position(100, 0), position(100, 100),
                position(100, -100), position(-300, 0));
        check(TransitStationCenter.center(coincidentMean).equals(position(42, 0)),
                "an input point at the starting mean is not automatically the median");
        List<TransitStation.Position> weightedMean = new ArrayList<>(coincidentMean);
        weightedMean.add(position(0, 0));
        check(TransitStationCenter.center(weightedMean).equals(position(0, 0)),
                "coincident platform multiplicity changes the median subgradient correctly");
        check(TransitStationCenter.center(List.of(position(0, 0), position(-500, 0), position(100, 10),
                position(100, 10), position(100, -10), position(100, -10))).equals(position(94, 0)),
                "line search crosses a nonoptimal input-point cusp");
        check(TransitStationCenter.center(List.of(new TransitStation.Position(0, 0, 0),
                new TransitStation.Position(10, 0, 0), new TransitStation.Position(0, 10, 0),
                new TransitStation.Position(0, 0, 10))).equals(new TransitStation.Position(2, 2, 2)),
                "station center minimizes full three-dimensional distances");
        var translated = List.of(position(29_990_000 - 200, -29_990_000 - 1),
                position(29_990_000 - 200, -29_990_000 + 1), position(29_990_000 + 200, -29_990_000 - 10),
                position(29_990_000 + 200, -29_990_000 + 10));
        check(TransitStationCenter.center(translated).equals(position(29_990_000 - 164, -29_990_000)),
                "translation near Minecraft world bounds preserves the station center");
    }

    private static TransitStation.Position position(int x, int z) {
        return new TransitStation.Position(x, 65, z);
    }

    private static void mtr3GroupsPlatformsByStationArea() throws Exception {
        Mtr3.PLATFORMS.clear();
        Mtr3.DATA_CACHE.platformIdToStation.clear();
        Area3 area = new Area3("Central", 101);
        Platform3 first = new Platform3(11, position(10, 30), false);
        Platform3 second = new Platform3(12, position(30, 30), false);
        Mtr3.PLATFORMS.addAll(List.of(first, second, first));
        Mtr3.DATA_CACHE.platformIdToStation.put(first.id, area);
        Mtr3.DATA_CACHE.platformIdToStation.put(second.id, area);

        List<TransitStation> original = TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS);
        check(original.size() == 1, "MTR3 emits one waypoint for two platforms in one station area");
        check(TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS).get(0) == original.get(0),
                "unchanged MTR platform membership reuses its immutable computed station center");
        TransitStation originalStation = original.get(0);
        check(originalStation.position().equals(position(20, 30)), "MTR3 places the station waypoint at the platform midpoint center");
        check(originalStation.waypointIdentity().equals(Long.toString(area.id)), "MTR3 waypoint identity uses station area ID");
        UUID markerId = TransitStationCatalog.markerId(originalStation, "overworld");

        Area3 renamedArea = new Area3("Renamed Central", area.id);
        Platform3 movedFirst = new Platform3(first.id, position(14, 30), false);
        Platform3 movedSecond = new Platform3(second.id, position(34, 30), false);
        Mtr3.PLATFORMS.clear();
        Mtr3.PLATFORMS.addAll(List.of(movedSecond, movedFirst));
        Mtr3.DATA_CACHE.platformIdToStation.put(movedFirst.id, renamedArea);
        Mtr3.DATA_CACHE.platformIdToStation.put(movedSecond.id, renamedArea);
        List<TransitStation> moved = TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS);
        check(moved.size() == 1 && moved.get(0).position().equals(position(24, 30)),
                "MTR3 reorder and platform movement recalculate one area center");
        check(moved.get(0).name().equals("Renamed Central"), "MTR3 area rename updates the waypoint label");
        check(markerId.equals(TransitStationCatalog.markerId(moved.get(0), "overworld")),
                "MTR3 station marker ID survives platform changes and area rename");

        renamedArea.maxX = 20;
        List<TransitStation> resized = TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS);
        check(resized.size() == 1 && resized.get(0).position().equals(position(14, 30)),
                "MTR3 area resize recalculates the center from eligible platforms");
        renamedArea.maxX = 40;
        Mtr3.PLATFORMS.remove(movedFirst);
        List<TransitStation> deleted = TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS);
        check(deleted.size() == 1 && deleted.get(0).position().equals(movedSecond.position),
                "MTR3 platform deletion recenters the waypoint on the remaining platform");

        Area3 coincidentArea = new Area3("Coincident", 303);
        Platform3 coincidentFirst = new Platform3(31, position(10, 30), false);
        Platform3 coincidentSecond = new Platform3(32, position(10, 30), false);
        Platform3 distant = new Platform3(33, position(30, 30), false);
        Mtr3.PLATFORMS.clear();
        Mtr3.PLATFORMS.addAll(List.of(coincidentFirst, coincidentSecond, distant));
        for (Platform3 platform : Mtr3.PLATFORMS) Mtr3.DATA_CACHE.platformIdToStation.put(platform.id, coincidentArea);
        List<TransitStation> coincident = TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS);
        check(coincident.size() == 1 && coincident.get(0).position().equals(position(10, 30)),
                "MTR3 keeps distinct platform IDs at the same midpoint as separate weighted observations");

        Area3 sameNameFirst = new Area3("Shared Name", 401);
        Area3 sameNameSecond = new Area3("Shared Name", 402);
        Platform3 inFirst = new Platform3(41, position(10, 30), false);
        Platform3 inSecond = new Platform3(42, position(30, 30), false);
        Mtr3.PLATFORMS.clear();
        Mtr3.PLATFORMS.addAll(List.of(inFirst, inSecond));
        Mtr3.DATA_CACHE.platformIdToStation.put(inFirst.id, sameNameFirst);
        Mtr3.DATA_CACHE.platformIdToStation.put(inSecond.id, sameNameSecond);
        List<TransitStation> sameNames = TransitStationReader.readMtr3(Mtr3.class, "overworld", POSITIONS);
        check(sameNames.size() == 2, "MTR3 keeps separate station areas with the same name");
        check(!TransitStationCatalog.markerId(sameNames.get(0), "overworld")
                        .equals(TransitStationCatalog.markerId(sameNames.get(1), "overworld")),
                "MTR3 same-name station areas receive distinct marker IDs");
    }

    private static void mtr4NoArgumentApiGroupsPlatformsByStationArea() throws Exception {
        Area4 area = new Area4("Central", 102);
        Platform4 first = new Platform4(111, area, position(10, 30), false);
        Platform4 second = new Platform4(112, area, position(30, 30), false);
        Mtr4 data = new Mtr4(List.of(first, second, first));
        List<TransitStation> original = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(original.size() == 1 && original.get(0).position().equals(position(20, 30)),
                "MTR4 no-argument API deduplicates a repeated platform ID and emits one centered waypoint");
        check(original.get(0).waypointIdentity().equals(Long.toString(area.id)), "MTR4 no-argument API uses station area ID");
        UUID markerId = TransitStationCatalog.markerId(original.get(0), "overworld");

        Area4 renamedArea = new Area4("Renamed Central", area.id);
        Platform4 movedFirst = new Platform4(first.id, renamedArea, position(14, 30), false);
        Platform4 movedSecond = new Platform4(second.id, renamedArea, position(34, 30), false);
        data.platforms.clear();
        data.platforms.addAll(List.of(movedSecond, movedFirst));
        List<TransitStation> moved = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(moved.size() == 1 && moved.get(0).position().equals(position(24, 30)),
                "MTR4 no-argument API recalculates one center after platform movement and reordering");
        check(moved.get(0).name().equals("Renamed Central"), "MTR4 no-argument API updates the renamed area label");
        check(markerId.equals(TransitStationCatalog.markerId(moved.get(0), "overworld")),
                "MTR4 no-argument marker ID survives platform changes and area rename");
        renamedArea.maxX = 20;
        check(TransitStationReader.readMtr4(data, "overworld", POSITIONS).get(0).position().equals(position(14, 30)),
                "MTR4 no-argument area resize recalculates center from eligible platforms");
        renamedArea.maxX = 40;
        data.platforms.remove(movedFirst);
        List<TransitStation> deleted = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(deleted.size() == 1 && deleted.get(0).position().equals(movedSecond.position),
                "MTR4 no-argument platform deletion recenters on the remaining platform");

        Area4 coincidentArea = new Area4("Coincident", 303);
        Mtr4 coincidentData = new Mtr4(List.of(
                new Platform4(131, coincidentArea, position(10, 30), false),
                new Platform4(132, coincidentArea, position(10, 30), false),
                new Platform4(133, coincidentArea, position(30, 30), false)));
        check(TransitStationReader.readMtr4(coincidentData, "overworld", POSITIONS).get(0).position().equals(position(10, 30)),
                "MTR4 no-argument API weights distinct platform IDs at the same midpoint separately");

        assertMtr4SameNameAreas(new Mtr4(List.of(
                new Platform4(141, new Area4("Shared Name", 401), position(10, 30), false),
                new Platform4(142, new Area4("Shared Name", 402), position(30, 30), false))), "MTR4 no-argument API");
    }

    private static void mtr4DataArgumentApiGroupsPlatformsByStationArea() throws Exception {
        Area4 area = new Area4("Central", 202);
        Platform4WithData first = new Platform4WithData(211, area, position(10, 30), false);
        Platform4WithData second = new Platform4WithData(212, area, position(30, 30), false);
        Data4 data = new Data4(List.of(first, second, first));
        List<TransitStation> original = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(original.size() == 1 && original.get(0).position().equals(position(20, 30)),
                "MTR4 Data-argument API deduplicates a repeated platform ID and emits one centered waypoint");
        check(original.get(0).waypointIdentity().equals(Long.toString(area.id)), "MTR4 Data-argument API uses station area ID");
        check(first.validationData == data && second.validationData == data,
                "MTR4 Data-argument API validates all eligible rails against synchronized data");
        UUID markerId = TransitStationCatalog.markerId(original.get(0), "overworld");

        Area4 renamedArea = new Area4("Renamed Central", area.id);
        Platform4WithData movedFirst = new Platform4WithData(first.id, renamedArea, position(14, 30), false);
        Platform4WithData movedSecond = new Platform4WithData(second.id, renamedArea, position(34, 30), false);
        data.platforms.clear();
        data.platforms.addAll(List.of(movedSecond, movedFirst));
        List<TransitStation> moved = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(moved.size() == 1 && moved.get(0).position().equals(position(24, 30)),
                "MTR4 Data-argument API recalculates one center after platform movement and reordering");
        check(moved.get(0).name().equals("Renamed Central"), "MTR4 Data-argument API updates the renamed area label");
        check(markerId.equals(TransitStationCatalog.markerId(moved.get(0), "overworld")),
                "MTR4 Data-argument marker ID survives platform changes and area rename");
        renamedArea.maxX = 20;
        check(TransitStationReader.readMtr4(data, "overworld", POSITIONS).get(0).position().equals(position(14, 30)),
                "MTR4 Data-argument area resize recalculates center from eligible platforms");
        renamedArea.maxX = 40;
        data.platforms.remove(movedFirst);
        List<TransitStation> deleted = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(deleted.size() == 1 && deleted.get(0).position().equals(movedSecond.position),
                "MTR4 Data-argument platform deletion recenters on the remaining platform");

        Area4 coincidentArea = new Area4("Coincident", 303);
        Data4 coincidentData = new Data4(List.of(
                new Platform4WithData(231, coincidentArea, position(10, 30), false),
                new Platform4WithData(232, coincidentArea, position(10, 30), false),
                new Platform4WithData(233, coincidentArea, position(30, 30), false)));
        check(TransitStationReader.readMtr4(coincidentData, "overworld", POSITIONS).get(0).position().equals(position(10, 30)),
                "MTR4 Data-argument API weights distinct platform IDs at the same midpoint separately");

        assertMtr4SameNameAreas(new Data4(List.of(
                new Platform4WithData(241, new Area4("Shared Name", 401), position(10, 30), false),
                new Platform4WithData(242, new Area4("Shared Name", 402), position(30, 30), false))),
                "MTR4 Data-argument API");
    }

    private static void assertMtr4SameNameAreas(Object data, String label) throws Exception {
        List<TransitStation> stations = TransitStationReader.readMtr4(data, "overworld", POSITIONS);
        check(stations.size() == 2, label + " keeps separate station areas with the same name");
        check(!TransitStationCatalog.markerId(stations.get(0), "overworld")
                        .equals(TransitStationCatalog.markerId(stations.get(1), "overworld")),
                label + " gives same-name station areas distinct marker IDs");
    }

    private static void catalogUsesStableStationAreaIdentity() {
        TransitStation first = station(TransitStation.Source.MTR, "overworld", "area-101", "Central", 20);
        TransitStation otherArea = station(TransitStation.Source.MTR, "overworld", "area-202", "Central", 30);
        TransitStation otherDimension = station(TransitStation.Source.MTR, "nether", "area-101", "Central", 20);
        TransitStation otherMod = station(TransitStation.Source.CREATE, "overworld", "area-101", "Central", 20);
        UUID id = TransitStationCatalog.markerId(first, "overworld");
        TransitStation renamedAndMoved = station(TransitStation.Source.MTR, "overworld", "area-101", "Renamed Central", 24);
        check(id.equals(TransitStationCatalog.markerId(renamedAndMoved, "overworld")),
                "area marker ID survives platform changes and area rename");
        check(!id.equals(TransitStationCatalog.markerId(otherArea, "overworld")),
                "distinct same-name station areas receive distinct marker IDs");
        check(!id.equals(TransitStationCatalog.markerId(otherDimension, "nether")), "dimension ids remain independent");
        check(!id.equals(TransitStationCatalog.markerId(otherMod, "overworld")), "mod ids remain independent");
        List<TransitStation> unique = TransitStationCatalog.unique(List.of(first, otherArea), Object::toString);
        check(unique.size() == 2, "catalog retains separate station area IDs");
        check(TransitStationCatalog.unique(List.of(), Object::toString).isEmpty(), "removed stations leave no snapshot");
    }

    private static void mtr4ValidatesRailsWithSynchronizedData() throws Exception {
        Area4 area = new Area4("Central");
        Platform4WithData valid = new Platform4WithData(area, new TransitStation.Position(20, 65, 30), false);
        Platform4WithData invalidRail = new Platform4WithData(area, new TransitStation.Position(21, 65, 30), true);
        Platform4WithData outside = new Platform4WithData(area, new TransitStation.Position(20, -50, 30), false);
        Platform4WithData unassigned = new Platform4WithData(null, new TransitStation.Position(22, 65, 30), false);
        ClientData4 data = new ClientData4(List.of(valid, invalidRail, outside, unassigned));
        List<TransitStation> result = TransitStationReader.readMtr4(data, "nether", POSITIONS);
        check(result.size() == 1, "MTR4 Data-argument API rejects invalid, out-of-area and unassigned platforms");
        check(valid.validationData == data && invalidRail.validationData == data,
                "MTR4 rail validation receives the synchronized client Data subclass");
        check(result.get(0).position().equals(valid.position), "MTR4 Data-argument API uses the actual platform midpoint");
        check(result.get(0).dimension().equals("nether"), "MTR4 Data-argument API uses the active client dimension");
        check(result.get(0).waypointIdentity().equals(Long.toString(area.id)),
                "MTR4 Data-argument API uses the station area's stable identity");
        data.platforms.clear();
        check(TransitStationReader.readMtr4(data, "nether", POSITIONS).isEmpty(),
                "MTR4 deleted platforms disappear from the next snapshot");
    }

    private static void unsupportedApiDoesNotInventStationPositions() throws Exception {
        try {
            TransitStationReader.readMtr4(new Object(), "overworld", POSITIONS);
            throw new AssertionError("Unknown API must fail safely for the caller");
        } catch (ReflectiveOperationException expected) { check(true, "unknown API has no fabricated fallback"); }
    }

    private static TransitStation station(TransitStation.Source source, String dimension, String identity, String name, int x) {
        return new TransitStation(source, dimension, identity, name, new TransitStation.Position(x, 65, 30));
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    // Public contracts mirror the upstream 0.5/6 and MTR3/4 APIs; no optional mod or Minecraft is needed.
    public static final class Railway {
        public final Map<UUID, Graph> trackNetworks = new HashMap<>();
        Railway(CreateStation... stations) { trackNetworks.put(UUID.randomUUID(), new Graph(List.of(stations))); }
    }
    public static final class Graph {
        private final List<CreateStation> stations;
        Graph(List<CreateStation> stations) { this.stations = stations; }
        public List<CreateStation> getPoints(String type) { return stations; }
    }
    public static final class CreateStation {
        public final UUID id;
        public final String name;
        public final Pair edgeLocation;
        Object blockDimension;
        final TransitStation.Position blockPosition;
        CreateStation(String name, String dimension, TransitStation.Position position) {
            this(UUID.randomUUID(), name, dimension, position);
        }
        CreateStation(UUID id, String name, String dimension, TransitStation.Position position) {
            this.id = id; this.name = name; blockDimension = dimension; blockPosition = position;
            edgeLocation = new Pair(new Node(dimension), new Node(dimension));
        }
        public Object getBlockEntityPos() { return blockPosition; }
        public Object getBlockEntityDimension() { return blockDimension; }
        public UUID getId() { return id; }
    }
    public static final class Pair {
        private final Node first;
        Node second;
        Pair(Node first, Node second) { this.first = first; this.second = second; }
        public Node getFirst() { return first; }
        public Node getSecond() { return second; }
    }
    public static final class Node {
        private final String dimension;
        Node(String dimension) { this.dimension = dimension; }
        public String getDimension() { return dimension; }
    }
    public static final class Mtr3 {
        public static final List<Platform3> PLATFORMS = new ArrayList<>();
        public static final Map<Object, Object> RAILS = new HashMap<>();
        public static final Cache3 DATA_CACHE = new Cache3();
    }
    public static final class Cache3 { public final Map<Long, Area3> platformIdToStation = new HashMap<>(); }
    public static final class Area3 {
        public final long id;
        public final String name;
        int maxX = 40;
        Area3(String name) { this(name, 101); }
        Area3(String name, long id) { this.name = name; this.id = id; }
        public long getId() { return id; }
        public boolean inArea(int x, int z) { return x >= 10 && x <= maxX && z >= 10 && z <= 40; }
    }
    public static final class Platform3 {
        public final long id;
        final TransitStation.Position position;
        private final boolean invalid;
        Platform3(long id, TransitStation.Position position, boolean invalid) {
            this.id = id; this.position = position; this.invalid = invalid;
        }
        public TransitStation.Position getMidPos() { return position; }
        public boolean isInvalidSavedRail(Map<?, ?> rails) { return invalid; }
    }
    public static final class Mtr4 {
        public final List<Platform4> platforms;
        Mtr4(List<Platform4> platforms) { this.platforms = new ArrayList<>(platforms); }
    }
    public static final class Area4 {
        private final String name;
        private final long id;
        int maxX = 40;
        Area4(String name) { this(name, 102); }
        Area4(String name, long id) { this.name = name; this.id = id; }
        public String getName() { return name; }
        public long getId() { return id; }
        public boolean inArea(TransitStation.Position position) {
            return position.x() >= 10 && position.x() <= maxX && position.y() >= 50 && position.y() <= 80;
        }
    }
    private static long nextMtr4PlatformId = 100;
    public static final class Platform4 {
        public final long id;
        public final Area4 area;
        final TransitStation.Position position;
        private final boolean invalid;
        Platform4(Area4 area, TransitStation.Position position, boolean invalid) {
            this(nextMtr4PlatformId++, area, position, invalid);
        }
        Platform4(long id, Area4 area, TransitStation.Position position, boolean invalid) {
            this.id = id; this.area = area; this.position = position; this.invalid = invalid;
        }
        public long getId() { return id; }
        public TransitStation.Position getMidPosition() { return position; }
        public boolean isInvalidSavedRail() { return invalid; }
    }
    // MTR 4.0.5's validator is inherited by Platform and requires the core Data base class.
    public static class Data4 {
        public final List<Platform4WithData> platforms;
        Data4(List<Platform4WithData> platforms) { this.platforms = new ArrayList<>(platforms); }
    }
    public static final class ClientData4 extends Data4 {
        ClientData4(List<Platform4WithData> platforms) { super(platforms); }
    }
    public static class SavedRail4WithData {
        private final boolean invalid;
        Data4 validationData;
        SavedRail4WithData(boolean invalid) { this.invalid = invalid; }
        public boolean isInvalidSavedRail(Data4 data) { validationData = data; return invalid; }
    }
    public static final class Platform4WithData extends SavedRail4WithData {
        public final long id;
        public final Area4 area;
        final TransitStation.Position position;
        Platform4WithData(Area4 area, TransitStation.Position position, boolean invalid) {
            this(nextMtr4PlatformId++, area, position, invalid);
        }
        Platform4WithData(long id, Area4 area, TransitStation.Position position, boolean invalid) {
            super(invalid); this.id = id; this.area = area; this.position = position;
        }
        public long getId() { return id; }
        public TransitStation.Position getMidPosition() { return position; }
    }
}
