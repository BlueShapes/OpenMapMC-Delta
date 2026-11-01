package net.minecraft.client.gui.components;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class AbstractSliderButton {
    protected double value;
    public boolean active = true;
    public boolean visible = true;
    private final int width;
    private Component message;
    private Tooltip tooltip;
    private boolean canChangeValue;

    protected AbstractSliderButton(int x, int y, int width, int height, Component message, double value) {
        this.width = width;
        this.message = message;
        this.value = value;
        updateMessage();
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32 || keyCode == 257) {
            canChangeValue = !canChangeValue;
            return true;
        }
        if (!canChangeValue || (keyCode != 263 && keyCode != 262)) return false;
        setValue(value + (keyCode == 263 ? -1.0 : 1.0) / (width - 8));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.isSelection()) {
            canChangeValue = !canChangeValue;
            return true;
        }
        if (!canChangeValue || (!event.isLeft() && !event.isRight())) return false;
        setValue(value + (event.isLeft() ? -1.0 : 1.0) / (width - 8));
        return true;
    }

    protected void setValue(double next) {
        double previous = value;
        value = Math.max(0.0, Math.min(1.0, next));
        if (Double.compare(previous, value) != 0) applyValue();
        updateMessage();
    }

    public void moveThumbTo(double progress) { setValue(progress); }
    public double progress() { return value; }
    public Component getMessage() { return message; }
    protected void setMessage(Component message) { this.message = message; }
    protected void setTooltip(Tooltip tooltip) { this.tooltip = tooltip; }
    public Tooltip tooltip() { return tooltip; }
    public void onRelease(double mouseX, double mouseY) {}
    public void onRelease(MouseButtonEvent event) {}

    protected abstract void updateMessage();
    protected abstract void applyValue();
}
