// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Reads or creates the stable identity file for one saved world. */
public final class WorldIdentityFile {
    public static final int MAX_BYTES = 128;
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private WorldIdentityFile() {}

    /**
     * Loads an existing UUID, or atomically publishes a new identity without replacement.
     * Invalid or unreadable existing files are left untouched and return empty.
     */
    public static synchronized Optional<UUID> loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) return readExisting(file);
            Path target = file.toAbsolutePath();
            Path parent = target.getParent();
            if (parent == null) return Optional.empty();
            Files.createDirectories(parent);
            UUID id = newNonzeroUuid();
            byte[] bytes = id.toString().getBytes(StandardCharsets.US_ASCII);
            Path temporary = Files.createTempFile(parent, ".odysseymap-world-id-", ".tmp");
            try {
                try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer data = ByteBuffer.wrap(bytes);
                    while (data.hasRemaining()) output.write(data);
                    output.force(true);
                }
                try {
                    // Publishing a hard link is atomic and never replaces a competing identity.
                    Files.createLink(target, temporary);
                    return Optional.of(id);
                } catch (FileAlreadyExistsException concurrentCreate) {
                    return readExisting(target);
                } catch (IOException | UnsupportedOperationException hardLinksUnavailable) {
                    // Same-directory move remains no-replace because REPLACE_EXISTING is omitted.
                    try {
                        Files.move(temporary, target);
                        return Optional.of(id);
                    } catch (FileAlreadyExistsException concurrentCreate) {
                        return readExisting(target);
                    }
                }
            } finally {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                    // Only this method's private temporary file can remain.
                }
            }
        } catch (IOException | SecurityException failure) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> readExisting(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) return Optional.empty();
            for (byte value : bytes) if (value < 0 || value > 0x7f) return Optional.empty();
            String text = new String(bytes, StandardCharsets.US_ASCII);
            if (!UUID_TEXT.matcher(text).matches()) return Optional.empty();
            UUID id = UUID.fromString(text);
            return isNonzero(id) ? Optional.of(id) : Optional.empty();
        } catch (IOException | SecurityException failure) {
            return Optional.empty();
        }
    }

    private static UUID newNonzeroUuid() {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (!isNonzero(id));
        return id;
    }

    private static boolean isNonzero(UUID id) {
        return id.getMostSignificantBits() != 0L || id.getLeastSignificantBits() != 0L;
    }
}
