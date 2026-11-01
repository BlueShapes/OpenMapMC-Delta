package net.aosankaku.openmapdelta.marker;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Isolated writer with a controllable failure for disk-first commit tests. */
public final class WaypointFileStore {
    @FunctionalInterface
    public interface Content { void write(Writer writer) throws IOException; }

    public static boolean failNextWrite;

    private WaypointFileStore() {}

    public static void write(Path destination, Content content) throws IOException {
        if (failNextWrite) {
            failNextWrite = false;
            throw new IOException("Simulated atomic-write failure");
        }
        Files.createDirectories(destination.getParent());
        try (Writer writer = Files.newBufferedWriter(destination)) { content.write(writer); }
    }
}
