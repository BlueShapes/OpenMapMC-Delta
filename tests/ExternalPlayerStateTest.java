// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.network.PlayerPosition;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Headless checks for source replacement, session isolation, precedence and expiry. */
public final class ExternalPlayerStateTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final long SECOND = 1_000_000_000L;

    public static void main(String[] args) {
        replacementAndRemovalAreImmediate();
        oldSessionAndLevelSnapshotsAreRejected();
        journeyMapWinsDuplicatePlayersUntilItsSnapshotExpires();
        snapshotsExpireAtFifteenSeconds();
        snapshotsAreBoundedAndIgnoreNullEntries();
        System.out.println("External player state tests passed.");
    }

    private static void replacementAndRemovalAreImmediate() {
        ExternalPlayerState state = new ExternalPlayerState();
        Object session = new Object();
        Object level = new Object();
        state.beginSession(session, level);
        PlayerPosition player = player("removed", "FarPlayer", 40, 70, -12);

        state.replace(ExternalPlayerState.Source.XAERO, session, level, List.of(player), 10 * SECOND);
        equal(List.of(player), state.snapshot(session, level, 10 * SECOND), "initial source snapshot");
        state.replace(ExternalPlayerState.Source.XAERO, session, level, List.of(), 11 * SECOND);
        equal(List.of(), state.snapshot(session, level, 11 * SECOND), "empty full snapshot removes old players immediately");
    }

    private static void oldSessionAndLevelSnapshotsAreRejected() {
        ExternalPlayerState state = new ExternalPlayerState();
        Object firstSession = new Object();
        Object secondSession = new Object();
        Object firstLevel = new Object();
        Object secondLevel = new Object();
        state.beginSession(firstSession, firstLevel);
        PlayerPosition oldPlayer = player("old", "OldPlayer", 1, 2, 3);
        state.replace(ExternalPlayerState.Source.JOURNEYMAP, firstSession, firstLevel, List.of(oldPlayer), 0);

        state.beginSession(secondSession, secondLevel);
        state.replace(ExternalPlayerState.Source.JOURNEYMAP, firstSession, firstLevel, List.of(oldPlayer), SECOND);
        equal(List.of(), state.snapshot(secondSession, secondLevel, SECOND), "a prior session cannot repopulate state");
        equal(List.of(), state.snapshot(firstSession, firstLevel, SECOND), "prior session cannot read the current snapshot");

        PlayerPosition currentPlayer = player("current", "CurrentPlayer", 4, 5, 6);
        state.replace(ExternalPlayerState.Source.XAERO, secondSession, firstLevel, List.of(currentPlayer), 2 * SECOND);
        equal(List.of(), state.snapshot(secondSession, secondLevel, 2 * SECOND), "a snapshot from a prior level is rejected");
        state.replace(ExternalPlayerState.Source.XAERO, secondSession, secondLevel, List.of(currentPlayer), 3 * SECOND);
        equal(List.of(currentPlayer), state.snapshot(secondSession, secondLevel, 3 * SECOND), "current session and level are accepted");
    }

    private static void journeyMapWinsDuplicatePlayersUntilItsSnapshotExpires() {
        ExternalPlayerState state = new ExternalPlayerState();
        Object session = new Object();
        Object level = new Object();
        state.beginSession(session, level);
        UUID id = id("duplicate");
        PlayerPosition xaero = new PlayerPosition(id, "FarPlayer", DIMENSION, 10, 64, 10);
        PlayerPosition journeyMap = new PlayerPosition(id, "FarPlayer", DIMENSION, 20, 65, 20);
        state.replace(ExternalPlayerState.Source.XAERO, session, level, List.of(xaero), SECOND);
        state.replace(ExternalPlayerState.Source.JOURNEYMAP, session, level, List.of(journeyMap), 0);
        equal(List.of(journeyMap), state.snapshot(session, level, SECOND), "JourneyMap source has precedence for duplicates");
        equal(List.of(xaero), state.snapshot(session, level, 15 * SECOND), "lower priority source remains available after the higher source expires");
    }

    private static void snapshotsExpireAtFifteenSeconds() {
        ExternalPlayerState state = new ExternalPlayerState();
        Object session = new Object();
        Object level = new Object();
        state.beginSession(session, level);
        PlayerPosition player = player("expiring", "Expiring", 0, 64, 0);
        state.replace(ExternalPlayerState.Source.BLUEMAP, session, level, List.of(player), 2 * SECOND);

        equal(List.of(player), state.snapshot(session, level, 16_999_999_999L), "snapshot remains available for less than 15 seconds");
        equal(List.of(), state.snapshot(session, level, 17 * SECOND), "snapshot expires at 15 seconds");
        equal(List.of(), state.snapshot(session, level, 17 * SECOND + SECOND), "expired snapshot stays absent");
    }

    private static void snapshotsAreBoundedAndIgnoreNullEntries() {
        ExternalPlayerState state = new ExternalPlayerState();
        Object session = new Object();
        Object level = new Object();
        state.beginSession(session, level);
        List<PlayerPosition> input = new ArrayList<>();
        input.add(null);
        for (int i = 0; i < ExternalPlayerState.MAX_PLAYERS + 8; i++) {
            input.add(player("bounded-" + i, "Player" + (i % 10), i, 64, i));
        }

        state.replace(ExternalPlayerState.Source.XAERO, session, level, input, 0);
        List<PlayerPosition> result = state.snapshot(session, level, 0);
        equal(ExternalPlayerState.MAX_PLAYERS, result.size(), "source snapshot is capped");
        check(result.stream().noneMatch(java.util.Objects::isNull), "null/malformed entries are discarded");
    }

    private static PlayerPosition player(String key, String name, int x, int y, int z) {
        return new PlayerPosition(id(key), name, DIMENSION, x, y, z);
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
