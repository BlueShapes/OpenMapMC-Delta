// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Client request deliberately has no owner identity; the server binds it to the network sender. */
public final class SharedWaypointRequest {
    public static final int MAX_BYTES = 2048;
    private static final int VERSION = 1;
    public enum Operation { CREATE, UPDATE, DELETE }

    private final Operation operation;
    private final UUID id;
    private final String dimension;
    private final int x, y, z;
    private final String label;
    private final int color;
    private final String iconId;
    private final String category;

    private SharedWaypointRequest(Operation operation, UUID id, String dimension, int x, int y, int z,
                                  String label, int color, String iconId, String category) {
        this.operation = java.util.Objects.requireNonNull(operation);
        this.id = id;
        this.dimension = dimension;
        this.x = x; this.y = y; this.z = z;
        this.label = label;
        this.color = color;
        this.iconId = iconId;
        this.category = category;
        if (operation == Operation.DELETE) {
            if (id == null || isZero(id)) throw new IllegalArgumentException("Invalid waypoint id");
        } else {
            if ((operation == Operation.CREATE && id != null) || (operation == Operation.UPDATE && (id == null || isZero(id)))) throw new IllegalArgumentException("Invalid request id");
            SharedWaypointData.validateDimension(dimension);
            SharedWaypointData.validateCoordinates(x, y, z);
            SharedWaypointData.validateLabel(label);
            SharedWaypointData.validateIcon(iconId);
            SharedWaypointData.validateCategory(category);
        }
    }

    public static SharedWaypointRequest create(String dimension, int x, int y, int z, String label, int color, String iconId, String category) {
        return new SharedWaypointRequest(Operation.CREATE, null, dimension, x, y, z, label, color, iconId, category);
    }
    public static SharedWaypointRequest update(UUID id, String dimension, int x, int y, int z, String label, int color, String iconId, String category) {
        return new SharedWaypointRequest(Operation.UPDATE, id, dimension, x, y, z, label, color, iconId, category);
    }
    public static SharedWaypointRequest delete(UUID id) {
        return new SharedWaypointRequest(Operation.DELETE, id, null, 0, 0, 0, null, 0, null, null);
    }

    public Operation operation() { return operation; }
    public UUID id() { return id; }
    public String dimension() { return dimension; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String label() { return label; }
    public int color() { return color; }
    public String iconId() { return iconId; }
    public String category() { return category; }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(operation.ordinal());
            if (operation != Operation.CREATE) SharedWaypointData.writeUuid(out, id);
            if (operation != Operation.DELETE) {
                SharedWaypointData.writeString(out, dimension, SharedWaypointData.MAX_DIMENSION_BYTES, SharedWaypointData.MAX_DIMENSION_BYTES, false);
                out.writeInt(x); out.writeInt(y); out.writeInt(z);
                SharedWaypointData.writeString(out, label, SharedWaypointData.MAX_LABEL_BYTES, SharedWaypointData.MAX_LABEL_CODEPOINTS, false);
                out.writeInt(color);
                SharedWaypointData.writeString(out, iconId, SharedWaypointData.MAX_ICON_BYTES, SharedWaypointData.MAX_ICON_BYTES, true);
                SharedWaypointData.writeString(out, category, SharedWaypointData.MAX_CATEGORY_BYTES, SharedWaypointData.MAX_CATEGORY_CODEPOINTS, true);
            }
            out.flush();
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Request too large");
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static SharedWaypointRequest decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Request too large");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported request version");
            int op = in.readUnsignedByte();
            if (op < 0 || op >= Operation.values().length) throw new IllegalArgumentException("Invalid request operation");
            Operation operation = Operation.values()[op];
            UUID id = operation == Operation.CREATE ? null : SharedWaypointData.readUuid(in);
            SharedWaypointRequest result;
            if (operation == Operation.DELETE) result = delete(id);
            else {
                String dimension = SharedWaypointData.validateDimension(SharedWaypointData.readString(in, SharedWaypointData.MAX_DIMENSION_BYTES, SharedWaypointData.MAX_DIMENSION_BYTES, false));
                int x = in.readInt(), y = in.readInt(), z = in.readInt();
                SharedWaypointData.validateCoordinates(x, y, z);
                String label = SharedWaypointData.validateLabel(SharedWaypointData.readString(in, SharedWaypointData.MAX_LABEL_BYTES, SharedWaypointData.MAX_LABEL_CODEPOINTS, false));
                int color = in.readInt();
                String icon = SharedWaypointData.validateIcon(SharedWaypointData.readString(in, SharedWaypointData.MAX_ICON_BYTES, SharedWaypointData.MAX_ICON_BYTES, true));
                String category = SharedWaypointData.validateCategory(SharedWaypointData.readString(in, SharedWaypointData.MAX_CATEGORY_BYTES, SharedWaypointData.MAX_CATEGORY_CODEPOINTS, true));
                result = operation == Operation.CREATE ? create(dimension, x, y, z, label, color, icon, category)
                        : update(id, dimension, x, y, z, label, color, icon, category);
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing request data");
            return result;
        } catch (IOException malformed) { throw new IllegalArgumentException("Truncated request", malformed); }
    }

    private static boolean isZero(UUID id) { return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L; }
}
