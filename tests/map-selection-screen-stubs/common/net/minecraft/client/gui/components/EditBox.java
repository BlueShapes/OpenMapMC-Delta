package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Small widget fixture for rendering screens that register text fields as children. */
public final class EditBox extends Button {
    private String value = "";
    private Component hint = Component.empty();
    private boolean editable = true;
    private int maxLength = 32;

    public EditBox(Font font, int x, int y, int width, int height, Component message) {
        super(message, x, y, width, height, button -> {});
    }
    public void setMaxLength(int value) { maxLength = value; }
    public int maxLength() { return maxLength; }
    public void setValue(String value) { this.value = value; }
    public String getValue() { return value; }
    public void setHint(Component value) { hint = value; }
    public Component hint() { return hint; }
    public void setEditable(boolean value) { editable = value; }
    public boolean isEditable() { return editable; }
}
