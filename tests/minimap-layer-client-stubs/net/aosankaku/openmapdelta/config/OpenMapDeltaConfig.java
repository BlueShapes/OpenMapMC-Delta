package net.aosankaku.openmapdelta.config;

public final class OpenMapDeltaConfig {
    public static final Value<MinimapLayerMode> MINIMAP_LAYER_MODE = new Value<>(MinimapLayerMode.SURFACE);
    public static final Value<Integer> MINIMAP_CAVE_Y = new Value<>(0);
    public static final Value<Integer> CAVE_LAYER_STEP = new Value<>(8);
    private OpenMapDeltaConfig() {}
    public static final class Value<T> {
        private T value;
        public Value(T initial) { value = initial; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }
}
