// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.platform;

import java.nio.file.Path;

/** Small configurable platform substitute for pure preference persistence tests. */
public final class Services {
    public static final Platform PLATFORM = new Platform();
    private Services() {}

    public static void setConfigDir(Path path) { PLATFORM.configDir = path; }

    public static final class Platform {
        private Path configDir;
        public Path getConfigDir() { return configDir; }
        public Path getGameDir() { return configDir.resolveSibling("game"); }
    }
}
