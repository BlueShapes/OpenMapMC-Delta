// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Server-supplied state. Apply and reset on the client game thread. */
public final class ClientServerState {
    public static final int MAX_PLAYERS = 4096;
    private record State(boolean caveAllowed, List<PlayerPosition> players, boolean received) {}
    private static volatile State state = new State(true, List.of(), false);

    private ClientServerState() {}

    public static boolean hasSnapshot() { return state.received(); }
    public static boolean isCaveAllowed() { return state.caveAllowed(); }
    public static List<PlayerPosition> players() { return state.players(); }

    public static void apply(boolean caveAllowed, List<PlayerPosition> players) {
        if (players.size() > MAX_PLAYERS) throw new IllegalArgumentException("Too many players");
        List<PlayerPosition> snapshot = List.copyOf(players);
        var ids = new HashSet<UUID>();
        for (PlayerPosition player : snapshot) {
            if (!ids.add(player.id())) throw new IllegalArgumentException("Duplicate player");
        }
        state = new State(caveAllowed, snapshot, true);
    }

    public static void reset() { state = new State(true, List.of(), false); }
}
