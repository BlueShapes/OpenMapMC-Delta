package net.aosankaku.openmapdelta.config;

public final class OpenMapDeltaConfig {
    public static final ConfigValue<MinimapShape> SHAPE = new ConfigValue<>(MinimapShape.CIRCLE);

    private OpenMapDeltaConfig() {}

    public enum MinimapShape { CIRCLE, SQUARE }

    public static final class ConfigValue<T> {
        private T value;
        private ConfigValue(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }
}
