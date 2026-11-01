// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.client.LocationContextClient;
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
        LocationContextClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (connection == null || !NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, LocationContextPayload.TYPE.id())) return false;
            ClientPacketDistributor.sendToServer(LocationContextPayload.request(request));
            return true;
        });
        modEventBus.addListener(NeoForgeSharedWaypointClientNetwork::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(SharedWaypointPayload.TYPE, (payload, context) -> {
            if (payload.kind() != SharedWaypointPayload.SNAPSHOT
                    && payload.kind() != SharedWaypointPayload.FEEDBACK) return;
            NeoForgeSharedWaypointClientHandler.apply(payload, context.connection());
        });
        event.register(LocationContextPayload.TYPE, (payload, context) -> {
            if (payload.kind() == LocationContextPayload.RESPONSE) {
                NeoForgeLocationContextClientHandler.apply(payload, context.connection());
            }
        });
    }
}
