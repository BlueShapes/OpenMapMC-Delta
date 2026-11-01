// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ServerSnapshotTest {
    private static int checks;

    public static void main(String[] args) {
        PlayerPosition player = new PlayerPosition(UUID.randomUUID(), "Steve", "minecraft:overworld", 123, 64, -456);
        var mutable = new ArrayList<>(List.of(player));
        ServerSnapshot snapshot = new ServerSnapshot(false, mutable);
        mutable.clear();
        check(snapshot.players().size() == 1, "snapshot must copy source list");
        check(snapshot.equals(ServerSnapshot.decode(snapshot.encode())), "wire round trip");
        snapshot.apply();
        check(!ClientServerState.isCaveAllowed() && ClientServerState.players().equals(List.of(player)), "server policy and players apply together");
        reject(() -> ClientServerState.players().add(player), "immutable client snapshot");
        reject(() -> new ServerSnapshot(true, List.of(player, player)), "duplicate UUIDs");
        reject(() -> new PlayerPosition(player.id(), "a".repeat(17), player.dimension(), 0, 0, 0), "oversized player name");
        reject(() -> new PlayerPosition(player.id(), player.name(), "../invalid", 0, 0, 0), "invalid dimension");
        reject(() -> new PlayerPosition(player.id(), player.name(), player.dimension(), Integer.MIN_VALUE, 0, 0), "overflow-safe coordinate bounds");
        for (int end = 0; end < snapshot.encode().length; end++) {
            byte[] truncated = Arrays.copyOf(snapshot.encode(), end);
            reject(() -> ServerSnapshot.decode(truncated), "truncated packet");
        }
        byte[] valid = snapshot.encode();
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        reject(() -> ServerSnapshot.decode(trailing), "trailing bytes");
        byte[] version = valid.clone(); version[0] = 2;
        reject(() -> ServerSnapshot.decode(version), "unknown protocol version");
        byte[] policy = valid.clone(); policy[1] = 2;
        reject(() -> ServerSnapshot.decode(policy), "invalid boolean policy");
        byte[] count = valid.clone(); ByteBuffer.wrap(count).putInt(2, Integer.MAX_VALUE);
        reject(() -> ServerSnapshot.decode(count), "bounded player allocation");
        byte[] nameLength = valid.clone(); ByteBuffer.wrap(nameLength).putInt(22, 17);
        reject(() -> ServerSnapshot.decode(nameLength), "bounded name allocation");
        reject(() -> ServerSnapshot.decode(new byte[ServerSnapshot.MAX_BYTES + 1]), "bounded packet allocation");
        // Invalid snapshots leave the last accepted state intact.
        check(!ClientServerState.isCaveAllowed() && ClientServerState.players().size() == 1, "invalid packet cannot relax policy");
        ClientServerState.reset();
        check(ClientServerState.isCaveAllowed() && ClientServerState.players().isEmpty(), "disconnect restores vanilla fallback");
        System.out.println("ServerSnapshotTest: " + checks + " checks passed");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void reject(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException | UnsupportedOperationException expected) { return; }
        throw new AssertionError(message);
    }
}
