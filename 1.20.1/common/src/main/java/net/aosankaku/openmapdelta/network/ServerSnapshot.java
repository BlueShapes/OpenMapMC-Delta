// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Loader-independent wire format with strict allocation and coordinate limits. */
public record ServerSnapshot(boolean caveAllowed, List<PlayerPosition> players) {
    public static final int MAX_BYTES = 800_000;
    private static final int VERSION = 1;

    public ServerSnapshot {
        if (players.size() > ClientServerState.MAX_PLAYERS) throw new IllegalArgumentException("Too many players");
        players = List.copyOf(players);
        var ids = new HashSet<UUID>();
        for (PlayerPosition player : players) {
            if (!ids.add(player.id())) throw new IllegalArgumentException("Duplicate player");
        }
    }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeBoolean(caveAllowed);
            out.writeInt(players.size());
            for (PlayerPosition player : players) {
                out.writeLong(player.id().getMostSignificantBits());
                out.writeLong(player.id().getLeastSignificantBits());
                writeString(out, player.name());
                writeString(out, player.dimension());
                out.writeInt(player.x());
                out.writeInt(player.y());
                out.writeInt(player.z());
            }
            out.flush();
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Snapshot too large");
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static ServerSnapshot decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Snapshot too large");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported snapshot version");
            int policy = in.readUnsignedByte();
            if (policy > 1) throw new IllegalArgumentException("Invalid cave policy");
            int count = in.readInt();
            if (count < 0 || count > ClientServerState.MAX_PLAYERS) throw new IllegalArgumentException("Invalid player count");
            var players = new ArrayList<PlayerPosition>(count);
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(in.readLong(), in.readLong());
                String name = readString(in, 16);
                String dimension = readString(in, 128);
                players.add(new PlayerPosition(id, name, dimension, in.readInt(), in.readInt(), in.readInt()));
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing snapshot data");
            return new ServerSnapshot(policy == 1, players);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Truncated snapshot", malformed);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in, int limit) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > limit) throw new IllegalArgumentException("Invalid string length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        for (byte b : bytes) if (b < 0) throw new IllegalArgumentException("Invalid string encoding");
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public void apply() { ClientServerState.apply(caveAllowed, players); }
}
