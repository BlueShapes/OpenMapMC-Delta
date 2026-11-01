package net.minecraft.client.gui.narration;

import net.minecraft.network.chat.Component;

public interface NarrationElementOutput {
    void add(NarratedElementType type, Component component);
}
