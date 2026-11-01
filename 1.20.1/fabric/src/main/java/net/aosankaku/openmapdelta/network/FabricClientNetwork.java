// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.mixin.ClientLoginConnectionAccessor;
import net.aosankaku.openmapdelta.network.ClientWorldIdentityState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.shared.SharedWaypointFeedback;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.aosankaku.openmapdelta.shared.SharedWaypointSnapshot;
import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.aosankaku.openmapdelta.server.ServerConfigRequest;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.aosankaku.openmapdelta.network.LocationContextRequest;
import net.aosankaku.openmapdelta.network.LocationContextResponse;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

public final class FabricClientNetwork {
    private FabricClientNetwork() {}

    public static void init() {
        net.aosankaku.openmapdelta.death.DeathPointInbox.configureSupportProbe(null);
        SharedWaypointClient.configureSender(FabricClientNetwork::sendSharedWaypointRequest);
        LocationContextClient.configureSender(FabricClientNetwork::sendLocationContextRequest);
        ServerConfigClient.configureSender(FabricClientNetwork::sendServerConfigRequest);
        ClientLoginNetworking.registerGlobalReceiver(FabricServerNetwork.WORLD_IDENTITY_SUPPORT_CHANNEL,
                (client, handler, buffer, listenerAdder) -> {
                    var connectionToken = ((ClientLoginConnectionAccessor) handler).odysseymap$getConnection();
                    if (buffer.readableBytes() != 1 || buffer.readUnsignedByte() != 1) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<FriendlyByteBuf> response = new CompletableFuture<>();
                    client.execute(() -> {
                        ClientWorldIdentityState.markExpected(connectionToken);
                        response.complete(PacketByteBufs.create());
                    });
                    return response;
                });
        ClientPlayNetworking.registerGlobalReceiver(FabricServerNetwork.CHANNEL, (client, handler, buffer, response) -> {
            ServerSnapshot snapshot = ServerSnapshot.decode(buffer.readByteArray(ServerSnapshot.MAX_BYTES));
            client.execute(() -> {
                if (client.getConnection() == handler) snapshot.apply();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricServerNetwork.WORLD_IDENTITY_CHANNEL, (client, handler, buffer, response) -> {
            WorldIdentityMessage identity = WorldIdentityMessage.decode(
                    buffer.readByteArray(WorldIdentityMessage.ENCODED_BYTES));
            var packetConnection = handler.getConnection();
            client.execute(() -> {
                var currentHandler = client.getConnection();
                var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                ClientWorldIdentityState.apply(packetConnection, currentConnection, identity.worldId());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricServerNetwork.SHARED_WAYPOINT_CHANNEL, (client, handler, buffer, response) -> {
            int kind = buffer.readUnsignedByte();
            int limit = kind == 0 ? SharedWaypointSnapshot.MAX_BYTES : kind == 1 ? SharedWaypointFeedback.MAX_BYTES : SharedWaypointRequest.MAX_BYTES;
            byte[] bytes = buffer.readByteArray(limit);
            var packetConnection = handler.getConnection();
            client.execute(() -> {
                var currentHandler = client.getConnection();
                var current = currentHandler == null ? null : currentHandler.getConnection();
                try {
                    if (kind == 0) SharedWaypointClient.apply(packetConnection, current, SharedWaypointSnapshot.decode(bytes));
                    else if (kind == 1) SharedWaypointClient.feedback(packetConnection, current, SharedWaypointFeedback.decode(bytes));
                } catch (IllegalArgumentException malformed) {
                    // Invalid payloads never alter the current client snapshot.
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricServerNetwork.LOCATION_CONTEXT_CHANNEL, (client, handler, buffer, response) -> {
            int kind = buffer.readUnsignedByte();
            if (kind != 1) return;
            byte[] bytes = buffer.readByteArray(LocationContextResponse.MAX_BYTES);
            var packetConnection = handler.getConnection();
            client.execute(() -> {
                var currentHandler = client.getConnection();
                var current = currentHandler == null ? null : currentHandler.getConnection();
                try {
                    LocationContextClient.apply(packetConnection, current, LocationContextResponse.decode(bytes));
                } catch (IllegalArgumentException malformed) {
                    // Invalid replies leave the pending location notice untouched.
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricServerNetwork.DEATH_POINT_CHANNEL, (client, handler, buffer, response) -> {
            var packetConnection = handler.getConnection();
            try {
                int kind = buffer.readUnsignedByte();
                if (kind == 0) {
                    if (buffer.isReadable()) return;
                    client.execute(() -> {
                        var currentHandler = client.getConnection();
                        var current = currentHandler == null ? null : currentHandler.getConnection();
                        net.aosankaku.openmapdelta.death.DeathPointInbox.markSupported(packetConnection, current);
                    });
                    return;
                }
                if (kind != 1) return;
                byte[] bytes = buffer.readByteArray(net.aosankaku.openmapdelta.death.DeathPointUpdate.MAX_BYTES);
                if (buffer.isReadable()) return;
                client.execute(() -> {
                    var currentHandler = client.getConnection();
                    var current = currentHandler == null ? null : currentHandler.getConnection();
                    try {
                        net.aosankaku.openmapdelta.death.DeathPointInbox.apply(packetConnection, current,
                                net.aosankaku.openmapdelta.death.DeathPointUpdate.decode(bytes));
                    } catch (IllegalArgumentException malformed) {
                        // Invalid owner reports never alter the current deathpoint state.
                    }
                });
            } catch (RuntimeException malformed) {
                // Ignore truncated or otherwise malformed deathpoint frames.
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricServerNetwork.SERVER_CONFIG_SNAPSHOT_CHANNEL, (client, handler, buffer, response) -> {
            byte[] bytes = buffer.readByteArray(ServerConfigSnapshot.MAX_BYTES);
            var packetConnection = handler.getConnection();
            client.execute(() -> {
                var currentHandler = client.getConnection();
                var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
                try {
                    ServerConfigClient.apply(packetConnection, currentConnection, ServerConfigSnapshot.decode(bytes));
                } catch (IllegalArgumentException malformed) {
                    // Malformed server configuration never changes the current client view.
                }
            });
        });
    }

    private static void sendSharedWaypointRequest(SharedWaypointRequest request) {
        var buffer = PacketByteBufs.create();
        buffer.writeByte(2);
        buffer.writeByteArray(request.encode());
        ClientPlayNetworking.send(FabricServerNetwork.SHARED_WAYPOINT_CHANNEL, buffer);
    }

    private static boolean sendLocationContextRequest(LocationContextRequest request) {
        if (Minecraft.getInstance().getConnection() == null
                || !ClientPlayNetworking.canSend(FabricServerNetwork.LOCATION_CONTEXT_CHANNEL)) return false;
        var buffer = PacketByteBufs.create();
        buffer.writeByte(0);
        buffer.writeByteArray(request.encode());
        ClientPlayNetworking.send(FabricServerNetwork.LOCATION_CONTEXT_CHANNEL, buffer);
        return true;
    }

    private static void sendServerConfigRequest(ServerConfigRequest request) {
        if (Minecraft.getInstance().getConnection() == null
                || !ClientPlayNetworking.canSend(FabricServerNetwork.SERVER_CONFIG_REQUEST_CHANNEL)) return;
        var buffer = PacketByteBufs.create();
        buffer.writeByteArray(request.encode());
        ClientPlayNetworking.send(FabricServerNetwork.SERVER_CONFIG_REQUEST_CHANNEL, buffer);
    }
}
