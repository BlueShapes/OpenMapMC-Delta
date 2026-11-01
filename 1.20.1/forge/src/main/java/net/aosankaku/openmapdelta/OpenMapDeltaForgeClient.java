// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta;

import com.mojang.blaze3d.platform.InputConstants;
import net.aosankaku.openmapdelta.client.ClientEvents;
import net.aosankaku.openmapdelta.client.ClientBootstrap;
import net.aosankaku.openmapdelta.client.OpenMapDeltaKeyMappings;
import net.aosankaku.openmapdelta.render.MinimapHudRenderer;
import net.aosankaku.openmapdelta.render.WorldWaypointRenderer;
import net.aosankaku.openmapdelta.network.ForgeNetwork;
import net.aosankaku.openmapdelta.network.ForgeLocationContextClientNetwork;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class OpenMapDeltaForgeClient {

    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping OPEN_FULLSCREEN;
    private static KeyMapping ZOOM_IN;
    private static KeyMapping ZOOM_OUT;
    private static KeyMapping CREATE_WAYPOINT;
    private static KeyMapping OPEN_SETTINGS;

    public OpenMapDeltaForgeClient(IEventBus modBus) {
        SharedWaypointClient.configureSender(ForgeNetwork::sendSharedRequest);
        ServerConfigClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (!ForgeNetwork.canSendServerConfig(connection)) return;
            ForgeNetwork.sendServerConfigRequest(request);
        });
        ForgeLocationContextClientNetwork.init();
        ClientBootstrap.init();
        modBus.addListener(this::onRegisterKeys);
        modBus.addListener(this::onRegisterOverlays);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        TOGGLE_MINIMAP = new KeyMapping("key.odysseymap.toggle",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_TOGGLE_MINIMAP, OpenMapDeltaKeyMappings.CATEGORY);
        OPEN_FULLSCREEN = new KeyMapping("key.odysseymap.fullscreen",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_OPEN_FULLSCREEN, OpenMapDeltaKeyMappings.CATEGORY);
        ZOOM_IN = new KeyMapping("key.odysseymap.zoom_in",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_ZOOM_IN, OpenMapDeltaKeyMappings.CATEGORY);
        ZOOM_OUT = new KeyMapping("key.odysseymap.zoom_out",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_ZOOM_OUT, OpenMapDeltaKeyMappings.CATEGORY);
        CREATE_WAYPOINT = new KeyMapping("key.odysseymap.waypoint",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_CREATE_WAYPOINT, OpenMapDeltaKeyMappings.CATEGORY);
        OPEN_SETTINGS = new KeyMapping("key.odysseymap.settings",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OpenMapDeltaKeyMappings.KEY_OPEN_SETTINGS, OpenMapDeltaKeyMappings.CATEGORY);
        event.register(TOGGLE_MINIMAP);
        event.register(OPEN_FULLSCREEN);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(CREATE_WAYPOINT);
        event.register(OPEN_SETTINGS);
    }

    private void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("minimap", (gui, graphics, partialTick, screenWidth, screenHeight) ->
                MinimapHudRenderer.render(graphics, partialTick, screenWidth, screenHeight));
    }

    private final ClientEvents events = new ClientEvents();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        events.onClientTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (TOGGLE_MINIMAP.consumeClick()) events.onToggleMinimap();
        while (OPEN_FULLSCREEN.consumeClick()) events.onOpenFullscreen(mc);
        while (ZOOM_IN.consumeClick()) events.onZoomIn();
        while (ZOOM_OUT.consumeClick()) events.onZoomOut();
        while (CREATE_WAYPOINT.consumeClick()) events.onCreateWaypoint(mc);
        while (OPEN_SETTINGS.consumeClick()) events.onOpenSettings(mc);
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        events.onPlayerLogout();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) events.onLevelUnload();
    }


    @SubscribeEvent
    public void onBlockChange(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) {
            var pos = event.getPos();
            events.onBlockChange(Minecraft.getInstance(), pos.getX(), pos.getZ());
        }
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            WorldWaypointRenderer.renderLabels(mc.font);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (mc.level == null) return;
        WorldWaypointRenderer.render(
                event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.font, event.getCamera(), mc.level,
                event.getCamera().getPosition(), event.getPartialTick(), mc.level.getGameTime());
    }
}
