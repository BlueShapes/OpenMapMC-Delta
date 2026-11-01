// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Optional, size-bounded server-to-client configuration snapshot. */
public record ServerConfigSnapshotPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<ServerConfigSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "server_config_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, ServerConfigSnapshotPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByteArray(payload.data()),
            buffer -> new ServerConfigSnapshotPayload(buffer.readByteArray(ServerConfigSnapshot.MAX_BYTES)));

    public ServerConfigSnapshotPayload {
        if (data == null || data.length > ServerConfigSnapshot.MAX_BYTES) {
            throw new IllegalArgumentException("Server configuration snapshot exceeds limit");
        }
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public Type<ServerConfigSnapshotPayload> type() { return TYPE; }
    public static ServerConfigSnapshotPayload of(ServerConfigSnapshot snapshot) { return new ServerConfigSnapshotPayload(snapshot.encode()); }
}
