// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.shared.SharedWaypointFeedback;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.aosankaku.openmapdelta.shared.SharedWaypointSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Single optional play channel with bounded discriminated request, snapshot, and feedback bodies. */
public record SharedWaypointPayload(int kind, byte[] data) implements CustomPacketPayload {
    public static final int SNAPSHOT = 0, FEEDBACK = 1, REQUEST = 2;
    public static final Type<SharedWaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "shared_waypoints"));
    public static final StreamCodec<FriendlyByteBuf, SharedWaypointPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeByte(payload.kind()); buffer.writeByteArray(payload.data()); },
            buffer -> {
                int kind = buffer.readUnsignedByte();
                int limit = limit(kind);
                return new SharedWaypointPayload(kind, buffer.readByteArray(limit));
            });

    public SharedWaypointPayload {
        int max = limit(kind);
        if (data == null || data.length > max) throw new IllegalArgumentException("Shared waypoint payload exceeds limit");
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public Type<SharedWaypointPayload> type() { return TYPE; }

    public static SharedWaypointPayload snapshot(SharedWaypointSnapshot value) { return new SharedWaypointPayload(SNAPSHOT, value.encode()); }
    public static SharedWaypointPayload feedback(SharedWaypointFeedback value) { return new SharedWaypointPayload(FEEDBACK, value.encode()); }
    public static SharedWaypointPayload request(SharedWaypointRequest value) { return new SharedWaypointPayload(REQUEST, value.encode()); }
    public SharedWaypointSnapshot snapshot() { require(SNAPSHOT); return SharedWaypointSnapshot.decode(data); }
    public SharedWaypointFeedback feedback() { require(FEEDBACK); return SharedWaypointFeedback.decode(data); }
    public SharedWaypointRequest request() { require(REQUEST); return SharedWaypointRequest.decode(data); }

    private void require(int expected) { if (kind != expected) throw new IllegalStateException("Unexpected shared waypoint payload kind"); }
    private static int limit(int kind) {
        return switch (kind) {
            case SNAPSHOT -> SharedWaypointSnapshot.MAX_BYTES;
            case FEEDBACK -> SharedWaypointFeedback.MAX_BYTES;
            case REQUEST -> SharedWaypointRequest.MAX_BYTES;
            default -> throw new IllegalArgumentException("Unknown shared waypoint payload kind");
        };
    }
}
