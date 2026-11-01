// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.area.AreaServer;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.resources.Identifier;

public final class FabricServerNetwork {
    public static final Identifier WORLD_IDENTITY_SUPPORT_CHANNEL = Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "world_identity_support_v1");

    private FabricServerNetwork() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            net.aosankaku.openmapdelta.server.DeathRecoveryServer.stop(server);
            ServerConfigService.reset(server);
            AreaServer.stop(server);
        });
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            var request = new FriendlyByteBuf(Unpooled.buffer());
            request.writeByte(1);
            sender.sendPacket(WORLD_IDENTITY_SUPPORT_CHANNEL, request);
        });
        ServerLoginNetworking.registerGlobalReceiver(WORLD_IDENTITY_SUPPORT_CHANNEL,
                (server, handler, understood, buffer, synchronizer, responseSender) -> {
                    // Unsupported clients are allowed to join; only supporting clients mark the identity as required.
                });
        PayloadTypeRegistry.clientboundPlay().register(DeathPointPayload.TYPE, DeathPointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapStatePayload.TYPE, MapStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldIdentityPayload.TYPE, WorldIdentityPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SharedWaypointPayload.TYPE, SharedWaypointPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SharedWaypointPayload.TYPE, SharedWaypointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AreaPayload.TYPE, AreaPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AreaPayload.TYPE, AreaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LocationContextPayload.TYPE, LocationContextPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LocationContextPayload.TYPE, LocationContextPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerConfigSnapshotPayload.TYPE, ServerConfigSnapshotPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerConfigRequestPayload.TYPE, ServerConfigRequestPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerConfigService.reset(server);
            ServerConfig.load();
            ServerWorldIdentity.initialize(server);
            SharedWaypointServer.initialize(server);
            AreaServer.initialize(server);
            net.aosankaku.openmapdelta.server.DeathRecoveryServer.initialize(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerWebMapNotice.announce(handler.player);
            sendWorldIdentity(handler.player);
            if (ServerPlayNetworking.canSend(handler.player, DeathPointPayload.TYPE))
                ServerPlayNetworking.send(handler.player, new DeathPointPayload(new byte[0]));
            SharedWaypointServer.playerJoined(server, handler.player);
            AreaServer.playerJoined(server, handler.player);
            ServerConfigService.playerJoined(server, handler.player);
            net.aosankaku.openmapdelta.server.DeathRecoveryServer.playerJoined(server, handler.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(SharedWaypointPayload.TYPE, (payload, context) -> {
            if (payload.kind() != SharedWaypointPayload.REQUEST) return;
            var server = context.server();
            var actor = context.player();
            server.execute(() -> handleSharedWaypointRequest(server, actor, payload.data()));
        });
        ServerPlayNetworking.registerGlobalReceiver(AreaPayload.TYPE, (payload, context) -> {
            if (payload.kind() != AreaPayload.REQUEST) return;
            var server = context.server();
            var actor = context.player();
            server.execute(() -> handleAreaRequest(server, actor, payload.data()));
        });
        ServerPlayNetworking.registerGlobalReceiver(LocationContextPayload.TYPE, (payload, context) -> {
            if (payload.kind() != LocationContextPayload.REQUEST) return;
            var server = context.server();
            var actor = context.player();
            server.execute(() -> {
                var result = LocationContextServer.handle(server, actor, payload.data());
                if (result != null && ServerPlayNetworking.canSend(actor, LocationContextPayload.TYPE)) {
                    ServerPlayNetworking.send(actor, LocationContextPayload.response(result));
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ServerConfigRequestPayload.TYPE, (payload, context) -> {
            var server = context.server();
            var actor = context.player();
            server.execute(() -> ServerConfigService.handle(server, actor, payload.data(),
                    snapshot -> sendServerConfigSnapshot(actor, snapshot),
                    () -> {
                        syncServerConfig(server);
                        sendServerPlayersSnapshot(server);
                    }));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) sendWorldIdentity(player);
            syncSharedWaypoints(server);
            syncSharedAreas(server);
            syncServerConfig(server);
            net.aosankaku.openmapdelta.server.DeathRecoveryServer.tick(server, (player, report) -> {
                if (ServerPlayNetworking.canSend(player, DeathPointPayload.TYPE)) ServerPlayNetworking.send(player, DeathPointPayload.of(report));
            });
            // Every send is capability-gated: vanilla clients never receive our payload.
            sendServerPlayersSnapshot(server);
        });
    }

    private static void syncServerConfig(net.minecraft.server.MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, ServerConfigSnapshotPayload.TYPE)) continue;
            ServerConfigService.syncIfChanged(server, player,
                    snapshot -> sendServerConfigSnapshot(player, snapshot));
        }
    }

    private static void sendServerConfigSnapshot(net.minecraft.server.level.ServerPlayer player,
                                                 net.aosankaku.openmapdelta.server.ServerConfigSnapshot snapshot) {
        if (ServerPlayNetworking.canSend(player, ServerConfigSnapshotPayload.TYPE)) {
            ServerPlayNetworking.send(player, ServerConfigSnapshotPayload.of(snapshot));
        }
    }

    private static void sendServerPlayersSnapshot(net.minecraft.server.MinecraftServer server) {
        var recipients = server.getPlayerList().getPlayers().stream()
                .filter(player -> ServerPlayNetworking.canSend(player, MapStatePayload.TYPE)).toList();
        if (recipients.isEmpty()) return;
        var snapshot = ServerPlayers.snapshot(server);
        for (var player : recipients) ServerPlayNetworking.send(player, new MapStatePayload(snapshot));
    }

    private static void sendWorldIdentity(net.minecraft.server.level.ServerPlayer player) {
        if (player.level().getServer() == null || !ServerPlayNetworking.canSend(player, WorldIdentityPayload.TYPE)) return;
        ServerWorldIdentity.get(player.level().getServer()).ifPresent(id ->
                ServerPlayNetworking.send(player, new WorldIdentityPayload(id)));
    }

    private static void handleSharedWaypointRequest(net.minecraft.server.MinecraftServer server,
                                                    net.minecraft.server.level.ServerPlayer actor, byte[] bytes) {
        var result = SharedWaypointServer.applyEncoded(server, actor, bytes, isSharedAdministrator(actor),
                server.isDedicatedServer() || server.isPublished(), dimension -> hasDimension(server, dimension));
        sendSharedFeedback(actor, result.feedback());
        if (result.changed()) syncSharedWaypoints(server);
    }

    private static void handleAreaRequest(net.minecraft.server.MinecraftServer server,
                                          net.minecraft.server.level.ServerPlayer actor, byte[] bytes) {
        var result = AreaServer.applyEncoded(server, actor, bytes, isSharedAdministrator(actor),
                dimension -> hasDimension(server, dimension));
        if (result == null) return;
        sendAreaFeedback(actor, result.feedback());
        if (result.syncRequested()) {
            if (ServerPlayNetworking.canSend(actor, AreaPayload.TYPE)) {
                AreaServer.sendSnapshot(server, actor, isSharedAdministrator(actor),
                        (recipient, snapshot) -> ServerPlayNetworking.send(recipient, AreaPayload.snapshot(snapshot)));
            }
        } else if (result.changed()) syncSharedAreas(server);
    }

    private static boolean hasDimension(net.minecraft.server.MinecraftServer server, String dimension) {
        for (var level : server.getAllLevels()) if (level.dimension().identifier().toString().equals(dimension)) return true;
        return false;
    }

    private static boolean isSharedAdministrator(net.minecraft.server.level.ServerPlayer player) {
        var server = player.level().getServer();
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || (server != null && server.isSingleplayerOwner(player.nameAndId()));
    }

    private static void syncSharedWaypoints(net.minecraft.server.MinecraftServer server) {
        SharedWaypointServer.trimDisconnected(server);
        boolean multiplayer = server.isDedicatedServer() || server.isPublished();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, SharedWaypointPayload.TYPE)) continue;
            SharedWaypointServer.syncIfChanged(server, player, multiplayer, isSharedAdministrator(player),
                    (recipient, snapshot) -> ServerPlayNetworking.send(recipient, SharedWaypointPayload.snapshot(snapshot)));
        }
    }

    private static void sendSharedFeedback(net.minecraft.server.level.ServerPlayer player, SharedWaypointFeedback feedback) {
        if (ServerPlayNetworking.canSend(player, SharedWaypointPayload.TYPE)) {
            ServerPlayNetworking.send(player, SharedWaypointPayload.feedback(feedback));
        }
    }

    private static void syncSharedAreas(net.minecraft.server.MinecraftServer server) {
        AreaServer.trimDisconnected(server);
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, AreaPayload.TYPE)) continue;
            AreaServer.syncIfChanged(server, player, isSharedAdministrator(player),
                    (recipient, snapshot) -> ServerPlayNetworking.send(recipient, AreaPayload.snapshot(snapshot)));
        }
    }

    private static void sendAreaFeedback(net.minecraft.server.level.ServerPlayer player,
                                         net.aosankaku.openmapdelta.area.AreaWire.Feedback feedback) {
        if (ServerPlayNetworking.canSend(player, AreaPayload.TYPE)) {
            ServerPlayNetworking.send(player, AreaPayload.feedback(feedback));
        }
    }
}
