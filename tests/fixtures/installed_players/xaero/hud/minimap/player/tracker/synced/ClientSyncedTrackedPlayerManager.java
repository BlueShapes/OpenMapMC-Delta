package xaero.hud.minimap.player.tracker.synced;

import java.util.List;

/** Test-only approximation of Xaero's synced player store. */
public final class ClientSyncedTrackedPlayerManager {
    private Iterable<?> players = List.of();
    public Iterable<?> getPlayers() { return players; }
    public void setPlayers(Iterable<?> players) { this.players = players; }
}
