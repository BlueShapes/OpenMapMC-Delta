// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.network.PlayerPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Session-scoped, expiring snapshots supplied by optional map integrations. */
public final class ExternalPlayerState {
    public static final int MAX_PLAYERS = 4096;
    public static final long TTL_NANOS = 15_000_000_000L;

    /** Enum order is the precedence order for a player present in multiple sources. */
    public enum Source { JOURNEYMAP, XAERO, BLUEMAP }

    private record Snapshot(Map<UUID, PlayerPosition> players, long capturedAtNanos) { }

    private final EnumMap<Source, Snapshot> snapshots = new EnumMap<>(Source.class);
    private Object sessionToken;
    private Object levelToken;

    /** Begin or retain the exact client session/level pair. Any identity change clears old data. */
    public void beginSession(Object nextSessionToken, Object nextLevelToken) {
        if (nextSessionToken == null || nextLevelToken == null) {
            reset();
            return;
        }
        if (sessionToken != nextSessionToken || levelToken != nextLevelToken) {
            snapshots.clear();
            sessionToken = nextSessionToken;
            levelToken = nextLevelToken;
        }
    }

    /** Replace one source's entire snapshot; old callbacks cannot write into another world/session. */
    public boolean replace(Source source, Object expectedSessionToken, Object expectedLevelToken,
                           Collection<PlayerPosition> players, long nowNanos) {
        if (source == null || !isCurrent(expectedSessionToken, expectedLevelToken)) return false;
        LinkedHashMap<UUID, PlayerPosition> bounded = new LinkedHashMap<>();
        if (players != null) {
            int scanned = 0;
            try {
                for (PlayerPosition player : players) {
                    if (scanned++ >= MAX_PLAYERS * 2) break;
                    if (player != null) bounded.putIfAbsent(player.id(), player);
                    if (bounded.size() >= MAX_PLAYERS) break;
                }
            } catch (RuntimeException ignored) {
                // Preserve the last complete snapshot when an optional source mutates while being read.
                return false;
            }
        }
        snapshots.put(source, new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(bounded)), nowNanos));
        return true;
    }

    /** Return the current merged view, ordered by source precedence and stable source order. */
    public List<PlayerPosition> snapshot(Object expectedSessionToken, Object expectedLevelToken, long nowNanos) {
        if (!isCurrent(expectedSessionToken, expectedLevelToken)) return List.of();
        snapshots.entrySet().removeIf(entry -> expired(entry.getValue(), nowNanos));
        LinkedHashMap<UUID, PlayerPosition> merged = new LinkedHashMap<>();
        for (Source source : Source.values()) {
            Snapshot snapshot = snapshots.get(source);
            if (snapshot == null) continue;
            for (Map.Entry<UUID, PlayerPosition> entry : snapshot.players().entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
                if (merged.size() >= MAX_PLAYERS) break;
            }
            if (merged.size() >= MAX_PLAYERS) break;
        }
        return List.copyOf(new ArrayList<>(merged.values()));
    }

    /** Clear all data and identity tokens, normally on disconnect. */
    public void reset() {
        snapshots.clear();
        sessionToken = null;
        levelToken = null;
    }

    private boolean isCurrent(Object expectedSessionToken, Object expectedLevelToken) {
        return expectedSessionToken != null && expectedLevelToken != null
                && sessionToken == expectedSessionToken && levelToken == expectedLevelToken;
    }

    private static boolean expired(Snapshot snapshot, long nowNanos) {
        long elapsed = nowNanos - snapshot.capturedAtNanos();
        return elapsed >= TTL_NANOS;
    }
}
