package net.aosankaku.openmapdelta.marker;

public final class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();
    public int clearCount;
    private MarkerManager() {}
    public static MarkerManager get() { return INSTANCE; }
    public void clearSession() { clearCount++; }
}
