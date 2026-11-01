// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Bounded response. Incomplete responses mean structure references could not all be checked. */
public record LocationContextResponse(UUID requestId, String dimension, int x, int y, int z,
                                      boolean available, boolean complete, String biomeId,
                                      List<String> structureIds) {
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 16_384;
    public static final int MAX_STRUCTURE_IDS = 64;

    public LocationContextResponse {
        if (requestId == null || requestId.getMostSignificantBits() == 0L && requestId.getLeastSignificantBits() == 0L)
            throw new IllegalArgumentException("Invalid response token");
        LocationContextRequest.validateId(dimension, "dimension");
        if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000
                || y < -2048 || y > 4096) throw new IllegalArgumentException("Coordinates outside supported bounds");
        biomeId = biomeId == null ? "" : biomeId;
        if (!biomeId.isEmpty()) LocationContextRequest.validateId(biomeId, "biome ID");
        if (structureIds == null || structureIds.size() > MAX_STRUCTURE_IDS) throw new IllegalArgumentException("Too many structures");
        var ordered = new TreeSet<String>();
        for (String id : structureIds) {
            LocationContextRequest.validateId(id, "structure ID");
            if (!ordered.add(id)) throw new IllegalArgumentException("Duplicate structure ID");
        }
        structureIds = List.copyOf(ordered);
        if (!available && (complete || !biomeId.isEmpty() || !structureIds.isEmpty()))
            throw new IllegalArgumentException("Unavailable response cannot contain context");
    }

    public static LocationContextResponse unavailable(LocationContextRequest request) {
        return new LocationContextResponse(request.requestId(), request.dimension(), request.x(), request.y(), request.z(),
                false, false, "", List.of());
    }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream(64 + structureIds.size() * 32);
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeLong(requestId.getMostSignificantBits());
            out.writeLong(requestId.getLeastSignificantBits());
            LocationContextRequest.writeId(out, dimension);
            out.writeInt(x); out.writeInt(y); out.writeInt(z);
            out.writeBoolean(available); out.writeBoolean(complete);
            out.writeBoolean(!biomeId.isEmpty());
            if (!biomeId.isEmpty()) LocationContextRequest.writeId(out, biomeId);
            out.writeByte(structureIds.size());
            for (String id : structureIds) LocationContextRequest.writeId(out, id);
            out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("Response too large");
            return result;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static LocationContextResponse decode(byte[] bytes) {
        if (bytes == null || bytes.length < 38 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid response length");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported response version");
            var id = new UUID(in.readLong(), in.readLong());
            String dimension = LocationContextRequest.readId(in);
            int x = in.readInt(), y = in.readInt(), z = in.readInt();
            boolean available = in.readBoolean(), complete = in.readBoolean();
            String biome = in.readBoolean() ? LocationContextRequest.readId(in) : "";
            int count = in.readUnsignedByte();
            if (count > MAX_STRUCTURE_IDS) throw new IllegalArgumentException("Too many structures");
            var structures = new java.util.ArrayList<String>(count);
            for (int i = 0; i < count; i++) structures.add(LocationContextRequest.readId(in));
            if (in.available() != 0) throw new IllegalArgumentException("Trailing response bytes");
            return new LocationContextResponse(id, dimension, x, y, z, available, complete, biome, structures);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Malformed response", malformed);
        }
    }
}
