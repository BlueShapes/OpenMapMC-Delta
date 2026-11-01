// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.aosankaku.openmapdelta.network.LocationContextRequest;
import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Client-only sender registration. */
public final class NeoForgeSharedWaypointClientNetwork {
    private NeoForgeSharedWaypointClientNetwork() {}
    public static void init() {
        SharedWaypointClient.configureSender(request ->
                PacketDistributor.sendToServer(SharedWaypointPayload.request(request)));
        LocationContextClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (connection == null || !NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, LocationContextPayload.TYPE.id())) return false;
            PacketDistributor.sendToServer(LocationContextPayload.request(request));
            return true;
        });
        ServerConfigClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (connection == null || !NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY,
                    ServerConfigRequestPayload.TYPE.id())) return;
            PacketDistributor.sendToServer(ServerConfigRequestPayload.of(request));
        });
    }
}
