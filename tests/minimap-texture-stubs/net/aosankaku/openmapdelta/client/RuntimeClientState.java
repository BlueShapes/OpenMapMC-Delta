package net.aosankaku.openmapdelta.client;

public final class RuntimeClientState {
    private static int zoomBlocksPerPixel = 1;

    private RuntimeClientState() {}
    public static int getZoomBlocksPerPixel() { return zoomBlocksPerPixel; }
    public static void setZoomBlocksPerPixel(int value) { zoomBlocksPerPixel = value; }
}
