// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.TileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Composes fullscreen terrain on the client tick and leaves drawing to the GUI frame. */
public final class FullscreenMapRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final FullscreenMapTexture TEXTURE = new FullscreenMapTexture();

    private static FullscreenMapViewport composedViewport;
    private static ClientLevel lastLevel;
    private static TileCache lastCache;
    private static int lastLayer = OpenMapDeltaClient.SURFACE_LAYER;
    private static Object lastDimension;
    private static long clientTicks;
    private static long lastComposeTick = Long.MIN_VALUE;
    private static long lastComposedRevision = Long.MIN_VALUE;
    private static boolean forceCompose = true;

    private FullscreenMapRenderer() {}

    /** Called on the client tick thread, including on 26.x deferred GUI versions. */
    public static void tickCompose(Minecraft mc, FullscreenMapScreen screen) {
        ClientLevel level = mc.level;
        if (level == null) return;
        clientTicks++;

        var panel = screen.getMapPanel();
        if (panel == null) return;
        int width = panel.getWidth();
        int height = panel.getHeight();
        if (width <= 0 || height <= 0) return;

        Object dimension = level.dimension();
        TileCache activeCache = OpenMapDeltaClient.getActiveTileCache();
        if (activeCache == null) return;
        int activeLayer = OpenMapDeltaClient.getSelectedMapLayer();
        boolean layerChanged = lastCache != activeCache || lastLayer != activeLayer;
        boolean levelChanged = lastLevel != null && (lastLevel != level || !lastDimension.equals(dimension));
        if (levelChanged || layerChanged) {
            TEXTURE.clear(mc);
            composedViewport = null;
            forceCompose = true;
        }
        lastLevel = level;
        lastDimension = dimension;
        lastCache = activeCache;
        lastLayer = activeLayer;

        double bpp = FullscreenMapZoom.safeBlocksPerPixel(screen.getBlocksPerPixel());
        double guiScale = mc.getWindow().getGuiScale();
        double panX = screen.getPanX();
        double panZ = screen.getPanZ();
        boolean safeMode = OpenMapDeltaConfig.MAP_SAFE_RENDER_MODE.get();
        boolean shapeChanged = composedViewport == null
                || !composedViewport.hasSameShape(width, height, bpp, guiScale);
        boolean recenter = !shapeChanged && !composedViewport.containsView(panX, panZ);
        FullscreenMapViewport requestedViewport = shapeChanged || recenter
                ? FullscreenMapViewport.forView(width, height, bpp, panX, panZ, guiScale)
                : composedViewport;
        // Request saved terrain directly; live column scans only refresh loaded world chunks.
        activeCache.preloadArea(level.dimension(), requestedViewport.getOriginX(), requestedViewport.getOriginZ(),
                inclusiveWorldEnd(requestedViewport.getOriginX(), requestedViewport.getTextureWidth(),
                        requestedViewport.getSampleStride()),
                inclusiveWorldEnd(requestedViewport.getOriginZ(), requestedViewport.getTextureHeight(),
                        requestedViewport.getSampleStride()));
        boolean urgent = forceCompose || shapeChanged || layerChanged
                || activeCache.getRevision() != lastComposedRevision;
        long refreshInterval = safeMode ? 30L : 10L;
        boolean refreshDue = lastComposeTick == Long.MIN_VALUE
                || clientTicks - lastComposeTick >= refreshInterval;
        if (!urgent && !recenter && !refreshDue) return;
        // Coalesce live column updates; shape/layer changes and recentering still compose immediately.
        if (!shapeChanged && !layerChanged && !recenter && lastComposeTick != Long.MIN_VALUE
                && clientTicks - lastComposeTick < 3L) return;

        try {
            if (TEXTURE.compose(mc, level, requestedViewport)) {
                composedViewport = requestedViewport;
                lastComposeTick = clientTicks;
                lastComposedRevision = activeCache.getRevision();
                forceCompose = false;
            }
        } catch (Exception e) {
            LOG.warn("Fullscreen map compose failed; will retry on a later client tick", e);
        }
    }

    /** Force a tick-thread compose after opening, recentering, or changing zoom. */
    public static void invalidate() {
        forceCompose = true;
    }

    private static int inclusiveWorldEnd(int origin, int textureExtent, double sampleStride) {
        double endExclusive = origin + (double) textureExtent * sampleStride;
        if (!Double.isFinite(endExclusive) || endExclusive >= (double) Integer.MAX_VALUE + 1.0) {
            return Integer.MAX_VALUE;
        }
        if (endExclusive <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) Math.ceil(endExclusive) - 1;
    }

    /** Release the dedicated texture and all cached frame metadata on logout/reset. */
    public static void reset() {
        TEXTURE.clear(Minecraft.getInstance());
        composedViewport = null;
        lastLevel = null;
        lastCache = null;
        lastLayer = OpenMapDeltaClient.SURFACE_LAYER;
        lastDimension = null;
        clientTicks = 0;
        lastComposeTick = Long.MIN_VALUE;
        lastComposedRevision = Long.MIN_VALUE;
        forceCompose = true;
    }

    public static FullscreenMapTexture getTexture() { return TEXTURE; }
    public static FullscreenMapViewport getComposedViewport() { return composedViewport; }
}
