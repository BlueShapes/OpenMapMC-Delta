package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class Button {
    public boolean active = true;
    private int x, y, width, height;
    private final Consumer<Button> onPress;
    private Component message;
    private Tooltip tooltip;

    protected Button(Component message, int x, int y, int width, int height, Consumer<Button> onPress) {
        this.message = message;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onPress = onPress;
    }

    public static Builder builder(Component message, Consumer<Button> onPress) { return new Builder(message, onPress); }
    public void setMessage(Component message) { this.message = message; }
    public String message() { return message.getString(); }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setTooltip(Tooltip tooltip) { this.tooltip = tooltip; }
    public Tooltip tooltip() { return tooltip; }
    public void press() { if (active) onPress.accept(this); }

    public static final class Builder {
        private final Component message;
        private final Consumer<Button> onPress;
        private int x, y, width, height;
        private Builder(Component message, Consumer<Button> onPress) { this.message = message; this.onPress = onPress; }
        public Builder bounds(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; return this; }
        public Builder createNarration(java.util.function.Function<Component, Component> narration) { return this; }
        public Button build() { return new Button(message, x, y, width, height, onPress); }
    }
}
