// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.server.ServerConfig;
import net.aosankaku.openmapdelta.area.AreaServer;
import net.aosankaku.openmapdelta.server.ServerConfigService;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.aosankaku.openmapdelta.server.LocationContextServer;
import net.aosankaku.openmapdelta.server.ServerPlayers;
import net.aosankaku.openmapdelta.server.ServerWebMapNotice;
import net.aosankaku.openmapdelta.shared.SharedWaypointFeedback;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.aosankaku.openmapdelta.shared.SharedWaypointServer;
import net.aosankaku.openmapdelta.shared.SharedWaypointSnapshot;
import net.aosankaku.openmapdelta.world.ServerWorldIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeoForgeNetwork {
    private NeoForgeNetwork() {}

    public static void init(IEventBus modEventBus) {
        net.aosankaku.openmapdelta.death.DeathPointInbox.configureSupportProbe(token -> token instanceof Connection connection
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, DeathPointPayload.TYPE.id()));
        ClientWorldIdentityState.configureSupportProbe(token -> token instanceof Connection connection
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, WorldIdentityPayload.TYPE.id()));
        modEventBus.addListener(NeoForgeNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(NeoForgeNetwork::onServerStarting);
        NeoForge.EVENT_BUS.addListener(NeoForgeNetwork::onServerStopped);
        NeoForge.EVENT_BUS.addListener(NeoForgeNetwork::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(NeoForgeNetwork::onServerTick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(MapStatePayload.TYPE, MapStatePayload.CODEC);
        registrar.playToClient(DeathPointPayload.TYPE, DeathPointPayload.CODEC);
        registrar.playToClient(ServerConfigSnapshotPayload.TYPE, ServerConfigSnapshotPayload.CODEC);
        registrar.playToServer(ServerConfigRequestPayload.TYPE, ServerConfigRequestPayload.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer actor)) return;
            context.enqueueWork(() -> handleServerConfigRequest(payload, actor));
        });
        registrar.playToClient(WorldIdentityPayload.TYPE, WorldIdentityPayload.CODEC, (payload, context) -> {
            Object packetConnectionToken = context.connection();
            context.enqueueWork(() -> NeoForgeWorldIdentityClientHandler.apply(payload, packetConnectionToken));
        });
        registrar.playBidirectional(SharedWaypointPayload.TYPE, SharedWaypointPayload.CODEC, (payload, context) -> {
            Player sender = context.player();
            if (!(sender instanceof ServerPlayer actor)) return;
            if (payload.kind() != SharedWaypointPayload.REQUEST) return;
            context.enqueueWork(() -> handleSharedWaypointRequest(payload, actor));
        });
        registrar.playBidirectional(AreaPayload.TYPE, AreaPayload.CODEC, (payload, context) -> {
            Player sender = context.player();
            if (!(sender instanceof ServerPlayer actor) || payload.kind() != AreaPayload.REQUEST) return;
            context.enqueueWork(() -> handleAreaRequest(payload, actor));
        });
        registrar.playBidirectional(LocationContextPayload.TYPE, LocationContextPayload.CODEC, (payload, context) -> {
            Player sender = context.player();
            if (!(sender instanceof ServerPlayer actor) || payload.kind() != LocationContextPayload.REQUEST) return;
            context.enqueueWork(() -> handleLocationContextRequest(payload, actor));
        });
    }

    private static void onServerStarting(ServerStartingEvent event) {
        ServerConfigService.reset(event.getServer());
        ServerConfig.load();
        ServerWorldIdentity.initialize(event.getServer());
        SharedWaypointServer.initialize(event.getServer());
        AreaServer.initialize(event.getServer());
        net.aosankaku.openmapdelta.server.DeathRecoveryServer.initialize(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        net.aosankaku.openmapdelta.server.DeathRecoveryServer.stop(event.getServer());
        ServerConfigService.reset(event.getServer());
        AreaServer.stop(event.getServer());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerWebMapNotice.announce(player);
            sendWorldIdentity(player);
            if (player.connection.hasChannel(DeathPointPayload.TYPE))
                PacketDistributor.sendToPlayer(player, new DeathPointPayload(new byte[0]));
            var server = serverOf(player);
            if (server != null) {
                SharedWaypointServer.playerJoined(server, player);
                AreaServer.playerJoined(server, player);
                ServerConfigService.playerJoined(server, player);
                net.aosankaku.openmapdelta.server.DeathRecoveryServer.playerJoined(server, player);
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) sendWorldIdentity(player);
        syncSharedWaypoints(server);
        syncSharedAreas(server);
        syncServerConfig(server);
        net.aosankaku.openmapdelta.server.DeathRecoveryServer.tick(server, (player, report) -> {
            if (player.connection.hasChannel(DeathPointPayload.TYPE)) PacketDistributor.sendToPlayer(player, DeathPointPayload.of(report));
        });
        sendServerPlayersSnapshot(server);
    }

    private static void sendServerPlayersSnapshot(MinecraftServer server) {
        var recipients = server.getPlayerList().getPlayers().stream()
                .filter(player -> player.connection.hasChannel(MapStatePayload.TYPE)).toList();
        if (recipients.isEmpty()) return;
        var payload = new MapStatePayload(ServerPlayers.snapshot(server));
        for (var player : recipients) PacketDistributor.sendToPlayer(player, payload);
    }

    private static void syncServerConfig(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (!player.connection.hasChannel(ServerConfigSnapshotPayload.TYPE)) continue;
            ServerConfigService.syncIfChanged(server, player, snapshot -> sendServerConfigSnapshot(player, snapshot));
        }
    }

    private static void sendServerConfigSnapshot(ServerPlayer player, ServerConfigSnapshot snapshot) {
        if (player.connection.hasChannel(ServerConfigSnapshotPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, ServerConfigSnapshotPayload.of(snapshot));
        }
    }

    private static void handleServerConfigRequest(ServerConfigRequestPayload payload, ServerPlayer actor) {
        var server = serverOf(actor);
        if (server == null) return;
        ServerConfigService.handle(server, actor, payload.data(), snapshot -> sendServerConfigSnapshot(actor, snapshot), () -> {
            syncServerConfig(server);
            sendServerPlayersSnapshot(server);
        });
    }

    private static void sendWorldIdentity(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null || !player.connection.hasChannel(WorldIdentityPayload.TYPE)) return;
        ServerWorldIdentity.get(server).ifPresent(id ->
                PacketDistributor.sendToPlayer(player, new WorldIdentityPayload(id)));
    }

    private static void handleSharedWaypointRequest(SharedWaypointPayload payload, ServerPlayer actor) {
        if (payload.kind() != SharedWaypointPayload.REQUEST) return;
        var server = serverOf(actor);
        if (server == null) {
            sendSharedFeedback(actor, SharedWaypointFeedback.failure("odysseymap.shared.unavailable"));
            return;
        }
        var result = SharedWaypointServer.applyEncoded(server, actor, payload.data(), isSharedAdministrator(actor),
                server.isDedicatedServer() || server.isPublished(), dimension -> hasDimension(server, dimension));
        sendSharedFeedback(actor, result.feedback());
        if (result.changed()) syncSharedWaypoints(server);
    }

    private static void handleAreaRequest(AreaPayload payload, ServerPlayer actor) {
        var server = serverOf(actor);
        if (server == null) return;
        var result = AreaServer.applyEncoded(server, actor, payload.data(), isSharedAdministrator(actor),
                dimension -> hasDimension(server, dimension));
        if (result == null) return;
        sendAreaFeedback(actor, result.feedback());
        if (result.syncRequested()) {
            if (hasAreaChannel(actor)) AreaServer.sendSnapshot(server, actor, isSharedAdministrator(actor),
                    (recipient, snapshot) -> PacketDistributor.sendToPlayer(recipient, AreaPayload.snapshot(snapshot)));
        } else if (result.changed()) syncSharedAreas(server);
    }

    private static void handleLocationContextRequest(LocationContextPayload payload, ServerPlayer actor) {
        if (payload.kind() != LocationContextPayload.REQUEST) return;
        var server = serverOf(actor);
        var response = LocationContextServer.handle(server, actor, payload.data());
        if (response != null && actor.connection.hasChannel(LocationContextPayload.TYPE)) {
            PacketDistributor.sendToPlayer(actor, LocationContextPayload.response(response));
        }
    }

    private static MinecraftServer serverOf(ServerPlayer player) { return player.level().getServer(); }

    private static boolean hasDimension(MinecraftServer server, String dimension) {
        for (var level : server.getAllLevels()) if (level.dimension().identifier().toString().equals(dimension)) return true;
        return false;
    }

    private static boolean isSharedAdministrator(ServerPlayer player) {
        var server = serverOf(player);
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || (server != null && server.isSingleplayerOwner(player.nameAndId()));
    }

    private static void syncSharedWaypoints(MinecraftServer server) {
        SharedWaypointServer.trimDisconnected(server);
        boolean multiplayer = server.isDedicatedServer() || server.isPublished();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!player.connection.hasChannel(SharedWaypointPayload.TYPE)) continue;
            SharedWaypointServer.syncIfChanged(server, player, multiplayer, isSharedAdministrator(player),
                    (recipient, snapshot) -> PacketDistributor.sendToPlayer(recipient, SharedWaypointPayload.snapshot(snapshot)));
        }
    }

    private static void sendSharedFeedback(ServerPlayer player, SharedWaypointFeedback feedback) {
        if (player.connection.hasChannel(SharedWaypointPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, SharedWaypointPayload.feedback(feedback));
        }
    }

    private static void syncSharedAreas(MinecraftServer server) {
        AreaServer.trimDisconnected(server);
        for (var player : server.getPlayerList().getPlayers()) {
            if (!hasAreaChannel(player)) continue;
            AreaServer.syncIfChanged(server, player, isSharedAdministrator(player),
                    (recipient, snapshot) -> PacketDistributor.sendToPlayer(recipient, AreaPayload.snapshot(snapshot)));
        }
    }

    private static void sendAreaFeedback(ServerPlayer player, net.aosankaku.openmapdelta.area.AreaWire.Feedback feedback) {
        if (hasAreaChannel(player)) PacketDistributor.sendToPlayer(player, AreaPayload.feedback(feedback));
    }

    private static boolean hasAreaChannel(ServerPlayer player) {
        return player.connection.hasChannel(AreaPayload.TYPE);
    }
}
