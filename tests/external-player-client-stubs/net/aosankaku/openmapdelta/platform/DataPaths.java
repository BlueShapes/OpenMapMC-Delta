package net.aosankaku.openmapdelta.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Uses a test-owned directory rather than the game configuration path. */
public final class DataPaths {
    private DataPaths() { }
    public static Path configDirectory() {
        Path directory = Path.of(System.getProperty("external.player.test.dir", ".task-work/external-player-client-test"));
        try { Files.createDirectories(directory); } catch (IOException failure) { throw new IllegalStateException(failure); }
        return directory;
    }
}
