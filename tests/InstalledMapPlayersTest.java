// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.network.PlayerPosition;
import journeymap.common.util.PlayerRadarManager;
import xaero.common.XaeroMinimapSession;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;
import xaero.hud.minimap.player.tracker.synced.SyncedTrackedPlayer;
import xaero.hud.minimap.player.tracker.system.RenderedPlayerTracker;
import xaero.hud.minimap.player.tracker.system.RenderedPlayerTrackerManager;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exercises installed map stores using isolated fixture APIs, without linking either mod. */
public final class InstalledMapPlayersTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID SELF = id("self");
    private static final UUID OTHER = id("other");
    private static final UUID UNKNOWN = id("unknown");

    public static void main(String[] args) {
        journeyMapFiltersHiddenAndOfflinePlayersAndUsesCurrentLevelAdapter();
        xaeroRequiresOnlineProfilesAndExactDimension();
        xaeroReadsRenderedAndOptionalTrackerSystems();
        xaeroTrackerLinkageFailureDoesNotEscapeReader();
        missingXaeroSessionReturnsAnEmptySnapshot();
        System.out.println("Installed map player reader tests passed.");
    }

    private static void journeyMapFiltersHiddenAndOfflinePlayersAndUsesCurrentLevelAdapter() {
        JourneyPlayer sameLevel = new JourneyPlayer("same-level");
        JourneyPlayer wrongLevel = new JourneyPlayer("wrong-level");
        Map<UUID, JourneyPlayer> players = new LinkedHashMap<>();
        players.put(OTHER, sameLevel);
        players.put(SELF, new JourneyPlayer("self"));
        players.put(UNKNOWN, new JourneyPlayer("offline"));
        players.put(id("hidden"), null);
        players.put(id("wrong-level"), wrongLevel);
        PlayerRadarManager.setPlayers(players);

        InstalledMapPlayers.Context context = context((raw) -> {
            JourneyPlayer player = (JourneyPlayer) raw;
            if (!player.level.equals("same-level")) return null;
            return new PlayerPosition(OTHER, "UntrustedName", OVERWORLD, 16, 70, -4);
        }, key -> null);

        List<PlayerPosition> result = InstalledMapPlayers.readJourneyMap(context);
        equal(List.of(new PlayerPosition(OTHER, "OnlineOther", OVERWORLD, 16, 70, -4)), result,
                "JourneyMap maps only visible, online, non-self players accepted by the current-level adapter");
        expectUnsupported(() -> result.clear(), "reader snapshots are immutable");
    }

    private static void xaeroRequiresOnlineProfilesAndExactDimension() {
        ClientSyncedTrackedPlayerManager manager = new ClientSyncedTrackedPlayerManager();
        manager.setPlayers(List.of(
                new SyncedTrackedPlayer(OTHER, 8.9, 64, -8.1, new DimensionKey(OVERWORLD)),
                new SyncedTrackedPlayer(SELF, 4, 65, 4, new DimensionKey(OVERWORLD)),
                new SyncedTrackedPlayer(UNKNOWN, 99, 70, 99, new DimensionKey(OVERWORLD)),
                new SyncedTrackedPlayer(id("other-dimension"), 1, 2, 3, new DimensionKey(NETHER)),
                new SyncedTrackedPlayer(id("invalid-coordinate"), Double.NaN, 2, 3, new DimensionKey(OVERWORLD))));
        XaeroMinimapSession.setCurrentSession(new XaeroMinimapSession(manager));

        List<PlayerPosition> result = InstalledMapPlayers.readXaero(context(raw -> null,
                key -> ((DimensionKey) key).location()));
        equal(List.of(new PlayerPosition(OTHER, "OnlineOther", OVERWORLD, 8, 64, -9)), result,
                "Xaero positions are floored, dimension checked, profile named, and invalid entries omitted");
    }

    private static void missingXaeroSessionReturnsAnEmptySnapshot() {
        XaeroMinimapSession.setCurrentSession(null);
        equal(List.of(), InstalledMapPlayers.readXaero(context(raw -> null, key -> null)),
                "missing Xaero session safely returns a removable empty snapshot");
    }

    private static void xaeroTrackerLinkageFailureDoesNotEscapeReader() {
        Iterable<?> brokenSystems = () -> new Iterator<>() {
            @Override public boolean hasNext() { throw new NoSuchMethodError("fixture API changed"); }
            @Override public Object next() { throw new NoSuchMethodError("fixture API changed"); }
        };
        RenderedPlayerTrackerManager manager = new RenderedPlayerTrackerManager(brokenSystems);
        XaeroMinimapSession.setCurrentSession(new XaeroMinimapSession(new ClientSyncedTrackedPlayerManager(), manager, true));
        equal(List.of(), InstalledMapPlayers.readXaero(context(raw -> null, key -> null)),
                "a tracker linkage failure is isolated from the client tick");
    }

    private static void xaeroReadsRenderedAndOptionalTrackerSystems() {
        TrackerRecord record = new TrackerRecord(OTHER, -1.1, 68.9, 2.0, new DimensionKey(OVERWORLD));
        RenderedPlayerTracker system = new RenderedPlayerTracker(new TrackerReader(), List.of(record));
        RenderedPlayerTrackerManager manager = new RenderedPlayerTrackerManager(List.of(system));
        XaeroMinimapSession.setCurrentSession(new XaeroMinimapSession(new ClientSyncedTrackedPlayerManager(), manager, true));
        equal(List.of(new PlayerPosition(OTHER, "OnlineOther", OVERWORLD, -2, 68, 2)),
                InstalledMapPlayers.readXaero(context(raw -> null, key -> ((DimensionKey) key).location())),
                "rendered tracker systems provide online remote players");

        XaeroMinimapSession.setCurrentSession(new XaeroMinimapSession(new ClientSyncedTrackedPlayerManager(), manager, false));
        equal(List.of(new PlayerPosition(OTHER, "OnlineOther", OVERWORLD, -2, 68, 2)),
                InstalledMapPlayers.readXaero(context(raw -> null, key -> ((DimensionKey) key).location())),
                "older player tracker accessor remains supported");
    }

    private static InstalledMapPlayers.Context context(
            java.util.function.Function<Object, PlayerPosition> journeyMapper,
            java.util.function.Function<Object, String> dimensionMapper) {
        return new InstalledMapPlayers.Context(OVERWORLD, SELF,
                Map.of(SELF, "SelfPlayer", OTHER, "OnlineOther"), journeyMapper, dimensionMapper);
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static void expectUnsupported(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + ": mutation unexpectedly succeeded");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    public record JourneyPlayer(String level) { }
    public record DimensionKey(String location) { }
    public record TrackerRecord(UUID id, double x, double y, double z, Object dimension) { }

    public static final class TrackerReader {
        public UUID getId(Object player) { return ((TrackerRecord) player).id(); }
        public double getX(Object player) { return ((TrackerRecord) player).x(); }
        public double getY(Object player) { return ((TrackerRecord) player).y(); }
        public double getZ(Object player) { return ((TrackerRecord) player).z(); }
        public Object getDimension(Object player) { return ((TrackerRecord) player).dimension(); }
    }
}
