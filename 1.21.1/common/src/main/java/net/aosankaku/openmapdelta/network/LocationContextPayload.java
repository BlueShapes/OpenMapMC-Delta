// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Single optional play channel for bounded notification context requests and responses. */
public record LocationContextPayload(int kind, byte[] data) implements CustomPacketPayload {
    public static final int REQUEST = 0, RESPONSE = 1;
    public static final Type<LocationContextPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "location_context"));
    public static final StreamCodec<FriendlyByteBuf, LocationContextPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeByte(payload.kind()); buffer.writeByteArray(payload.data()); },
            buffer -> {
                int kind = buffer.readUnsignedByte();
                int maximum = limit(kind);
                return new LocationContextPayload(kind, buffer.readByteArray(maximum));
            });

    public LocationContextPayload {
        if (data == null || data.length > limit(kind)) throw new IllegalArgumentException("Location context payload exceeds limit");
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public Type<LocationContextPayload> type() { return TYPE; }
    public static LocationContextPayload request(LocationContextRequest request) { return new LocationContextPayload(REQUEST, request.encode()); }
    public static LocationContextPayload response(LocationContextResponse response) { return new LocationContextPayload(RESPONSE, response.encode()); }
    public LocationContextRequest request() { require(REQUEST); return LocationContextRequest.decode(data); }
    public LocationContextResponse response() { require(RESPONSE); return LocationContextResponse.decode(data); }
    private void require(int expected) { if (kind != expected) throw new IllegalStateException("Unexpected location context payload kind"); }
    private static int limit(int kind) {
        return switch (kind) {
            case REQUEST -> LocationContextRequest.MAX_BYTES;
            case RESPONSE -> LocationContextResponse.MAX_BYTES;
            default -> throw new IllegalArgumentException("Unknown location context payload kind");
        };
    }
}
