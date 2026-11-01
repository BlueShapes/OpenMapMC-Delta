// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.server.ServerConfig;
import net.aosankaku.openmapdelta.server.ServerConfigService;
import net.aosankaku.openmapdelta.server.ServerPlayers;
import net.aosankaku.openmapdelta.server.ServerWebMapNotice;
import net.aosankaku.openmapdelta.shared.SharedWaypointFeedback;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.aosankaku.openmapdelta.shared.SharedWaypointServer;
import net.aosankaku.openmapdelta.shared.SharedWaypointSnapshot;
import net.aosankaku.openmapdelta.server.LocationContextServer;
import net.aosankaku.openmapdelta.world.ServerWorldIdentity;
import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;

public final class FabricServerNetwork {
    public static final ResourceLocation CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "map_state");
    public static final ResourceLocation WORLD_IDENTITY_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "world_identity");
    public static final ResourceLocation WORLD_IDENTITY_SUPPORT_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "world_identity_support_v1");
    public static final ResourceLocation SHARED_WAYPOINT_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "shared_waypoints");
    public static final ResourceLocation LOCATION_CONTEXT_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "location_context");
    public static final ResourceLocation DEATH_POINT_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "death_points");
    public static final ResourceLocation SERVER_CONFIG_REQUEST_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "server_config_request");
    public static final ResourceLocation SERVER_CONFIG_SNAPSHOT_CHANNEL = new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "server_config_snapshot");
    private FabricServerNetwork() {}

    public static void init() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            var request = PacketByteBufs.create();
            request.writeByte(1);
            sender.sendPacket(WORLD_IDENTITY_SUPPORT_CHANNEL, request);
        });
        ServerLoginNetworking.registerGlobalReceiver(WORLD_IDENTITY_SUPPORT_CHANNEL,
                (server, handler, understood, buffer, synchronizer, responseSender) -> {
                    // Unsupported clients are allowed to join; only supporting clients mark the identity as required.
                });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerConfigService.reset(server);
            ServerConfig.load();
            ServerWorldIdentity.initialize(server);
            SharedWaypointServer.initialize(server);
            DeathRecoveryServer.initialize(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DeathRecoveryServer.stop(server);
            ServerConfigService.reset(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerWebMapNotice.announce(handler.player);
            sendWorldIdentity(handler.player);
            SharedWaypointServer.playerJoined(server, handler.player);
            ServerConfigService.playerJoined(server, handler.player);
            DeathRecoveryServer.playerJoined(server, handler.player);
            if (ServerPlayNetworking.canSend(handler.player, DEATH_POINT_CHANNEL)) {
                var support = PacketByteBufs.create();
                support.writeByte(0);
                ServerPlayNetworking.send(handler.player, DEATH_POINT_CHANNEL, support);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(SHARED_WAYPOINT_CHANNEL, (server, player, handler, buffer, responseSender) -> {
            int kind = buffer.readUnsignedByte();
            if (kind != 2) return;
            byte[] bytes = buffer.readByteArray(SharedWaypointRequest.MAX_BYTES);
            server.execute(() -> handleSharedWaypointRequest(server, player, bytes));
        });
        ServerPlayNetworking.registerGlobalReceiver(LOCATION_CONTEXT_CHANNEL, (server, player, handler, buffer, responseSender) -> {
            int kind = buffer.readUnsignedByte();
            if (kind != 0) return;
            byte[] bytes = buffer.readByteArray(net.aosankaku.openmapdelta.network.LocationContextRequest.MAX_BYTES);
            server.execute(() -> {
                var result = LocationContextServer.handle(server, player, bytes);
                if (result == null || !ServerPlayNetworking.canSend(player, LOCATION_CONTEXT_CHANNEL)) return;
                var reply = PacketByteBufs.create();
                reply.writeByte(1);
                reply.writeByteArray(result.encode());
                ServerPlayNetworking.send(player, LOCATION_CONTEXT_CHANNEL, reply);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SERVER_CONFIG_REQUEST_CHANNEL, (server, player, handler, buffer, responseSender) -> {
            byte[] bytes = buffer.readByteArray(net.aosankaku.openmapdelta.server.ServerConfigRequest.MAX_BYTES);
            server.execute(() -> ServerConfigService.handle(server, player, bytes,
                    snapshot -> sendServerConfigSnapshot(player, snapshot),
                    () -> {
                        syncServerConfig(server);
                        sendServerPlayersSnapshot(server);
                    }));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) sendWorldIdentity(player);
            syncSharedWaypoints(server);
            syncServerConfig(server);
            DeathRecoveryServer.tick(server, (player, update) -> {
                if (!ServerPlayNetworking.canSend(player, DEATH_POINT_CHANNEL)) return;
                var report = PacketByteBufs.create();
                report.writeByte(1);
                report.writeByteArray(update.encode());
                ServerPlayNetworking.send(player, DEATH_POINT_CHANNEL, report);
            });
            // Every send is capability-gated: vanilla clients never receive our payload.
            sendServerPlayersSnapshot(server);
        });
    }

    private static void syncServerConfig(net.minecraft.server.MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, SERVER_CONFIG_SNAPSHOT_CHANNEL)) continue;
            ServerConfigService.syncIfChanged(server, player, snapshot -> sendServerConfigSnapshot(player, snapshot));
        }
    }

    private static void sendServerConfigSnapshot(net.minecraft.server.level.ServerPlayer player,
                                                 net.aosankaku.openmapdelta.server.ServerConfigSnapshot snapshot) {
        if (!ServerPlayNetworking.canSend(player, SERVER_CONFIG_SNAPSHOT_CHANNEL)) return;
        var buffer = PacketByteBufs.create();
        buffer.writeByteArray(snapshot.encode());
        ServerPlayNetworking.send(player, SERVER_CONFIG_SNAPSHOT_CHANNEL, buffer);
    }

    private static void sendServerPlayersSnapshot(net.minecraft.server.MinecraftServer server) {
        var recipients = server.getPlayerList().getPlayers().stream()
                .filter(player -> ServerPlayNetworking.canSend(player, CHANNEL)).toList();
        if (recipients.isEmpty()) return;
        var snapshot = ServerPlayers.snapshot(server);
        for (var player : recipients) {
            var buffer = PacketByteBufs.create();
            buffer.writeByteArray(snapshot.encode());
            ServerPlayNetworking.send(player, CHANNEL, buffer);
        }
    }

    private static void sendWorldIdentity(net.minecraft.server.level.ServerPlayer player) {
        if (player.getServer() == null || !ServerPlayNetworking.canSend(player, WORLD_IDENTITY_CHANNEL)) return;
        ServerWorldIdentity.get(player.getServer()).ifPresent(id -> {
            var buffer = PacketByteBufs.create();
            buffer.writeByteArray(new WorldIdentityMessage(id).encode());
            ServerPlayNetworking.send(player, WORLD_IDENTITY_CHANNEL, buffer);
        });
    }

    private static void handleSharedWaypointRequest(net.minecraft.server.MinecraftServer server,
                                                    net.minecraft.server.level.ServerPlayer actor, byte[] bytes) {
        var result = SharedWaypointServer.applyEncoded(server, actor, bytes, isSharedAdministrator(actor),
                server.isDedicatedServer() || server.isPublished(), dimension -> hasDimension(server, dimension));
        sendSharedFeedback(actor, result.feedback());
        if (result.changed()) syncSharedWaypoints(server);
    }

    private static boolean hasDimension(net.minecraft.server.MinecraftServer server, String dimension) {
        for (var level : server.getAllLevels()) if (level.dimension().location().toString().equals(dimension)) return true;
        return false;
    }

    private static boolean isSharedAdministrator(net.minecraft.server.level.ServerPlayer player) {
        var server = player.getServer();
        return player.createCommandSourceStack().hasPermission(2)
                || (server != null && server.isSingleplayerOwner(player.getGameProfile()));
    }

    private static void syncSharedWaypoints(net.minecraft.server.MinecraftServer server) {
        SharedWaypointServer.trimDisconnected(server);
        boolean multiplayer = server.isDedicatedServer() || server.isPublished();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, SHARED_WAYPOINT_CHANNEL)) continue;
            SharedWaypointServer.syncIfChanged(server, player, multiplayer, isSharedAdministrator(player),
                    FabricServerNetwork::sendSharedSnapshot);
        }
    }

    private static void sendSharedSnapshot(net.minecraft.server.level.ServerPlayer player, SharedWaypointSnapshot snapshot) {
        var buffer = PacketByteBufs.create();
        buffer.writeByte(0);
        buffer.writeByteArray(snapshot.encode());
        ServerPlayNetworking.send(player, SHARED_WAYPOINT_CHANNEL, buffer);
    }

    private static void sendSharedFeedback(net.minecraft.server.level.ServerPlayer player, SharedWaypointFeedback feedback) {
        if (!ServerPlayNetworking.canSend(player, SHARED_WAYPOINT_CHANNEL)) return;
        var buffer = PacketByteBufs.create();
        buffer.writeByte(1);
        buffer.writeByteArray(feedback.encode());
        ServerPlayNetworking.send(player, SHARED_WAYPOINT_CHANNEL, buffer);
    }
}
