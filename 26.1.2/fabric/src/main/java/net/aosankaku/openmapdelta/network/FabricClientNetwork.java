// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.aosankaku.openmapdelta.mixin.ClientLoginConnectionAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

public final class FabricClientNetwork {
    private FabricClientNetwork() {}

    public static void init() {
        SharedWaypointClient.configureSender(request -> ClientPlayNetworking.send(SharedWaypointPayload.request(request)));
        LocationContextClient.configureSender(request -> {
            if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(LocationContextPayload.TYPE)) return false;
            ClientPlayNetworking.send(LocationContextPayload.request(request));
            return true;
        });
        ClientLoginNetworking.registerGlobalReceiver(FabricServerNetwork.WORLD_IDENTITY_SUPPORT_CHANNEL,
                (client, handler, buffer, listenerAdder) -> {
                    var connectionToken = ((ClientLoginConnectionAccessor) handler).odysseymap$getConnection();
                    if (buffer.readableBytes() != 1 || buffer.readUnsignedByte() != 1) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<FriendlyByteBuf> response = new CompletableFuture<>();
                    client.execute(() -> {
                        ClientWorldIdentityState.markExpected(connectionToken);
                        response.complete(new FriendlyByteBuf(Unpooled.buffer()));
                    });
                    return response;
                });
        ClientPlayNetworking.registerGlobalReceiver(MapStatePayload.TYPE, (payload, context) ->
                context.client().execute(payload.snapshot()::apply));
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            var packetConnection = handler.getConnection();
            ClientPlayNetworking.registerReceiver(WorldIdentityPayload.TYPE, (payload, context) -> {
                var worldId = payload.worldId();
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                    ClientWorldIdentityState.apply(packetConnection, currentConnection, worldId);
                });
            });
            ClientPlayNetworking.registerReceiver(SharedWaypointPayload.TYPE, (payload, context) -> {
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                    try {
                        if (payload.kind() == SharedWaypointPayload.SNAPSHOT) {
                            SharedWaypointClient.apply(packetConnection, currentConnection, payload.snapshot());
                        } else if (payload.kind() == SharedWaypointPayload.FEEDBACK) {
                            SharedWaypointClient.feedback(packetConnection, currentConnection, payload.feedback());
                        }
                    } catch (IllegalArgumentException malformed) {
                        // Invalid payloads never replace an accepted complete server snapshot.
                    }
                });
            });
            ClientPlayNetworking.registerReceiver(LocationContextPayload.TYPE, (payload, context) -> {
                if (payload.kind() != LocationContextPayload.RESPONSE) return;
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                    try {
                        LocationContextClient.apply(packetConnection, currentConnection, payload.response());
                    } catch (IllegalArgumentException malformed) {
                        // Invalid replies leave the pending notification unchanged.
                    }
                });
            });
        });
    }
}
