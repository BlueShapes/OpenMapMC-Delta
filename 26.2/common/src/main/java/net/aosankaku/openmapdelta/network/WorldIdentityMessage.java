// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Loader-independent, fixed-size world identity packet. */
public record WorldIdentityMessage(UUID worldId) {
    public static final int VERSION = 1;
    public static final int ENCODED_BYTES = 17;

    public WorldIdentityMessage {
        if (worldId == null || (worldId.getMostSignificantBits() == 0L && worldId.getLeastSignificantBits() == 0L)) {
            throw new IllegalArgumentException("World identity must be a nonzero UUID");
        }
    }

    public byte[] encode() {
        return ByteBuffer.allocate(ENCODED_BYTES)
                .put((byte) VERSION)
                .putLong(worldId.getMostSignificantBits())
                .putLong(worldId.getLeastSignificantBits())
                .array();
    }

    public static WorldIdentityMessage decode(byte[] bytes) {
        if (bytes == null || bytes.length != ENCODED_BYTES) throw new IllegalArgumentException("Invalid world identity packet length");
        ByteBuffer input = ByteBuffer.wrap(bytes);
        if (Byte.toUnsignedInt(input.get()) != VERSION) throw new IllegalArgumentException("Unsupported world identity packet version");
        return new WorldIdentityMessage(new UUID(input.getLong(), input.getLong()));
    }
}
