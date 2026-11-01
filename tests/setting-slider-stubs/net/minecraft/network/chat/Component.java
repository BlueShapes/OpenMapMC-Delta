package net.minecraft.network.chat;

public final class Component {
    private final String text;

    private Component(String text) { this.text = text; }
    public static Component empty() { return new Component(""); }
    public static Component literal(String text) { return new Component(text); }
    public String getString() { return text; }
    public Component copy() { return new Component(text); }
    public Component append(String suffix) { return new Component(text + suffix); }
    public Component append(Component suffix) { return new Component(text + suffix.text); }
}
