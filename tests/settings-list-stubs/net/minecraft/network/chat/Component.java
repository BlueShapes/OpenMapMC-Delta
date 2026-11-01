package net.minecraft.network.chat;

public final class Component {
    private final String text;
    private Component(String text) { this.text = text; }
    public static Component literal(String text) { return new Component(text); }
    public static Component translatable(String key) { return new Component(key); }
    @Override public String toString() { return text; }
}
