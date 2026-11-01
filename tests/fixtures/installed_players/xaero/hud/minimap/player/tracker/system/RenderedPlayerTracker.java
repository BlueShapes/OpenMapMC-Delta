package xaero.hud.minimap.player.tracker.system;

import java.util.Iterator;
import java.util.List;

/** Test-only approximation of the rendered tracker contract. */
public final class RenderedPlayerTracker {
    private final Object reader;
    private final List<?> players;
    public RenderedPlayerTracker(Object reader, List<?> players) {
        this.reader = reader;
        this.players = players;
    }
    public Object getReader() { return reader; }
    public Iterator<?> getTrackedPlayerIterator() { return players.iterator(); }
}
