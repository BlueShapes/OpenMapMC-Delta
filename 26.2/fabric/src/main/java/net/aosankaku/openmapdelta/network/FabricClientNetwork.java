// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.mixin.ClientLoginConnectionAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.area.AreaClient;
import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

public final class FabricClientNetwork {
    private FabricClientNetwork() {}

    public static void init() {
        // Fabric's canSend describes C2S endpoints; this S2C capability is announced by the join frame.
        net.aosankaku.openmapdelta.death.DeathPointInbox.configureSupportProbe(null);
        SharedWaypointClient.configureSender(request -> ClientPlayNetworking.send(SharedWaypointPayload.request(request)));
        AreaClient.configureSender(request -> {
            if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(AreaPayload.TYPE)) {
                throw new IllegalStateException("Shared area channel is unavailable");
            }
            ClientPlayNetworking.send(AreaPayload.request(request));
        });
        LocationContextClient.configureSender(request -> {
            if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(LocationContextPayload.TYPE)) return false;
            ClientPlayNetworking.send(LocationContextPayload.request(request));
            return true;
        });
        ServerConfigClient.configureSender(request -> {
            if (Minecraft.getInstance().getConnection() == null
                    || !ClientPlayNetworking.canSend(ServerConfigRequestPayload.TYPE)) return;
            ClientPlayNetworking.send(ServerConfigRequestPayload.of(request));
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
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            var packetConnection = handler.getConnection();
            ClientPlayNetworking.registerReceiver(MapStatePayload.TYPE, (payload, context) -> {
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                    payload.snapshot().applyFromConnection(packetConnection, currentConnection);
                });
            });
            ClientPlayNetworking.registerReceiver(DeathPointPayload.TYPE, (payload, context) -> {
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var current = currentHandler == null ? null : currentHandler.getConnection();
                    if (payload.data().length == 0) { net.aosankaku.openmapdelta.death.DeathPointInbox.markSupported(packetConnection, current); return; }
                    try { net.aosankaku.openmapdelta.death.DeathPointInbox.apply(packetConnection, current, payload.report()); }
                    catch (IllegalArgumentException malformed) { /* Ignore malformed owner reports. */ }
                });
            });
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
            ClientPlayNetworking.registerReceiver(AreaPayload.TYPE, (payload, context) -> {
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                    try {
                        if (payload.kind() == AreaPayload.SNAPSHOT) {
                            AreaClient.apply(packetConnection, currentConnection, payload.snapshot());
                        } else if (payload.kind() == AreaPayload.FEEDBACK) {
                            AreaClient.feedback(packetConnection, currentConnection, payload.feedback());
                        }
                    } catch (IllegalArgumentException malformed) {
                        // Malformed or incomplete packets never replace accepted area state.
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
            ClientPlayNetworking.registerReceiver(ServerConfigSnapshotPayload.TYPE, (payload, context) -> {
                byte[] bytes = payload.data();
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                    try {
                        ServerConfigClient.apply(packetConnection, currentConnection,
                                ServerConfigSnapshot.decode(bytes));
                    } catch (IllegalArgumentException malformed) {
                        // Invalid configuration snapshots never alter the active connection state.
                    }
                });
            });
        });
    }
}
