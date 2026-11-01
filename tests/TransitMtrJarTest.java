// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import org.mtr.core.data.ClientData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Optional headless regression against the installed MTR 4 JAR, without Minecraft startup. */
public final class TransitMtrJarTest {
    private static int assertions;
    private static final TransitStationReader.PositionReader POSITIONS = value -> {
        Position position = (Position) value;
        return new TransitStation.Position((int) position.getX(), (int) position.getY(), (int) position.getZ());
    };

    public static void main(String[] args) throws Exception {
        ClientData data = new ClientData();
        Station station = new Station(data);
        station.setName("上南|Kaminami||private");
        station.setCorners(new Position(0, 60, 0), new Position(40, 80, 40));
        Position start = new Position(10, 65, 20);
        Position end = new Position(30, 65, 20);
        Platform platform = new Platform(start, end, TransportMode.TRAIN, data);
        Rail rail = Rail.newPlatformRail(start, Angle.E, end, Angle.W, Rail.Shape.QUADRATIC,
                0D, new ObjectArrayList<>(), TransportMode.TRAIN);
        check(rail.isPlatform(), "fixture contains an actual MTR platform rail");
        data.stations.add(station);
        data.platforms.add(platform);
        data.rails.add(rail);
        // Reproduce the synchronized cache relationship without running the transport simulator.
        platform.area = station;
        Object2ObjectOpenHashMap<Position, Rail> connections = new Object2ObjectOpenHashMap<>();
        connections.put(end, rail);
        data.positionsToRail.put(start, connections);

        List<TransitStation> discovered = snapshot(data);
        check(discovered.size() == 1, "real MTR API discovers the station with its valid platform");
        TransitStation found = discovered.get(0);
        check(found.source() == TransitStation.Source.MTR, "station is identified as MTR");
        check(found.name().equals("上南（Kaminami）"), "MTR name keeps its language alias and hides private metadata");
        check(found.waypointIdentity().equals(Long.toUnsignedString(station.getId())), "MTR station area identity is retained");
        check(found.position().equals(new TransitStation.Position(20, 65, 20)), "marker uses the real platform midpoint");
        check(found.dimension().equals("overworld"), "marker uses the synchronized client dimension");

        Position secondStart = new Position(10, 65, 30);
        Position secondEnd = new Position(30, 65, 30);
        Platform second = new Platform(secondStart, secondEnd, TransportMode.TRAIN, data);
        Rail secondRail = Rail.newPlatformRail(secondStart, Angle.E, secondEnd, Angle.W, Rail.Shape.QUADRATIC,
                0D, new ObjectArrayList<>(), TransportMode.TRAIN);
        second.area = station;
        data.platforms.add(second);
        data.rails.add(secondRail);
        Object2ObjectOpenHashMap<Position, Rail> secondConnections = new Object2ObjectOpenHashMap<>();
        secondConnections.put(secondEnd, secondRail);
        data.positionsToRail.put(secondStart, secondConnections);
        List<TransitStation> twoPlatforms = snapshot(data);
        check(twoPlatforms.size() == 1, "two real platforms in one MTR station area produce one station marker");
        TransitStation combined = twoPlatforms.get(0);
        check(combined.position().equals(new TransitStation.Position(20, 65, 25)),
                "two-platform station marker lies between the platform centers");
        check(combined.name().equals("上南（Kaminami）"), "combined waypoint keeps the formatted station name");
        check(TransitStationCatalog.markerId(found, "overworld")
                .equals(TransitStationCatalog.markerId(combined, "overworld")), "adding a platform preserves station marker ID");

        Position thirdStart = new Position(20, 65, 20);
        Position thirdEnd = new Position(40, 65, 20);
        Platform third = new Platform(thirdStart, thirdEnd, TransportMode.TRAIN, data);
        Rail thirdRail = Rail.newPlatformRail(thirdStart, Angle.E, thirdEnd, Angle.W, Rail.Shape.QUADRATIC,
                0D, new ObjectArrayList<>(), TransportMode.TRAIN);
        third.area = station;
        data.platforms.add(third);
        data.rails.add(thirdRail);
        Object2ObjectOpenHashMap<Position, Rail> thirdConnections = new Object2ObjectOpenHashMap<>();
        thirdConnections.put(thirdEnd, thirdRail);
        data.positionsToRail.put(thirdStart, thirdConnections);
        List<TransitStation> triangle = snapshot(data);
        check(triangle.size() == 1 && triangle.get(0).position().equals(new TransitStation.Position(22, 65, 22)),
                "three real platform centers use the geometric median rather than their arithmetic mean");
        data.platforms.remove(third);
        check(snapshot(data).get(0).position().equals(combined.position()),
                "removing the third platform restores the two-platform station center");

        station.setName("Renamed MTR|Station||private");
        List<TransitStation> renamedPlatforms = snapshot(data);
        TransitStation renamed = find(renamedPlatforms, station);
        check(renamed.name().equals("Renamed MTR（Station）"), "station rename appears with its formatted language alias");
        check(TransitStationCatalog.markerId(found, "overworld")
                .equals(TransitStationCatalog.markerId(renamed, "overworld")), "rename preserves marker ID");
        check(renamed.position().equals(combined.position()), "rename preserves the combined platform center");
        data.positionsToRail.clear();
        check(snapshot(data).isEmpty(), "missing real rails remove all markers");
        data.positionsToRail.put(start, connections);
        data.positionsToRail.put(secondStart, secondConnections);
        platform.area = null;
        List<TransitStation> unassigned = snapshot(data);
        check(unassigned.size() == 1 && unassigned.get(0).position().equals(new TransitStation.Position(20, 65, 30)),
                "unassigning one platform recalculates the station center from the other platform");
        platform.area = station;
        station.setCorners(new Position(0, 60, 0), new Position(40, 80, 24));
        List<TransitStation> narrowed = snapshot(data);
        check(narrowed.size() == 1 && narrowed.get(0).position().equals(found.position()),
                "narrowing the station area excludes only the second platform even with its cached area");
        station.setCorners(new Position(0, 60, 0), new Position(40, 80, 40));
        check(snapshot(data).size() == 1 && snapshot(data).get(0).position().equals(combined.position()),
                "expanding the station area restores the combined platform center");
        station.setCorners(new Position(100, 60, 100), new Position(140, 80, 140));
        check(snapshot(data).isEmpty(), "out-of-area platforms have no markers");
        station.setCorners(new Position(0, 60, 0), new Position(40, 80, 40));
        Station otherStation = new Station(data);
        otherStation.setName("Reassigned station");
        otherStation.setCorners(new Position(0, 60, 0), new Position(40, 80, 40));
        data.stations.add(otherStation);
        platform.area = otherStation;
        List<TransitStation> reassignedAreas = snapshot(data);
        check(reassignedAreas.size() == 2, "platforms reassigned to different station areas have separate station markers");
        TransitStation reassigned = find(reassignedAreas, otherStation);
        check(reassigned.name().equals(otherStation.getName()), "reassignment updates the platform's station name");
        check(!TransitStationCatalog.markerId(found, "overworld")
                .equals(TransitStationCatalog.markerId(reassigned, "overworld")), "distinct station areas have distinct marker IDs");
        check(find(reassignedAreas, station).position().equals(new TransitStation.Position(20, 65, 30)),
                "reassignment recalculates the original station center from its remaining platform");
        data.platforms.remove(platform);
        List<TransitStation> deletedFirst = snapshot(data);
        check(deletedFirst.size() == 1 && deletedFirst.get(0).position().equals(new TransitStation.Position(20, 65, 30)),
                "deleting the first platform preserves the remaining station at the second platform center");
        data.platforms.clear();
        check(snapshot(data).isEmpty(), "deleted platforms disappear");
        System.out.println("Installed MTR JAR station checks passed (" + assertions + " assertions).");
    }

    private static List<TransitStation> snapshot(ClientData data) throws ReflectiveOperationException {
        return TransitStationCatalog.unique(TransitStationReader.readMtr4(data, "overworld", POSITIONS), Object::toString);
    }

    private static TransitStation find(List<TransitStation> points, Station station) {
        return points.stream().filter(point -> point.waypointIdentity().equals(Long.toUnsignedString(station.getId())))
                .findFirst().orElseThrow(() -> new AssertionError("Missing station marker " + station.getId()));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
