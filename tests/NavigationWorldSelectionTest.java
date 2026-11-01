// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import net.aosankaku.openmapdelta.marker.MarkerType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-destination selection without Minecraft startup or rendering native graphics. */
public final class NavigationWorldSelectionTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static final NavigationState.Position START = new NavigationState.Position(OVERWORLD, 40, 70, -20);
    private static final NavigationState.Observer HERE = new NavigationState.Observer(OVERWORLD, 0.5, 64, 0.5);
    private static int assertions;

    private record Marker(UUID id, NavigationState.Position position, MarkerType type) {}

    public static void main(String[] args) {
        selectedPlayerRendersWithVisibleAndHiddenSources();
        trackedPlayerMovesAndChangesDimension();
        stopAndArrivalRemoveWorldDestination();
        ordinaryPlayersAndStaticWaypointsStayUnchanged();
        missingSnapshotsAreIgnored();
        trackedPlayerDoesNotHideTheLocalMapHead();
        System.out.println("Navigation world selection tests passed (" + assertions + " assertions).");
    }

    private static NavigationState tracking() {
        NavigationState state = new NavigationState();
        check(state.start(PLAYER, PLAYER, START, HERE, 3, 5), "player navigation starts");
        return state;
    }

    private static List<Marker> world(NavigationState state, List<Marker> visible, String dimension) {
        return NavigationWorldSelection.merge(visible, state.targets(), dimension, Marker::id, Marker::position,
                Marker::type, target -> new Marker(target.id(), target.position(),
                        NavigationWorldSelection.snapshotType(target.trackedPlayerId() == null
                                ? MarkerType.NAVIGATION_POINT : MarkerType.PLAYER)));
    }

    private static void selectedPlayerRendersWithVisibleAndHiddenSources() {
        NavigationState state = tracking();
        Marker source = new Marker(PLAYER, START, MarkerType.PLAYER);
        Marker unrelated = new Marker(OTHER, START, MarkerType.PLAYER);
        List<Marker> selected = world(state, List.of(source, unrelated, source), OVERWORLD);
        check(selected.size() == 1, "one world destination despite visible or duplicate player sources");
        Marker target = selected.get(0);
        check(target.id().equals(PLAYER) && target.position().equals(START), "selected player identity and XYZ preserved");
        check(target.type().isWorldWaypoint(), "selected player reaches the icon and beam renderer");
        check(target.type() == MarkerType.NAVIGATION_POINT && !target.type().isEditableWaypoint(),
                "selected player uses a session world destination, not a saved waypoint");
        check(world(state, List.of(), OVERWORLD).equals(selected),
                "explicit navigation renders even when ordinary player and waypoint visibility is disabled");
        check(!MarkerType.PLAYER.isWorldWaypoint(), "unselected players remain map markers");
    }

    private static void trackedPlayerMovesAndChangesDimension() {
        NavigationState state = tracking();
        NavigationState.Position moved = new NavigationState.Position(OVERWORLD, 120, 83, 56);
        state.update(HERE, 5, Map.of(PLAYER, moved));
        List<Marker> selected = world(state, List.of(new Marker(PLAYER, START, MarkerType.PLAYER)), OVERWORLD);
        check(selected.size() == 1 && selected.get(0).position().equals(moved),
                "world icon and beam follow all three player coordinates despite a stale source");
        state.update(HERE, 5, Map.of());
        check(world(state, List.of(), OVERWORLD).get(0).position().equals(moved),
                "unavailable player data preserves the last known destination");
        NavigationState.Position changed = new NavigationState.Position(NETHER, 12, 75, -8);
        state.update(HERE, 5, Map.of(PLAYER, changed));
        check(world(state, List.of(), OVERWORLD).isEmpty(), "a target in another dimension does not leave a world beam");
        check(world(state, List.of(), NETHER).get(0).position().equals(changed),
                "entering the target dimension displays its current destination");
    }

    private static void stopAndArrivalRemoveWorldDestination() {
        NavigationState state = tracking();
        Marker source = new Marker(PLAYER, START, MarkerType.PLAYER);
        state.stop(PLAYER);
        check(world(state, List.of(source), OVERWORLD).isEmpty(), "manual stop removes icon and beam for a visible player");
        state = tracking();
        NavigationState.Observer arrived = new NavigationState.Observer(OVERWORLD, 40.5, 70, -19.5);
        check(state.update(arrived, 5, Map.of()).equals(List.of(PLAYER)), "arrival completes the selected player target");
        check(world(state, List.of(source), OVERWORLD).isEmpty(), "arrival removes icon and beam");
        state = tracking();
        state.clear();
        check(world(state, List.of(source), OVERWORLD).isEmpty(), "session reset clears player world destinations");
    }

    private static void ordinaryPlayersAndStaticWaypointsStayUnchanged() {
        NavigationState state = new NavigationState();
        Marker ordinary = new Marker(OTHER, START, MarkerType.WAYPOINT);
        Marker player = new Marker(PLAYER, START, MarkerType.PLAYER);
        check(world(state, List.of(ordinary, player), OVERWORLD).equals(List.of(ordinary)),
                "ordinary waypoints render and unselected players do not gain a beam");
        state.start(OTHER, null, START, HERE, 3, 5);
        Marker staticSnapshot = new Marker(OTHER, START, NavigationWorldSelection.snapshotType(MarkerType.WAYPOINT));
        List<Marker> selected = NavigationWorldSelection.merge(List.of(ordinary, player), state.targets(), OVERWORLD,
                Marker::id, Marker::position, Marker::type, target -> staticSnapshot);
        check(selected.equals(List.of(ordinary)), "a static waypoint navigation does not duplicate its existing beam");
        check(NavigationWorldSelection.snapshotType(MarkerType.NAVIGATION_POINT) == MarkerType.NAVIGATION_POINT,
                "coordinate navigation keeps its existing world marker");
        check(NavigationWorldSelection.snapshotType(MarkerType.SHARED_WAYPOINT) == MarkerType.NAVIGATION,
                "shared waypoint snapshots remain map fallbacks");
    }

    private static void missingSnapshotsAreIgnored() {
        NavigationState state = tracking();
        check(NavigationWorldSelection.merge(List.<Marker>of(), state.targets(), OVERWORLD,
                Marker::id, Marker::position, Marker::type, target -> null).isEmpty(),
                "a removed navigation snapshot cannot create a stale world destination");
    }

    private static void trackedPlayerDoesNotHideTheLocalMapHead() {
        check(!NavigationMapSelection.suppressesLocalHead(
                        NavigationWorldSelection.snapshotType(MarkerType.PLAYER), PLAYER),
                "nearby hidden or offline player navigation does not suppress the local map head");
        check(NavigationMapSelection.suppressesLocalHead(MarkerType.WAYPOINT, null),
                "ordinary nearby waypoints keep their existing overlap policy");
        check(NavigationMapSelection.suppressesLocalHead(MarkerType.NAVIGATION_POINT, null),
                "coordinate navigation keeps its existing overlap policy");
        check(NavigationMapSelection.suppressesLocalHead(MarkerType.LOCATION_NOTICE, PLAYER),
                "tracked location notices keep their existing overlap policy");
        check(!NavigationMapSelection.suppressesLocalHead(MarkerType.PLAYER, null)
                        && !NavigationMapSelection.suppressesLocalHead(MarkerType.NAVIGATION, PLAYER),
                "ordinary player and map-only fallback markers do not suppress the local head");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
