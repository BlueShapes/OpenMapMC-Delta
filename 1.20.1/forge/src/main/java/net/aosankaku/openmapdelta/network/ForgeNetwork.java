// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.server.ServerConfig;
import net.aosankaku.openmapdelta.server.ServerConfigRequest;
import net.aosankaku.openmapdelta.server.ServerConfigService;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.aosankaku.openmapdelta.server.ServerPlayers;
import net.aosankaku.openmapdelta.server.ServerWebMapNotice;
import net.aosankaku.openmapdelta.shared.SharedWaypointFeedback;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.aosankaku.openmapdelta.shared.SharedWaypointServer;
import net.aosankaku.openmapdelta.shared.SharedWaypointSnapshot;
import net.aosankaku.openmapdelta.server.LocationContextServer;
import net.aosankaku.openmapdelta.world.ServerWorldIdentity;
import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.aosankaku.openmapdelta.death.DeathPointUpdate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ForgeNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            // Forge's indexed packet envelope differs from Fabric's raw payload envelope.
            new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "forge_map_state"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static final SimpleChannel WORLD_IDENTITY_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "forge_world_identity"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static final SimpleChannel SHARED_WAYPOINT_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "forge_shared_waypoints"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static final SimpleChannel LOCATION_CONTEXT_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "forge_location_context"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static final SimpleChannel DEATH_POINT_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "forge_death_points"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static final SimpleChannel SERVER_CONFIG_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OpenMapDeltaCommon.LEGACY_PROTOCOL_NAMESPACE, "forge_server_config"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private record Message(ServerSnapshot snapshot) {}
    private record SharedSnapshotMessage(byte[] bytes) {}
    private record SharedFeedbackMessage(byte[] bytes) {}
    private record SharedRequestMessage(byte[] bytes) {}
    private record LocationContextRequestMessage(byte[] bytes) {}
    private record LocationContextResponseMessage(byte[] bytes) {}
    private record DeathPointMessage(byte[] bytes) {}
    private record ServerConfigRequestMessage(byte[] bytes) {}
    private record ServerConfigSnapshotMessage(byte[] bytes) {}

    private ForgeNetwork() {}

    public static void init() {
        ClientWorldIdentityState.configureSupportProbe(token -> token instanceof Connection connection
                && WORLD_IDENTITY_CHANNEL.isRemotePresent(connection));
        net.aosankaku.openmapdelta.death.DeathPointInbox.configureSupportProbe(token -> token instanceof Connection connection
                && DEATH_POINT_CHANNEL.isRemotePresent(connection));
        CHANNEL.messageBuilder(Message.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.snapshot().encode()))
                .decoder(buffer -> new Message(ServerSnapshot.decode(buffer.readByteArray(ServerSnapshot.MAX_BYTES))))
                .consumerMainThread((message, context) -> message.snapshot().apply()).add();
        WORLD_IDENTITY_CHANNEL.messageBuilder(WorldIdentityMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.encode()))
                .decoder(buffer -> WorldIdentityMessage.decode(buffer.readByteArray(WorldIdentityMessage.ENCODED_BYTES)))
                .consumerMainThread((message, context) ->
                        ForgeWorldIdentityClientHandler.apply(message, context.get().getNetworkManager())).add();
        SHARED_WAYPOINT_CHANNEL.messageBuilder(SharedSnapshotMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new SharedSnapshotMessage(buffer.readByteArray(SharedWaypointSnapshot.MAX_BYTES)))
                .consumerMainThread((message, context) -> ForgeSharedWaypointClientHandler.applySnapshot(
                        SharedWaypointSnapshot.decode(message.bytes()), context.get().getNetworkManager())).add();
        SHARED_WAYPOINT_CHANNEL.messageBuilder(SharedFeedbackMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new SharedFeedbackMessage(buffer.readByteArray(SharedWaypointFeedback.MAX_BYTES)))
                .consumerMainThread((message, context) -> ForgeSharedWaypointClientHandler.applyFeedback(
                        SharedWaypointFeedback.decode(message.bytes()), context.get().getNetworkManager())).add();
        SHARED_WAYPOINT_CHANNEL.messageBuilder(SharedRequestMessage.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new SharedRequestMessage(buffer.readByteArray(SharedWaypointRequest.MAX_BYTES)))
                .consumerMainThread(ForgeNetwork::onSharedWaypointRequest).add();
        LOCATION_CONTEXT_CHANNEL.messageBuilder(LocationContextRequestMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new LocationContextRequestMessage(buffer.readByteArray(
                        net.aosankaku.openmapdelta.network.LocationContextRequest.MAX_BYTES)))
                .consumerMainThread(ForgeNetwork::onLocationContextRequest).add();
        LOCATION_CONTEXT_CHANNEL.messageBuilder(LocationContextResponseMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new LocationContextResponseMessage(buffer.readByteArray(
                        net.aosankaku.openmapdelta.network.LocationContextResponse.MAX_BYTES)))
                .consumerMainThread((message, context) -> {
                    try {
                        ForgeLocationContextClientHandler.apply(
                                net.aosankaku.openmapdelta.network.LocationContextResponse.decode(message.bytes()),
                                context.get().getNetworkManager());
                    } catch (IllegalArgumentException malformed) {
                        // Invalid replies leave the pending notification unchanged.
                    }
                }).add();
        DEATH_POINT_CHANNEL.messageBuilder(DeathPointMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new DeathPointMessage(buffer.readByteArray(DeathPointUpdate.MAX_BYTES)))
                .consumerMainThread((message, context) -> ForgeDeathPointClientHandler.apply(
                        message.bytes(), context.get().getNetworkManager())).add();
        SERVER_CONFIG_CHANNEL.messageBuilder(ServerConfigRequestMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new ServerConfigRequestMessage(buffer.readByteArray(ServerConfigRequest.MAX_BYTES)))
                .consumerMainThread(ForgeNetwork::onServerConfigRequest).add();
        SERVER_CONFIG_CHANNEL.messageBuilder(ServerConfigSnapshotMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeByteArray(message.bytes()))
                .decoder(buffer -> new ServerConfigSnapshotMessage(buffer.readByteArray(ServerConfigSnapshot.MAX_BYTES)))
                .consumerMainThread((message, context) -> {
                    var packetConnection = context.get().getNetworkManager();
                    ServerConfigSnapshot snapshot;
                    try {
                        snapshot = ServerConfigSnapshot.decode(message.bytes());
                    } catch (IllegalArgumentException malformed) {
                        return;
                    }
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ForgeServerConfigClientHandler.apply(packetConnection, snapshot));
                }).add();
        MinecraftForge.EVENT_BUS.addListener(ForgeNetwork::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(ForgeNetwork::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(ForgeNetwork::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ForgeNetwork::onServerTick);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        ServerConfigService.reset(event.getServer());
        ServerConfig.load();
        ServerWorldIdentity.initialize(event.getServer());
        SharedWaypointServer.initialize(event.getServer());
        DeathRecoveryServer.initialize(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        DeathRecoveryServer.stop(event.getServer());
        ServerConfigService.reset(event.getServer());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerWebMapNotice.announce(player);
            sendWorldIdentity(player);
            var server = player.getServer();
            if (server != null) {
                SharedWaypointServer.playerJoined(server, player);
                ServerConfigService.playerJoined(server, player);
                DeathRecoveryServer.playerJoined(server, player);
            }
            if (DEATH_POINT_CHANNEL.isRemotePresent(player.connection.connection)) {
                DEATH_POINT_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DeathPointMessage(new byte[0]));
            }
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) sendWorldIdentity(player);
        syncSharedWaypoints(server);
        DeathRecoveryServer.tick(server, (player, update) -> {
            if (!DEATH_POINT_CHANNEL.isRemotePresent(player.connection.connection)) return;
            DEATH_POINT_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new DeathPointMessage(update.encode()));
        });
        syncServerConfig(server);
        sendServerPlayersSnapshot(server);
    }

    private static void syncServerConfig(net.minecraft.server.MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (!SERVER_CONFIG_CHANNEL.isRemotePresent(player.connection.connection)) continue;
            ServerConfigService.syncIfChanged(server, player, snapshot -> sendServerConfigSnapshot(player, snapshot));
        }
    }

    private static void sendServerConfigSnapshot(ServerPlayer player, ServerConfigSnapshot snapshot) {
        if (!SERVER_CONFIG_CHANNEL.isRemotePresent(player.connection.connection)) return;
        SERVER_CONFIG_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ServerConfigSnapshotMessage(snapshot.encode()));
    }

    private static void sendServerPlayersSnapshot(net.minecraft.server.MinecraftServer server) {
        var recipients = server.getPlayerList().getPlayers().stream()
                .filter(player -> CHANNEL.isRemotePresent(player.connection.connection)).toList();
        if (recipients.isEmpty()) return;
        var message = new Message(ServerPlayers.snapshot(server));
        for (var player : recipients) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    private static void onServerConfigRequest(ServerConfigRequestMessage message,
                                              java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        ServerPlayer actor = context.getSender();
        if (actor == null) return;
        var server = actor.getServer();
        if (server == null) return;
        ServerConfigService.handle(server, actor, message.bytes(),
                snapshot -> sendServerConfigSnapshot(actor, snapshot),
                () -> {
                    syncServerConfig(server);
                    sendServerPlayersSnapshot(server);
                });
    }

    public static void sendServerConfigRequest(ServerConfigRequest request) {
        SERVER_CONFIG_CHANNEL.sendToServer(new ServerConfigRequestMessage(request.encode()));
    }

    public static boolean canSendServerConfig(Connection connection) {
        return connection != null && SERVER_CONFIG_CHANNEL.isRemotePresent(connection);
    }

    private static void sendWorldIdentity(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || !WORLD_IDENTITY_CHANNEL.isRemotePresent(player.connection.connection)) return;
        ServerWorldIdentity.get(server).ifPresent(id -> WORLD_IDENTITY_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), new WorldIdentityMessage(id)));
    }

    private static void onSharedWaypointRequest(SharedRequestMessage message,
                                                java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        ServerPlayer actor = context.getSender();
        if (actor == null) return;
        var server = actor.getServer();
        if (server == null) {
            sendSharedFeedback(actor, SharedWaypointFeedback.failure("odysseymap.shared.unavailable"));
            return;
        }
        var result = SharedWaypointServer.applyEncoded(server, actor, message.bytes(), isSharedAdministrator(actor),
                server.isDedicatedServer() || server.isPublished(), dimension -> hasDimension(server, dimension));
        sendSharedFeedback(actor, result.feedback());
        if (result.changed()) syncSharedWaypoints(server);
    }

    private static boolean isSharedAdministrator(ServerPlayer player) {
        var server = player.getServer();
        return player.createCommandSourceStack().hasPermission(2)
                || (server != null && server.isSingleplayerOwner(player.getGameProfile()));
    }

    private static boolean hasDimension(net.minecraft.server.MinecraftServer server, String dimension) {
        for (var level : server.getAllLevels()) if (level.dimension().location().toString().equals(dimension)) return true;
        return false;
    }

    private static void syncSharedWaypoints(net.minecraft.server.MinecraftServer server) {
        SharedWaypointServer.trimDisconnected(server);
        boolean multiplayer = server.isDedicatedServer() || server.isPublished();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!SHARED_WAYPOINT_CHANNEL.isRemotePresent(player.connection.connection)) continue;
            SharedWaypointServer.syncIfChanged(server, player, multiplayer, isSharedAdministrator(player),
                    ForgeNetwork::sendSharedSnapshot);
        }
    }

    private static void sendSharedSnapshot(ServerPlayer player, SharedWaypointSnapshot snapshot) {
        SHARED_WAYPOINT_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SharedSnapshotMessage(snapshot.encode()));
    }

    private static void sendSharedFeedback(ServerPlayer player, SharedWaypointFeedback feedback) {
        if (SHARED_WAYPOINT_CHANNEL.isRemotePresent(player.connection.connection)) {
            SHARED_WAYPOINT_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new SharedFeedbackMessage(feedback.encode()));
        }
    }

    public static void sendSharedRequest(SharedWaypointRequest request) {
        SHARED_WAYPOINT_CHANNEL.sendToServer(new SharedRequestMessage(request.encode()));
    }

    private static void onLocationContextRequest(LocationContextRequestMessage message,
                                                java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        ServerPlayer actor = context.getSender();
        if (actor == null) return;
        var server = actor.getServer();
        var response = LocationContextServer.handle(server, actor, message.bytes());
        if (response != null && server != null && LOCATION_CONTEXT_CHANNEL.isRemotePresent(actor.connection.connection)) {
            LOCATION_CONTEXT_CHANNEL.send(PacketDistributor.PLAYER.with(() -> actor),
                    new LocationContextResponseMessage(response.encode()));
        }
    }

    public static boolean canSendLocationContext(Connection connection) {
        return connection != null && LOCATION_CONTEXT_CHANNEL.isRemotePresent(connection);
    }

    public static void sendLocationContextRequest(net.aosankaku.openmapdelta.network.LocationContextRequest request) {
        LOCATION_CONTEXT_CHANNEL.sendToServer(new LocationContextRequestMessage(request.encode()));
    }
}
