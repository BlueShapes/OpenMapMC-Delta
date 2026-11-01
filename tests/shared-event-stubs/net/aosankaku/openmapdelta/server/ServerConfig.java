// Copyright (c) 2026 AoSankaku. OpenMapMC Delta test support.
package net.aosankaku.openmapdelta.server;

/** Provides only the shared waypoint cap needed by the server-event headless test. */
public final class ServerConfig {
    private ServerConfig() {}
    public static int maxSharedWaypoints() { return 4096; }
}
