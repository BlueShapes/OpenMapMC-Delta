// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.death.DeathPointUpdate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Separate optional S2C capability, so older waypoint clients never receive an unfamiliar report. */
public record DeathPointPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<DeathPointPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "deathpoints"));
    public static final StreamCodec<FriendlyByteBuf, DeathPointPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByteArray(payload.data()),
            buffer -> new DeathPointPayload(buffer.readByteArray(DeathPointUpdate.MAX_BYTES)));
    public DeathPointPayload {
        if (data == null || data.length > DeathPointUpdate.MAX_BYTES)
            throw new IllegalArgumentException("Invalid deathpoint payload length");
        data = data.clone();
    }
    @Override public byte[] data() { return data.clone(); }
    @Override public Type<DeathPointPayload> type() { return TYPE; }
    public static DeathPointPayload of(DeathPointUpdate report) { return new DeathPointPayload(report.encode()); }
    public DeathPointUpdate report() { return DeathPointUpdate.decode(data); }
}
