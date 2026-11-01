// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import net.aosankaku.openmapdelta.marker.MarkerType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Destination lifecycle and map selection tests, without Minecraft client or server startup. */
public final class NavigationStateTest {
    private static int assertions;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final NavigationState.Position ORIGIN = position(OVERWORLD, 0, 64, 0);
    private static final NavigationState.Observer HERE = observer(OVERWORLD, 0.5, 64, 0.5);
    private static boolean expectedDeathSelectable;

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Expected whether Smart Deathpoints are integrated");
        expectedDeathSelectable = Boolean.parseBoolean(args[0]);
        capacityAndDuplicates();
        initialArrivalRequiresExitAndReentry();
        arrivalUsesFeetAndStandingBlockCenter();
        dimensionsAndUnknownHeightsDoNotInventArrivals();
        trackingKeepsLatestValidPosition();
        staticDestinationsDoNotFollowPlayers();
        disabledArrivalAndLifecycle();
        mapFallbacksAndDeduplication();
        requestPolicy();
        localHeadVisibility();
        check(MarkerType.NAVIGATION.isMapSelectable() && !MarkerType.NAVIGATION.isWorldWaypoint()
                && !MarkerType.NAVIGATION.isEditableWaypoint(), "navigation remains a map-only type");
        check(MarkerType.NAVIGATION_POINT.isMapSelectable() && MarkerType.NAVIGATION_POINT.isWorldWaypoint()
                && !MarkerType.NAVIGATION_POINT.isEditableWaypoint(),
                "coordinate navigation can render as a world waypoint without becoming editable");
        check(MarkerType.PLAYER.isMapSelectable() && MarkerType.LOCATION_NOTICE.isMapSelectable(),
                "players and notices are selectable");
        check(MarkerType.DEATH.isMapSelectable() == expectedDeathSelectable,
                "Smart Deathpoints are map targets only in source roots that integrate them");
        check(!MarkerType.PORTAL.isMapSelectable() && !MarkerType.ENTITY.isMapSelectable(),
                "incidental portals and entities are not navigation targets");
        System.out.println("Navigation state tests passed (" + assertions + " assertions).");
    }

    private static void capacityAndDuplicates() {
        NavigationState state = new NavigationState();
        UUID first = id(1), second = id(2), third = id(3), fourth = id(4);
        check(state.start(first, null, ORIGIN, HERE, 3, 5), "first target starts");
        check(state.start(second, null, ORIGIN, HERE, 3, 5), "second target starts");
        check(state.start(third, null, ORIGIN, HERE, 3, 5), "third target starts");
        check(!state.start(fourth, null, ORIGIN, HERE, 3, 5), "capacity rejects without evicting");
        check(!state.start(first, first, position(NETHER, 20, 70, 20), HERE, 16, 5), "same ID never duplicates or replaces");
        check(state.targets().get(0).position().equals(ORIGIN) && state.targets().get(0).trackedPlayerId() == null,
                "duplicate requests preserve the original snapshot and mode");
        check(state.size() == 3 && state.contains(first) && state.contains(second) && state.contains(third),
                "capacity rejection leaves every existing target intact");
        check(!state.start(fourth, null, ORIGIN, HERE, 1, 5) && state.size() == 3,
                "lowering the limit blocks new targets without deleting existing ones");
        state.stop(first);
        check(!state.start(fourth, null, ORIGIN, HERE, 1, 5), "lower limit still applies after one stop");
        state.stop(second); state.stop(third);
        check(state.start(fourth, null, ORIGIN, HERE, 1, 5), "a free slot permits a new target");
        state.clear();
        for (int i = 0; i < 16; i++) check(state.start(id(i), null, ORIGIN, HERE, 16, 0), "maximum supported slots");
        check(!state.start(id(100), null, ORIGIN, HERE, 16, 0) && state.size() == 16,
                "sixteen-slot cap never evicts silently");
        check(!state.start(null, null, ORIGIN, HERE, 3, 5), "invalid target IDs are rejected");
    }

    private static void initialArrivalRequiresExitAndReentry() {
        NavigationState state = new NavigationState(); UUID target = id(10);
        state.start(target, null, ORIGIN, HERE, 3, 5);
        check(state.update(HERE, 5, Map.of()).isEmpty() && state.contains(target), "initially inside waits");
        check(state.update(observer(OVERWORLD, 5.5, 64, 0.5), 5, Map.of()).isEmpty(), "arrival boundary remains inside");
        check(state.update(observer(OVERWORLD, 5.500001, 64, 0.5), 5, Map.of()).isEmpty(), "exit arms but does not arrive");
        check(state.update(observer(OVERWORLD, 5.5, 64, 0.5), 5, Map.of()).equals(List.of(target)), "reentry ends at inclusive boundary");
        check(!state.contains(target), "arrived target is removed");
        state.start(target, null, ORIGIN, HERE, 3, 5);
        check(state.update(observer(NETHER, 0.5, 64, 0.5), 5, Map.of()).isEmpty(), "leaving the dimension arms arrival");
        check(state.update(HERE, 5, Map.of()).equals(List.of(target)), "returning to the arrival sphere ends navigation");
    }

    private static void arrivalUsesFeetAndStandingBlockCenter() {
        close(NavigationState.distanceSquared(HERE, ORIGIN), 0, "horizontal block center matches feet");
        close(NavigationState.distanceSquared(observer(OVERWORLD, 3.5, 68, 0.5), ORIGIN), 25,
                "all three coordinate axes contribute");
        NavigationState state = new NavigationState(); UUID target = id(20);
        state.start(target, null, ORIGIN, observer(OVERWORLD, 0.5, 69.001, 0.5), 3, 5);
        close(state.distance(observer(OVERWORLD, 0.5, 69.001, 0.5), target), 5.001,
                "remaining distance uses actual vertical feet position");
        check(state.update(observer(OVERWORLD, 0.5, 69, 0.5), 5, Map.of()).equals(List.of(target)),
                "vertical approach arrives at the sphere boundary");
    }

    private static void dimensionsAndUnknownHeightsDoNotInventArrivals() {
        NavigationState state = new NavigationState(); UUID target = id(30);
        state.start(target, null, position(NETHER, 0, 64, 0), HERE, 3, 5);
        check(state.update(HERE, 5, Map.of()).isEmpty() && state.contains(target), "same XYZ in another dimension never arrives");
        check(Double.isNaN(state.distance(HERE, target)), "another-dimension distance is unknown");
        check(state.update(observer(NETHER, 0.5, 64, 0.5), 5, Map.of()).equals(List.of(target)), "correct dimension permits arrival");
        state.start(target, null, position(OVERWORLD, 0, NavigationState.UNKNOWN_Y, 0), HERE, 3, 5);
        close(state.distance(observer(OVERWORLD, 3.5, 10000, 4.5), target), 5,
                "unknown Y shows horizontal distance without borrowing the player's Y");
        check(state.update(HERE, 5, Map.of()).isEmpty() && state.contains(target),
                "unknown Y remains fail-safe for automatic arrival");
        check(Double.isNaN(state.distance(observer(NETHER, 0.5, 64, 0.5), target))
                        && Double.isNaN(state.distance(observer(OVERWORLD, Double.NaN, 64, 0.5), target)),
                "unknown-Y display distance still rejects other dimensions and invalid observers");
        state.clear();
        state.start(target, null, ORIGIN, HERE, 3, 5);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(state.update(observer(OVERWORLD, invalid, 64, 0.5), 5, Map.of()).isEmpty(), "invalid observer cannot arm or arrive");
        }
        check(state.update(HERE, 5, Map.of()).isEmpty(), "invalid observations preserve initial-inside waiting");
    }

    private static void trackingKeepsLatestValidPosition() {
        NavigationState state = new NavigationState(); UUID target = id(40), player = id(400);
        state.start(target, player, ORIGIN, HERE, 3, 5);
        NavigationState.Position moved = position(OVERWORLD, 20, 70, 30);
        state.update(HERE, 5, Map.of(player, moved));
        check(state.targets().get(0).position().equals(moved), "online player UUID moves the destination");
        check(state.targets().get(0).id().equals(target), "tracking preserves the source marker ID");
        state.update(HERE, 5, Map.of());
        check(state.targets().get(0).position().equals(moved), "unknown/departed player retains the last position");
        state.update(HERE, 5, Map.of(id(999), ORIGIN));
        check(state.targets().get(0).position().equals(moved), "another player's update cannot replace the target");
        NavigationState.Position dimensionChange = position(NETHER, 0, 64, 0);
        state.update(HERE, 5, Map.of(player, dimensionChange));
        check(state.contains(target) && state.targets().get(0).position().equals(dimensionChange),
                "following a dimension change updates the dimension without arriving at same XYZ");
        check(state.update(observer(NETHER, 0.5, 64, 0.5), 5, Map.of()).equals(List.of(target)),
                "offline last position still arrives only in its last dimension");
    }

    private static void staticDestinationsDoNotFollowPlayers() {
        NavigationState state = new NavigationState(); UUID target = id(50);
        state.start(target, null, ORIGIN, HERE, 3, 0);
        state.update(HERE, 0, Map.of(target, position(NETHER, 100, 80, 100)));
        check(state.targets().get(0).position().equals(ORIGIN), "static source coordinate snapshot never follows player data");
        try { state.targets().clear(); throw new AssertionError("Mutable target list"); }
        catch (UnsupportedOperationException expected) { check(state.size() == 1, "callers cannot mutate destination membership"); }
    }

    private static void disabledArrivalAndLifecycle() {
        NavigationState state = new NavigationState(); UUID target = id(60), player = id(600);
        state.start(target, player, ORIGIN, HERE, 3, 0);
        for (int i = 0; i < 100; i++) {
            check(state.update(HERE, 0, Map.of(player, ORIGIN)).isEmpty() && state.contains(target),
                    "zero disables autoarrival while tracking remains active");
        }
        state.stop(target);
        check(state.size() == 0 && Double.isNaN(state.distance(HERE, target)), "manual stop removes distance and state");
        state.start(target, null, ORIGIN, HERE, 3, 5);
        state.clear();
        check(state.targets().isEmpty() && !state.contains(target), "session reset clears every destination");
        check(state.start(target, null, ORIGIN, HERE, 3, 5), "a new session can reuse the same source ID");
        check(state.update(HERE, 5, Map.of()).isEmpty(), "a reused ID receives a fresh arrival gate");
    }

    private record MapMarker(UUID id, NavigationState.Position position, String kind) {}
    private static void mapFallbacksAndDeduplication() {
        NavigationState state = new NavigationState(); UUID target = id(70);
        state.start(target, null, ORIGIN, HERE, 3, 0);
        MapMarker original = new MapMarker(target, ORIGIN, "original");
        var visible = merge(state, List.of(original));
        check(visible.size() == 1 && visible.get(0) == original, "visible original wins instead of overlapping a fallback");
        var hidden = merge(state, List.of());
        check(hidden.size() == 1 && hidden.get(0).id().equals(target) && hidden.get(0).kind().equals("navigation"),
                "hidden or deleted source produces one same-ID navigation fallback");
        MapMarker edited = new MapMarker(target, position(OVERWORLD, 30, 80, 40), "edited");
        var frozen = merge(state, List.of(edited));
        check(frozen.size() == 1 && frozen.get(0).position().equals(ORIGIN), "editing the source preserves the session coordinate snapshot");
        check(merge(state, List.of(original, original)).size() == 1, "duplicate source draw entries are deduplicated");
        check(NavigationMapSelection.merge(List.<MapMarker>of(), state.targets(), NETHER, MapMarker::id,
                MapMarker::position, t -> new MapMarker(t.id(), t.position(), "navigation")).isEmpty(),
                "fallbacks draw only in their own dimension");
        state.stop(target);
        check(merge(state, List.of(edited)).get(0) == edited, "stopping navigation restores the ordinary edited marker");
        check(merge(state, List.of()).isEmpty(), "stopping removes a deleted source's fallback");
    }

    private static List<MapMarker> merge(NavigationState state, List<MapMarker> visible) {
        return NavigationMapSelection.merge(visible, state.targets(), OVERWORLD, MapMarker::id,
                MapMarker::position, t -> new MapMarker(t.id(), t.position(), "navigation"));
    }

    private static void localHeadVisibility() {
        for (boolean configured : new boolean[]{false, true}) for (boolean navigating : new boolean[]{false, true})
            for (boolean overlap : new boolean[]{false, true}) for (boolean inside : new boolean[]{false, true}) {
                boolean expected = inside && (navigating || configured && !overlap);
                check(NavigationMapSelection.localHeadVisible(configured, navigating, overlap, inside) == expected,
                        "local-head selection uses its actual viewport/config/suppression draw policy");
            }
    }

    private static void requestPolicy() {
        UUID self = id(90), other = id(91);
        check(!NavigationRequestPolicy.canStart(self, self), "the local player cannot become its own destination");
        check(NavigationRequestPolicy.canStart(other, self), "other destinations remain available");
        check(!NavigationRequestPolicy.canStart(null, self), "missing destination IDs are rejected");
        check(NavigationRequestPolicy.contextDestination(false, false)
                        == NavigationRequestPolicy.ContextDestination.MAP_COORDINATE,
                "an empty map click may navigate to its coordinates");
        check(NavigationRequestPolicy.contextDestination(true, true)
                        == NavigationRequestPolicy.ContextDestination.SELECTED_MARKER,
                "a still-present selected marker retains priority");
        check(NavigationRequestPolicy.contextDestination(true, false)
                        == NavigationRequestPolicy.ContextDestination.STALE_SELECTION,
                "a stale marker selection cannot turn into blank-map navigation");
    }

    private static UUID id(int value) { return new UUID(0, value); }
    private static NavigationState.Position position(String dimension, int x, int y, int z) {
        return new NavigationState.Position(dimension, x, y, z);
    }
    private static NavigationState.Observer observer(String dimension, double x, double y, double z) {
        return new NavigationState.Observer(dimension, x, y, z);
    }
    private static void close(double value, double expected, String description) {
        check(Math.abs(value - expected) < 1e-8, description);
    }
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
}
