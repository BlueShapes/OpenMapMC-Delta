// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta;

import net.aosankaku.openmapdelta.client.ClientEvents;
import net.aosankaku.openmapdelta.client.ClientBootstrap;
import net.aosankaku.openmapdelta.network.FabricClientNetwork;
import net.aosankaku.openmapdelta.client.OpenMapDeltaKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;

public class OpenMapDeltaFabric implements ClientModInitializer {

    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping OPEN_FULLSCREEN;
    private static KeyMapping ZOOM_IN;
    private static KeyMapping ZOOM_OUT;
    private static KeyMapping CREATE_WAYPOINT;
    private static KeyMapping OPEN_SETTINGS;

    private final ClientEvents events = new ClientEvents();
    private boolean wasDead;

    @Override
    public void onInitializeClient() {
        ClientBootstrap.init();
        FabricClientNetwork.init();

        registerKeyBindings();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            events.onClientTick(client);

            while (TOGGLE_MINIMAP.consumeClick()) events.onToggleMinimap();
            while (OPEN_FULLSCREEN.consumeClick()) events.onOpenFullscreen(client);
            while (ZOOM_IN.consumeClick()) events.onZoomIn();
            while (ZOOM_OUT.consumeClick()) events.onZoomOut();
            while (CREATE_WAYPOINT.consumeClick()) events.onCreateWaypoint(client);
            while (OPEN_SETTINGS.consumeClick()) events.onOpenSettings(client);

            LocalPlayer player = client.player;
            if (player != null) {
                if (wasDead && !player.isDeadOrDying()) {
                    events.onPlayerDeath(player);
                }
                wasDead = player.isDeadOrDying();

            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            events.onPlayerLogout();
        });
    }

    private void registerKeyBindings() {
        TOGGLE_MINIMAP = new KeyMapping("key.odysseymap.toggle", InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_TOGGLE_MINIMAP, OpenMapDeltaKeyMappings.CATEGORY);
        OPEN_FULLSCREEN = new KeyMapping("key.odysseymap.fullscreen", InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_OPEN_FULLSCREEN, OpenMapDeltaKeyMappings.CATEGORY);
        ZOOM_IN = new KeyMapping("key.odysseymap.zoom_in", InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_ZOOM_IN, OpenMapDeltaKeyMappings.CATEGORY);
        ZOOM_OUT = new KeyMapping("key.odysseymap.zoom_out", InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_ZOOM_OUT, OpenMapDeltaKeyMappings.CATEGORY);
        CREATE_WAYPOINT = new KeyMapping("key.odysseymap.waypoint", InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_CREATE_WAYPOINT, OpenMapDeltaKeyMappings.CATEGORY);
        OPEN_SETTINGS = new KeyMapping("key.odysseymap.settings", InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_OPEN_SETTINGS, OpenMapDeltaKeyMappings.CATEGORY);
    }
}
