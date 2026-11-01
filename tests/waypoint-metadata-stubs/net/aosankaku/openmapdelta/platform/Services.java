// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.platform;

import java.nio.file.Path;

public final class Services {
    public static final Platform PLATFORM = new Platform();
    private Services() {}
    public static final class Platform {
        public Path getConfigDir() { return Path.of(System.getProperty("codex.waypoint.test.config")); }
        public Path getGameDir() { return getConfigDir().resolveSibling("game"); }
    }
}
