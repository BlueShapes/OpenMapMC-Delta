// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import net.aosankaku.openmapdelta.marker.MarkerIconId;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict shared encoding and validation used by both wire packets and the world store. */
final class SharedWaypointData {
    static final int MAX_LABEL_BYTES = 1024;
    static final int MAX_OWNER_NAME_BYTES = 64;
    static final int MAX_DIMENSION_BYTES = 128;
    static final int MAX_ICON_BYTES = 160;
    static final int MAX_CATEGORY_BYTES = 256;
    static final int MAX_LABEL_CODEPOINTS = 256;
    static final int MAX_CATEGORY_CODEPOINTS = 64;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private SharedWaypointData() {}

    static String validateDimension(String value) {
        if (value == null || value.length() > MAX_DIMENSION_BYTES || !RESOURCE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return value;
    }

    static String validateLabel(String value) {
        validateText(value, MAX_LABEL_BYTES, MAX_LABEL_CODEPOINTS, false, "label");
        if (value.trim().isEmpty()) throw new IllegalArgumentException("Empty label");
        return value;
    }

    static String validateOwnerName(String value) {
        validateText(value, MAX_OWNER_NAME_BYTES, 64, false, "owner name");
        if (value.trim().isEmpty()) throw new IllegalArgumentException("Empty owner name");
        return value;
    }

    static String validateIcon(String value) {
        if (value == null || value.length() > MAX_ICON_BYTES) throw new IllegalArgumentException("Invalid icon");
        if (!value.isEmpty() && !MarkerIconId.normalize(value).equals(value)) throw new IllegalArgumentException("Invalid icon");
        return value;
    }

    static String validateCategory(String value) {
        if (value == null) value = "";
        validateText(value, MAX_CATEGORY_BYTES, MAX_CATEGORY_CODEPOINTS, true, "category");
        return value.trim();
    }

    static void validateCoordinates(int x, int y, int z) {
        if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000 || y < -2048 || y > 4096) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
    }

    static void validateText(String value, int maxBytes, int maxCodepoints, boolean allowEmpty, String field) {
        if (value == null || (!allowEmpty && value.isEmpty()) || value.codePointCount(0, value.length()) > maxCodepoints) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) throw new IllegalArgumentException("Invalid Unicode");
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Invalid Unicode");
            }
            int codepoint = value.codePointAt(i);
            if (Character.isISOControl(codepoint)) throw new IllegalArgumentException("Control character");
        }
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            if (encoded.remaining() > maxBytes) throw new IllegalArgumentException("String too long");
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Invalid Unicode", malformed);
        }
    }

    static void writeString(DataOutput output, String value, int maxBytes, int maxCodepoints, boolean allowEmpty) throws IOException {
        validateText(value, maxBytes, maxCodepoints, allowEmpty, "string");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) throw new IllegalArgumentException("String too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    static String readString(DataInput input, int maxBytes, int maxCodepoints, boolean allowEmpty) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maxBytes || (!allowEmpty && length == 0)) throw new IllegalArgumentException("Invalid string length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            validateText(value, maxBytes, maxCodepoints, allowEmpty, "string");
            return value;
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Invalid UTF-8", malformed);
        }
    }

    static void writeUuid(DataOutput output, UUID id) throws IOException {
        if (id == null || (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L)) throw new IllegalArgumentException("Invalid UUID");
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    static UUID readUuid(DataInput input) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        if (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L) throw new IllegalArgumentException("Invalid UUID");
        return id;
    }

    static void writeWaypoint(DataOutput output, SharedWaypoint waypoint) throws IOException {
        writeUuid(output, waypoint.id());
        writeUuid(output, waypoint.ownerId());
        writeString(output, waypoint.ownerName(), MAX_OWNER_NAME_BYTES, 64, false);
        writeString(output, waypoint.dimension(), MAX_DIMENSION_BYTES, MAX_DIMENSION_BYTES, false);
        validateCoordinates(waypoint.x(), waypoint.y(), waypoint.z());
        output.writeInt(waypoint.x());
        output.writeInt(waypoint.y());
        output.writeInt(waypoint.z());
        writeString(output, waypoint.label(), MAX_LABEL_BYTES, MAX_LABEL_CODEPOINTS, false);
        output.writeInt(waypoint.color());
        writeString(output, waypoint.iconId(), MAX_ICON_BYTES, MAX_ICON_BYTES, true);
        writeString(output, waypoint.category(), MAX_CATEGORY_BYTES, MAX_CATEGORY_CODEPOINTS, true);
    }

    static SharedWaypoint readWaypoint(DataInput input) throws IOException {
        UUID id = readUuid(input);
        UUID owner = readUuid(input);
        String ownerName = validateOwnerName(readString(input, MAX_OWNER_NAME_BYTES, 64, false));
        String dimension = validateDimension(readString(input, MAX_DIMENSION_BYTES, MAX_DIMENSION_BYTES, false));
        int x = input.readInt();
        int y = input.readInt();
        int z = input.readInt();
        validateCoordinates(x, y, z);
        String label = validateLabel(readString(input, MAX_LABEL_BYTES, MAX_LABEL_CODEPOINTS, false));
        int color = input.readInt();
        String icon = validateIcon(readString(input, MAX_ICON_BYTES, MAX_ICON_BYTES, true));
        String category = validateCategory(readString(input, MAX_CATEGORY_BYTES, MAX_CATEGORY_CODEPOINTS, true));
        return new SharedWaypoint(id, owner, ownerName, dimension, x, y, z, label, color, icon, category);
    }
}
