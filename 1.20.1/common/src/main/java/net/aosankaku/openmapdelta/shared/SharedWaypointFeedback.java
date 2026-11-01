// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

/** Small translated response. Keys are allowlisted so the server cannot inject client translation keys. */
public record SharedWaypointFeedback(boolean success, String messageKey) {
    public static final int MAX_BYTES = 96;
    private static final int VERSION = 1;
    private static final Set<String> KEYS = Set.of(
            "odysseymap.shared.created", "odysseymap.shared.updated", "odysseymap.shared.deleted",
            "odysseymap.shared.denied", "odysseymap.shared.unavailable", "odysseymap.shared.invalid",
            "odysseymap.shared.full", "odysseymap.shared.rate_limited", "odysseymap.shared.storage_error");

    public SharedWaypointFeedback {
        if (!KEYS.contains(messageKey)) throw new IllegalArgumentException("Unknown feedback key");
    }
    public static SharedWaypointFeedback success(String key) { return new SharedWaypointFeedback(true, key); }
    public static SharedWaypointFeedback failure(String key) { return new SharedWaypointFeedback(false, key); }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION); out.writeBoolean(success);
            byte[] key = messageKey.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            out.writeByte(key.length); out.write(key);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static SharedWaypointFeedback decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Feedback too large");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported feedback version");
            int status = in.readUnsignedByte();
            if (status > 1) throw new IllegalArgumentException("Invalid feedback status");
            int length = in.readUnsignedByte();
            if (length < 1 || length > 80) throw new IllegalArgumentException("Invalid feedback key length");
            byte[] key = new byte[length]; in.readFully(key);
            for (byte b : key) if (b < 0) throw new IllegalArgumentException("Invalid feedback key encoding");
            if (in.available() != 0) throw new IllegalArgumentException("Trailing feedback data");
            return new SharedWaypointFeedback(status == 1, new String(key, java.nio.charset.StandardCharsets.US_ASCII));
        } catch (IOException malformed) { throw new IllegalArgumentException("Truncated feedback", malformed); }
    }
}
