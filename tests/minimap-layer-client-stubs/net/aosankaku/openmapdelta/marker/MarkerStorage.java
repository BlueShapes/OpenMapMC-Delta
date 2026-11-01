package net.aosankaku.openmapdelta.marker;

public final class MarkerStorage {
    public static int loadCount;
    private MarkerStorage() {}
    public static void load() { loadCount++; }
}
