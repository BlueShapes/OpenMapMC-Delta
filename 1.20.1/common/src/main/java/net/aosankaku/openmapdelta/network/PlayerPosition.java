// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.util.Objects;
import java.util.UUID;

/** Immutable, bounded online-player position shared with the client and web map. */
public record PlayerPosition(UUID id, String name, String dimension, int x, int y, int z) {
    public PlayerPosition {
        Objects.requireNonNull(id, "id");
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Invalid player name");
        }
        if (dimension == null || dimension.length() > 128
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        if (Math.abs((long) x) >= 30_000_000 || Math.abs((long) z) >= 30_000_000
                || Math.abs((long) y) > 30_000_000) {
            throw new IllegalArgumentException("Invalid position");
        }
    }
}
