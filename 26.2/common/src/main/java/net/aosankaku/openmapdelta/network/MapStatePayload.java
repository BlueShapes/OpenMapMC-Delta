// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MapStatePayload(ServerSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<MapStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "map_state"));
    public static final StreamCodec<FriendlyByteBuf, MapStatePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByteArray(payload.snapshot().encode()),
            buffer -> new MapStatePayload(ServerSnapshot.decode(buffer.readByteArray(ServerSnapshot.MAX_BYTES))));

    @Override
    public Type<MapStatePayload> type() { return TYPE; }
}
