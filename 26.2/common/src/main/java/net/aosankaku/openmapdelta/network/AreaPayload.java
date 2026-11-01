// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.area.AreaWire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Optional, bounded channel dedicated to shared surface areas. */
public record AreaPayload(int kind, byte[] data) implements CustomPacketPayload {
    public static final int SNAPSHOT = 0;
    public static final int FEEDBACK = 1;
    public static final int REQUEST = 2;
    public static final Type<AreaPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "areas"));
    public static final StreamCodec<FriendlyByteBuf, AreaPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.kind());
                buffer.writeByteArray(payload.data());
            },
            buffer -> {
                int kind = buffer.readUnsignedByte();
                return new AreaPayload(kind, buffer.readByteArray(limit(kind)));
            });

    public AreaPayload {
        if (data == null || data.length > limit(kind)) throw new IllegalArgumentException("Area payload exceeds its limit");
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public Type<AreaPayload> type() { return TYPE; }

    public static AreaPayload request(AreaWire.Request value) { return new AreaPayload(REQUEST, AreaWire.encode(value)); }
    public static AreaPayload snapshot(AreaWire.Snapshot value) { return new AreaPayload(SNAPSHOT, AreaWire.encode(value)); }
    public static AreaPayload feedback(AreaWire.Feedback value) { return new AreaPayload(FEEDBACK, AreaWire.encode(value)); }
    public AreaWire.Request request() { require(REQUEST); return AreaWire.decodeRequest(data); }
    public AreaWire.Snapshot snapshot() { require(SNAPSHOT); return AreaWire.decodeSnapshot(data); }
    public AreaWire.Feedback feedback() { require(FEEDBACK); return AreaWire.decodeFeedback(data); }

    private void require(int expected) {
        if (kind != expected) throw new IllegalStateException("Unexpected area payload kind");
    }

    private static int limit(int kind) {
        return switch (kind) {
            case SNAPSHOT -> AreaWire.MAX_SNAPSHOT_BYTES;
            case FEEDBACK -> AreaWire.MAX_FEEDBACK_BYTES;
            case REQUEST -> AreaWire.MAX_REQUEST_BYTES;
            default -> throw new IllegalArgumentException("Unknown area payload kind");
        };
    }
}
