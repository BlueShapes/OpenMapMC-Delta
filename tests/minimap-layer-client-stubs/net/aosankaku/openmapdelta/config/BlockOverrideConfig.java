package net.aosankaku.openmapdelta.config;

public final class BlockOverrideConfig {
    public static int reloadCount;
    private BlockOverrideConfig() {}
    public static void reload() { reloadCount++; }
}
