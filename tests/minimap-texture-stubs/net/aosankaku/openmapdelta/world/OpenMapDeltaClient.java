package net.aosankaku.openmapdelta.world;

public final class OpenMapDeltaClient {
    public static final int SURFACE_LAYER = Integer.MIN_VALUE;
    private static TileCache surfaceCache = new TileCache();
    private static TileCache minimapCache = surfaceCache;
    private static int selectedMinimapLayer = SURFACE_LAYER;

    private OpenMapDeltaClient() {}

    public static TileCache getTileCache() { return surfaceCache; }
    public static TileCache getMinimapTileCache() { return minimapCache; }
    public static int getSelectedMinimapLayer() { return selectedMinimapLayer; }
    public static TileCache getActiveTileCache() {
        return selectedMinimapLayer == SURFACE_LAYER ? surfaceCache : minimapCache;
    }

    public static void select(TileCache surface, TileCache minimap, int layer) {
        surfaceCache = surface;
        minimapCache = minimap;
        selectedMinimapLayer = layer;
    }

    public static void reset() {
        surfaceCache = new TileCache();
        minimapCache = surfaceCache;
        selectedMinimapLayer = SURFACE_LAYER;
    }
}
