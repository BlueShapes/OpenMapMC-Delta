// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.platform.services;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface IPlatformHelper {
    String getPlatformName();
    boolean isModLoaded(String modId);
    boolean isDevelopmentEnvironment();
    Path getConfigDir();

    /** Game directory for persistent cache storage; real loaders override this for their launch root. */
    default Path getGameDir() { return Paths.get(".").toAbsolutePath().normalize(); }

    /** Human-readable mod name for item icon search; namespaces remain a safe fallback. */
    default String getModName(String modId) {
        return modId == null || modId.isBlank() ? "minecraft" : modId;
    }

    /** Loader-reported version used to detect changes to installed map mods. */
    default String getModVersion(String modId) { return "unknown"; }

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
