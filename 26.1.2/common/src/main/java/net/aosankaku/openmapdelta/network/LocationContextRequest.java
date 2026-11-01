// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded, loader-independent request for biome and loaded structure context at a point. */
public record LocationContextRequest(UUID requestId, String dimension, int x, int y, int z) {
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 256;
    public static final int MAX_ID_BYTES = 128;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public LocationContextRequest {
        if (requestId == null || isZero(requestId)) throw new IllegalArgumentException("Invalid request token");
        validateId(dimension, "dimension");
        if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000
                || y < -2048 || y > 4096) throw new IllegalArgumentException("Coordinates outside supported bounds");
    }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream(48);
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeLong(requestId.getMostSignificantBits());
            out.writeLong(requestId.getLeastSignificantBits());
            writeId(out, dimension);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("Request too large");
            return result;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static LocationContextRequest decode(byte[] bytes) {
        if (bytes == null || bytes.length < 34 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid request length");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported request version");
            var id = new UUID(in.readLong(), in.readLong());
            String dimension = readId(in);
            int x = in.readInt(), y = in.readInt(), z = in.readInt();
            if (in.available() != 0) throw new IllegalArgumentException("Trailing request bytes");
            return new LocationContextRequest(id, dimension, x, y, z);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Malformed request", malformed);
        }
    }

    static void writeId(DataOutputStream out, String id) throws IOException {
        byte[] value = id.getBytes(StandardCharsets.UTF_8);
        if (value.length == 0 || value.length > MAX_ID_BYTES) throw new IllegalArgumentException("Invalid resource ID length");
        out.writeShort(value.length);
        out.write(value);
    }

    static String readId(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length == 0 || length > MAX_ID_BYTES || length > in.available()) throw new IllegalArgumentException("Invalid resource ID length");
        byte[] value = in.readNBytes(length);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Invalid UTF-8 resource ID", malformed);
        }
    }

    static void validateId(String value, String field) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES
                || !RESOURCE_ID.matcher(value).matches()) throw new IllegalArgumentException("Invalid " + field);
    }

    private static boolean isZero(UUID value) {
        return value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L;
    }
}
