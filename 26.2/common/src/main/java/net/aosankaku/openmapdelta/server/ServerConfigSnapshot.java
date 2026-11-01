// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import java.io.*;

/** Only administrators receive settings; every response carries authoritative state. */
public record ServerConfigSnapshot(long revision, boolean administrator, ServerConfigValues values, Result result) {
    public static final int MAX_BYTES = 8192;
    public enum Result { NONE, SAVED, DENIED, CONFLICT, INVALID, STORAGE_ERROR, RATE_LIMITED }
    public ServerConfigSnapshot {
        if (revision < 0 || result == null || administrator != (values != null)) {
            throw new IllegalArgumentException("Invalid settings snapshot");
        }
    }
    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(1);
            out.writeLong(revision);
            out.writeBoolean(administrator);
            out.writeByte(result.ordinal());
            if (administrator) ServerConfigValues.write(out, values);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static ServerConfigSnapshot decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid settings snapshot size");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != 1) throw new IllegalArgumentException("Unsupported settings snapshot version");
            long revision = in.readLong();
            boolean admin = ServerConfigValues.readBoolean(in);
            int result = in.readUnsignedByte();
            if (result >= Result.values().length) throw new IllegalArgumentException("Invalid settings result");
            var snapshot = new ServerConfigSnapshot(revision, admin,
                    admin ? ServerConfigValues.read(in) : null, Result.values()[result]);
            if (in.available() != 0) throw new IllegalArgumentException("Trailing settings snapshot data");
            return snapshot;
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid settings snapshot", invalid); }
    }
}
