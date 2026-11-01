package net.minecraft.network.chat;

public class Component {
    private final String text;
    private Component(String text) { this.text = text; }
    public static Component literal(String text) { return new Component(text); }
    public static Component translatable(String key, Object... args) { return new Component(key); }
    public String getString() { return text; }
    public Component append(String suffix) { return new Component(text + suffix); }
    public Component append(Component suffix) { return new Component(text + suffix.getString()); }
}
