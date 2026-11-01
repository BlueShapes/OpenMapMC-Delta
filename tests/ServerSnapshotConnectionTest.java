// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.util.List;
import java.util.UUID;

/** A delayed player/policy snapshot must not replace the next server's accepted state. */
public final class ServerSnapshotConnectionTest {
    public static void main(String[] args) {
        Object oldConnection = new Object();
        Object newConnection = new Object();
        var oldPlayer = new PlayerPosition(UUID.randomUUID(), "Old", "minecraft:overworld", 10, 64, 20);
        var newPlayer = new PlayerPosition(UUID.randomUUID(), "New", "minecraft:overworld", 30, 70, 40);
        var oldSnapshot = new ServerSnapshot(true, List.of(oldPlayer));
        var newSnapshot = new ServerSnapshot(false, List.of(newPlayer));

        ClientServerState.reset();
        check(newSnapshot.applyFromConnection(newConnection, newConnection), "current connection accepts snapshot");
        check(!oldSnapshot.applyFromConnection(oldConnection, newConnection), "queued previous-server snapshot rejected");
        check(!ClientServerState.isCaveAllowed() && ClientServerState.players().equals(List.of(newPlayer)),
                "rejected snapshot preserves current players and cave policy");
        check(!oldSnapshot.applyFromConnection(null, null), "two absent connections are not an authenticated session");
        check(!oldSnapshot.applyFromConnection(null, newConnection), "missing packet origin rejected");

        ClientServerState.reset();
        check(!oldSnapshot.applyFromConnection(oldConnection, null), "queued snapshot after disconnect rejected");
        check(ClientServerState.isCaveAllowed() && ClientServerState.players().isEmpty(),
                "late snapshot cannot repopulate disconnected session");
        check(newSnapshot.applyFromConnection(newConnection, newConnection), "reconnected session still updates normally");
        System.out.println("Server snapshot connection tests passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
