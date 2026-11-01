// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.Objects;

/** A connection identity prevents stale scans/screens from surviving logout or reconnect. */
public final class WaypointImportSession {
    private Object connection;
    private String world;
    private long token;

    public boolean enter(Object currentConnection, String currentWorld) {
        if (currentConnection == connection && Objects.equals(currentWorld, world)) return false;
        connection = currentConnection;
        world = currentWorld;
        token++;
        return true;
    }

    public void clear() {
        connection = null;
        world = null;
        token++;
    }

    public long token() { return token; }
    public String world() { return world; }

    public boolean matches(Object currentConnection, String currentWorld, long expectedToken) {
        return connection != null && world != null && connection == currentConnection
                && world.equals(currentWorld) && token == expectedToken;
    }

    public static boolean visibleInWorld(String importedWorld, String currentWorld) {
        return importedWorld == null || importedWorld.equals(currentWorld);
    }
}
