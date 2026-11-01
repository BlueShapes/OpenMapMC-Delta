package net.aosankaku.openmapdelta.config;

/** Thin mutable test surface for the two settings read by the real controller. */
public final class OpenMapDeltaConfig {
    public static final Value MAP_MOD_ENABLED = new Value(true);
    public static final Value EXTERNAL_PLAYERS_ENABLED = new Value(true);
    private OpenMapDeltaConfig() { }
    public static final class Value {
        private volatile boolean value;
        public Value(boolean initial) { value = initial; }
        public boolean get() { return value; }
        public void set(boolean value) { this.value = value; }
    }
}
