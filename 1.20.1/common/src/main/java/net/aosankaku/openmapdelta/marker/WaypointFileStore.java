// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes only Odyssey-owned files and preserves the previous file on serialization failure. */
public final class WaypointFileStore {
    @FunctionalInterface
    public interface Content { void write(Writer writer) throws IOException; }
    static class Operations {
        void copy(Path source, Path destination) throws IOException {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        void move(Path source, Path destination, boolean atomic) throws IOException {
            if (atomic) Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            else Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private WaypointFileStore() {}

    public static void write(Path destination, Content content) throws IOException {
        write(destination, content, new Operations());
    }

    static void write(Path destination, Content content, Operations operations) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "odysseymap-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { content.write(writer); }
            try {
                operations.move(temporary, destination, true);
            } catch (AtomicMoveNotSupportedException e) {
                replaceWithBackup(temporary, destination, operations);
            }
        } finally {
            // Cleanup failure after a successful replacement must not report a failed commit.
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    private static void replaceWithBackup(Path temporary, Path destination, Operations operations) throws IOException {
        Path backup = null;
        boolean backupReady = false;
        boolean replacementAttempted = false;
        boolean preserveBackup = false;
        try {
            if (Files.exists(destination)) {
                backup = Files.createTempFile(destination.getParent(), "odysseymap-backup-", ".tmp");
                operations.copy(destination, backup);
                backupReady = true;
            }
            replacementAttempted = true;
            operations.move(temporary, destination, false);
        } catch (IOException failure) {
            if (backupReady && replacementAttempted) {
                try { operations.copy(backup, destination); }
                catch (IOException restoreFailure) {
                    preserveBackup = true;
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        } finally {
            if (backup != null && !preserveBackup) {
                try { Files.deleteIfExists(backup); } catch (IOException ignored) {}
            }
        }
    }
}
