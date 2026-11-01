package net.aosankaku.openmapdelta.config;

public final class OpenMapDeltaConfig {
    public static final BooleanSetting SHOW_SHARED_WAYPOINTS = new BooleanSetting();
    public static void save() { }
    public static final class BooleanSetting {
        public void set(boolean value) { }
    }
}
