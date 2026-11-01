// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.compat.TransitWaypointManager;
import net.aosankaku.openmapdelta.compat.TransitRailManager;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.gui.MapSettingsScreen;
import net.aosankaku.openmapdelta.gui.WaypointEditScreen;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.WaypointImportManager;
import net.aosankaku.openmapdelta.render.FullscreenMapRenderer;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientEvents {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static boolean minimapVisible = true;
    private final CacheRevisionTracker cacheRevisionTracker = new CacheRevisionTracker();
    private int scanFrameCounter;

    public static boolean isMinimapVisible() {
        return OpenMapDeltaConfig.MAP_MOD_ENABLED.get() && minimapVisible && OpenMapDeltaConfig.ENABLED.get();
    }

    public void onClientTick(Minecraft mc) {
        net.aosankaku.openmapdelta.marker.EntityIconSprites.tick(mc);
        MapModSelectionController.tick(mc);
        net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient.tick(mc);
        net.aosankaku.openmapdelta.client.ServerConfigClient.tick();
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) {
            SleepPointTracker.reset();
            TransitRailManager.get().reset();
            net.aosankaku.openmapdelta.web.WebMapClient.stop();
            if (mc.gui.screen() instanceof FullscreenMapScreen) mc.gui.setScreen(null);
            return;
        }
        MinimapInfoProvider.tick(mc);
        cacheRevisionTracker.tick();
        boolean sessionChanged = OpenMapDeltaClient.tick(mc);
        net.aosankaku.openmapdelta.area.AreaManager.tick(mc);
        TransitWaypointManager.get().tick(mc);
        TransitRailManager.get().tick(mc, mc.gui.screen() instanceof FullscreenMapScreen);
        if (sessionChanged) {
            long revision = OpenMapDeltaClient.getTileCache().getRevision();
            FullscreenMapRenderer.invalidate();
            cacheRevisionTracker.forceInvalidation(revision, OpenMapDeltaConfig.MAP_SAFE_RENDER_MODE.get());
        }
        if (mc.player == null || mc.level == null) {
            SleepPointTracker.reset();
            net.aosankaku.openmapdelta.web.WebMapClient.tick(mc);
            invalidateFullscreenAfterCacheChange();
            return;
        }

        WaypointImportManager.get().tick(mc);
        SleepPointTracker.tick(mc);
        LocationSharing.tick(mc);
        net.aosankaku.openmapdelta.death.DeathPointController.get().tick(mc);

        scanFrameCounter++;
        if (scanFrameCounter >= OpenMapDeltaConfig.effectiveScanInterval()) {
            scanFrameCounter = 0;
            OpenMapDeltaClient.getScanner().tick(mc);
        }

        // Cache revisions include async loads and scans; compose remains on the existing tick path.
        invalidateFullscreenAfterCacheChange();
        if (mc.gui.screen() instanceof FullscreenMapScreen screen) {
            FullscreenMapRenderer.tickCompose(mc, screen);
        }

        MarkerManager.get().tick(mc);
        net.aosankaku.openmapdelta.web.WebMapClient.tick(mc);
    }

    public static void showMinimap() {
        minimapVisible = true;
    }

    public void onToggleMinimap() {
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) return;
        minimapVisible = !minimapVisible;
    }

    public void onOpenFullscreen(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            LOG.warn("Cannot open fullscreen map: player or level is null");
            return;
        }
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get() || !OpenMapDeltaConfig.MAP_FULLSCREEN_ENABLED.get()) return;
        FullscreenMapRenderer.invalidate();
        mc.gui.setScreen(new FullscreenMapScreen());
    }

    public void onOpenSettings(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        mc.gui.setScreen(new MapSettingsScreen(null));
    }

    public void onZoomIn() {
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) return;
        RuntimeClientState.zoomIn();
        OpenMapDeltaClient.getTileCache().markAllDirty();
    }

    public void onZoomOut() {
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) return;
        RuntimeClientState.zoomOut();
        OpenMapDeltaClient.getTileCache().markAllDirty();
    }

    public void onCreateWaypoint(Minecraft mc) {
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        ResourceKey<Level> dim = player.level().dimension();
        BlockPos pos = player.blockPosition();
        String defaultName = Component.translatable("odysseymap.marker.default_name", MarkerManager.get().getWaypoints().size() + 1).getString();
        mc.gui.setScreen(WaypointEditScreen.forCreate(null, dim, pos.getX(), pos.getY(), pos.getZ(), defaultName));
    }

    public void onPlayerLogout() {
        net.aosankaku.openmapdelta.marker.EntityIconSprites.clear(Minecraft.getInstance());
        net.aosankaku.openmapdelta.area.AreaManager.reset();
        net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient.reset();
        net.aosankaku.openmapdelta.client.ServerConfigClient.reset();
        SleepPointTracker.reset();
        TransitRailManager.get().reset();
        net.aosankaku.openmapdelta.network.ClientWorldIdentityState.reset();
        net.aosankaku.openmapdelta.shared.SharedWaypointClient.reset();
        net.aosankaku.openmapdelta.render.WaypointOwnerFace.clear();
        net.aosankaku.openmapdelta.web.WebMapClient.stop();
        net.aosankaku.openmapdelta.render.MarkerIconSpriteCache.clear(Minecraft.getInstance());
        net.aosankaku.openmapdelta.marker.MarkerIconCatalog.invalidateEmojiful();
        WaypointImportManager.get().onPlayerLogout();
        LocationSharing.reset();
        net.aosankaku.openmapdelta.death.DeathPointController.get().reset();
        MinimapInfoProvider.reset();
        ClientServerState.reset();
        OpenMapDeltaClient.reset();
        MarkerManager.get().clearSession();
        cacheRevisionTracker.reset();
        FullscreenMapRenderer.invalidate();
    }

    public void onLevelUnload() {
        net.aosankaku.openmapdelta.marker.EntityIconSprites.clear(Minecraft.getInstance());
        net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient.reset();
        SleepPointTracker.reset();
        TransitRailManager.get().reset();
        net.aosankaku.openmapdelta.marker.MarkerIconCatalog.invalidateEmojiful();
        MinimapInfoProvider.resetMotion();
        OpenMapDeltaClient.resetSession();
        cacheRevisionTracker.reset();
        FullscreenMapRenderer.invalidate();
    }

    public void onPlayerDeath(LocalPlayer player) {
        // DeathPointController captures the death before respawn to retain inventory context.
    }

    private void invalidateFullscreenAfterCacheChange() {
        var tileCache = OpenMapDeltaClient.getTileCache();
        if (tileCache == null) return;
        boolean safeMode = OpenMapDeltaConfig.MAP_SAFE_RENDER_MODE.get();
        if (cacheRevisionTracker.shouldInvalidate(tileCache.getRevision(), safeMode)) {
            FullscreenMapRenderer.invalidate();
        }
    }

    public void onBlockChange(Minecraft mc, int x, int z) {
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) return;
        if (mc.level != null && OpenMapDeltaClient.getTileCache() != null) {
            OpenMapDeltaClient.invalidateArea(mc.level, x, z);
        }
    }
}
