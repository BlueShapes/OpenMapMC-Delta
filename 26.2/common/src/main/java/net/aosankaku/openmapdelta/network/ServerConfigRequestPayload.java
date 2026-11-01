// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.server.ServerConfigRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Optional, size-bounded client-to-server configuration request. */
public record ServerConfigRequestPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<ServerConfigRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "server_config_request"));
    public static final StreamCodec<FriendlyByteBuf, ServerConfigRequestPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByteArray(payload.data()),
            buffer -> new ServerConfigRequestPayload(buffer.readByteArray(ServerConfigRequest.MAX_BYTES)));

    public ServerConfigRequestPayload {
        if (data == null || data.length > ServerConfigRequest.MAX_BYTES) {
            throw new IllegalArgumentException("Server configuration request exceeds limit");
        }
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public Type<ServerConfigRequestPayload> type() { return TYPE; }
    public static ServerConfigRequestPayload of(ServerConfigRequest request) { return new ServerConfigRequestPayload(request.encode()); }
}
