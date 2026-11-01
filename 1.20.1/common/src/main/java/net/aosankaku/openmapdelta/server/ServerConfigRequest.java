// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import java.io.*;

/** No client-supplied actor or permission; the loader binds this to its authenticated player. */
public record ServerConfigRequest(long revision, ServerConfigValues values) {
    public static final int MAX_BYTES = 8192;
    public ServerConfigRequest {
        if (revision < 0 || values == null) throw new IllegalArgumentException("Invalid settings request");
    }
    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(1);
            out.writeLong(revision);
            ServerConfigValues.write(out, values);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static ServerConfigRequest decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid settings request size");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != 1) throw new IllegalArgumentException("Unsupported settings request version");
            var request = new ServerConfigRequest(in.readLong(), ServerConfigValues.read(in));
            if (in.available() != 0) throw new IllegalArgumentException("Trailing settings request data");
            return request;
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid settings request", invalid); }
    }
}
