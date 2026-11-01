package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import testsupport.RenderRecorder;

public final class GuiGraphics extends GuiGraphicsExtractor {
    public void flush() { RenderRecorder.events.add("flush"); }
    public void blit(Object texture, int x, int y, int width, int height, float u, float v,
                     int uvWidth, int uvHeight, int textureWidth, int textureHeight) {
        RenderRecorder.add(pose(), texture, x, y, width, height, u, v, uvWidth, uvHeight,
                textureWidth, textureHeight, 0xFFFFFFFF, null);
    }
}
