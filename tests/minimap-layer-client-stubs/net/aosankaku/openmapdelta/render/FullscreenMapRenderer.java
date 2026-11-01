package net.aosankaku.openmapdelta.render;

public final class FullscreenMapRenderer {
    public static int resetCount, invalidateCount;
    private FullscreenMapRenderer() {}
    public static void reset() { resetCount++; }
    public static void invalidate() { invalidateCount++; }
}
