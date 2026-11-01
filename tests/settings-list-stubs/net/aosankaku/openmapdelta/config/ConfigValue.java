package net.aosankaku.openmapdelta.config;

public final class ConfigValue<T> {
    private final T value;
    public ConfigValue(T value) { this.value = value; }
    public T get() { return value; }
}
