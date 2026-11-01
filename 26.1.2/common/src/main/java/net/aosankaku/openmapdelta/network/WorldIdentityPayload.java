// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Optional, fixed-size server world identity payload. */
public record WorldIdentityPayload(UUID worldId) implements CustomPacketPayload {
    public static final Type<WorldIdentityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "world_identity"));
    public static final StreamCodec<FriendlyByteBuf, WorldIdentityPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(new WorldIdentityMessage(payload.worldId()).encode()),
            buffer -> {
                if (buffer.readableBytes() != WorldIdentityMessage.ENCODED_BYTES) {
                    throw new IllegalArgumentException("Invalid world identity payload length");
                }
                byte[] bytes = new byte[WorldIdentityMessage.ENCODED_BYTES];
                buffer.readBytes(bytes);
                return new WorldIdentityPayload(WorldIdentityMessage.decode(bytes).worldId());
            });

    public WorldIdentityPayload {
        new WorldIdentityMessage(worldId);
    }

    @Override
    public Type<WorldIdentityPayload> type() { return TYPE; }
}
