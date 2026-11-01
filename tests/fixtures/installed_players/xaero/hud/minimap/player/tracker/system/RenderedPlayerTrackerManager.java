package xaero.hud.minimap.player.tracker.system;

import java.util.List;

/** Test-only approximation of Xaero's rendered tracker system registry. */
public final class RenderedPlayerTrackerManager {
    private final Iterable<?> systems;
    public RenderedPlayerTrackerManager(Iterable<?> systems) { this.systems = systems; }
    public Iterable<?> getAllSystems() { return systems; }
}
