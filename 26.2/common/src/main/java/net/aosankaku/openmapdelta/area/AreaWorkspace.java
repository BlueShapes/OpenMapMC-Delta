// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;

/** Client-only persistence, deliberately separate from terrain-cache deletion and server transport. */
public final class AreaWorkspace {
    public enum Result { SAVED, CONFLICT, UNAVAILABLE, INVALID, IO_ERROR }
    private final Path root;
    private Object connection;
    private String identity;
    private Path path;
    private AreaDocument document = new AreaDocument(0, java.util.List.of());
    private boolean available;
    private IOException failure;

    public AreaWorkspace(Path configDirectory) {
        root = Objects.requireNonNull(configDirectory).toAbsolutePath().normalize().resolve("places");
    }

    /** Runs on the client thread. Repeated ticks do not read the same file again. */
    public void open(Object sessionConnection, String stableIdentity) {
        if (sessionConnection == connection && Objects.equals(stableIdentity, identity)) return;
        reset();
        connection = sessionConnection;
        identity = stableIdentity;
        if (sessionConnection == null || stableIdentity == null || stableIdentity.isBlank()) return;
        path = root.resolve(hash(stableIdentity)).resolve("areas.json");
        AreaStore.LoadResult loaded = AreaStore.load(path, stableIdentity);
        available = loaded.valid();
        failure = loaded.failure();
        if (available) document = loaded.document();
    }

    /** The base object is also an editor-generation token; equal empty revisions from other sessions are stale. */
    public Result save(AreaDocument base, AreaDocument edited) {
        if (!available || identity == null || path == null) return Result.UNAVAILABLE;
        if (base != document) return Result.CONFLICT;
        if (edited == null) return Result.INVALID;
        try {
            if (edited.areas().stream().anyMatch(area -> area.scope() != MapArea.Scope.PERSONAL)) return Result.INVALID;
            if (edited.areas().equals(document.areas())) return Result.SAVED;
            long nextRevision = Math.addExact(document.revision(), 1);
            var values = new ArrayList<MapArea>(edited.areas().size());
            for (MapArea area : edited.areas()) {
                MapArea previous = document.find(area.id()).orElse(null);
                values.add(area.equals(previous) ? area : area.withRevision(nextRevision));
            }
            AreaDocument candidate = new AreaDocument(nextRevision, values);
            AreaStore.save(path, identity, candidate);
            document = candidate;
            failure = null;
            return Result.SAVED;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return Result.INVALID;
        } catch (IOException storageFailure) {
            failure = storageFailure;
            return Result.IO_ERROR;
        }
    }

    public boolean available() { return available; }
    public AreaDocument document() { return document; }
    public Path path() { return path; }
    public IOException failure() { return failure; }

    public void reset() {
        connection = null;
        identity = null;
        path = null;
        document = new AreaDocument(0, java.util.List.of());
        available = false;
        failure = null;
    }

    private static String hash(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
