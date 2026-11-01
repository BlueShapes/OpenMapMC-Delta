// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Resolves persistent application paths only after the legacy-data gate succeeds. */
public final class DataPaths {
    private DataPaths() {}

    public static void ensureMigrated() {
        try {
            DataDirectoryMigration.ensureMigrated(Services.PLATFORM.getConfigDir(), Services.PLATFORM.getGameDir());
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot migrate OpenMap Delta data before accessing it", failure);
        }
    }

    /** New settings live in {@code config/openmapdelta}. */
    public static Path configDirectory() {
        ensureMigrated();
        return Services.PLATFORM.getConfigDir().resolve(DataDirectoryMigration.DATA_DIRECTORY);
    }

    /** Tile cache data lives directly in the game directory. */
    public static Path cacheDirectory() {
        ensureMigrated();
        return Services.PLATFORM.getGameDir().resolve(DataDirectoryMigration.CACHE_DIRECTORY);
    }
}
