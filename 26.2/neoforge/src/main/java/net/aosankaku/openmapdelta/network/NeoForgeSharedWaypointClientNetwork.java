// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.area.AreaClient;
import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only sender registration. */
public final class NeoForgeSharedWaypointClientNetwork {
    private NeoForgeSharedWaypointClientNetwork() {}
    public static void init(IEventBus modEventBus) {
        SharedWaypointClient.configureSender(request ->
                ClientPacketDistributor.sendToServer(SharedWaypointPayload.request(request)));
        AreaClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (connection == null || !NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, AreaPayload.TYPE.id())) {
                throw new IllegalStateException("Shared area channel is unavailable");
            }
            ClientPacketDistributor.sendToServer(AreaPayload.request(request));
        });
        LocationContextClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (connection == null || !NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, LocationContextPayload.TYPE.id())) return false;
            ClientPacketDistributor.sendToServer(LocationContextPayload.request(request));
            return true;
        });
        ServerConfigClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (connection == null || !NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, ServerConfigRequestPayload.TYPE.id())) return;
            ClientPacketDistributor.sendToServer(ServerConfigRequestPayload.of(request));
        });
        modEventBus.addListener(NeoForgeSharedWaypointClientNetwork::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(MapStatePayload.TYPE, (payload, context) -> {
            var listener = Minecraft.getInstance().getConnection();
            payload.snapshot().applyFromConnection(context.connection(), listener == null ? null : listener.getConnection());
        });
        event.register(DeathPointPayload.TYPE, (payload, context) ->
                NeoForgeDeathPointClientHandler.apply(payload, context.connection()));
        event.register(ServerConfigSnapshotPayload.TYPE, (payload, context) ->
                NeoForgeServerConfigClientHandler.apply(payload, context.connection()));
        event.register(SharedWaypointPayload.TYPE, (payload, context) -> {
            if (payload.kind() != SharedWaypointPayload.SNAPSHOT
                    && payload.kind() != SharedWaypointPayload.FEEDBACK) return;
            NeoForgeSharedWaypointClientHandler.apply(payload, context.connection());
        });
        event.register(AreaPayload.TYPE, (payload, context) -> {
            if (payload.kind() != AreaPayload.SNAPSHOT && payload.kind() != AreaPayload.FEEDBACK) return;
            var listener = Minecraft.getInstance().getConnection();
            Object current = listener == null ? null : listener.getConnection();
            try {
                if (payload.kind() == AreaPayload.SNAPSHOT) {
                    AreaClient.apply(context.connection(), current, payload.snapshot());
                } else {
                    AreaClient.feedback(context.connection(), current, payload.feedback());
                }
            } catch (IllegalArgumentException malformed) {
                // Malformed or incomplete packets never replace accepted area state.
            }
        });
        event.register(LocationContextPayload.TYPE, (payload, context) -> {
            if (payload.kind() == LocationContextPayload.RESPONSE) {
                NeoForgeLocationContextClientHandler.apply(payload, context.connection());
            }
        });
    }
}
