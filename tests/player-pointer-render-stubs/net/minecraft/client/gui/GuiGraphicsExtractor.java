package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import testsupport.RenderRecorder;

public class GuiGraphicsExtractor {
    private final PoseStack pose = new PoseStack();
    public boolean failBlit;
    public PoseStack pose() { return pose; }
    public void fill(int x1, int y1, int x2, int y2, int color) {
        RenderRecorder.fills.add(new RenderRecorder.Fill(pose.snapshot(), x1, y1, x2, y2, color));
    }
    public void blit(Object pipeline, net.minecraft.resources.Identifier texture, int x, int y, float u, float v,
                     int width, int height, int uvWidth, int uvHeight, int color) {
        RenderRecorder.add(pose, texture, x, y, width, height, u, v, uvWidth, uvHeight,
                uvWidth, uvHeight, color, pipeline);
    }
    public void blit(Object pipeline, net.minecraft.resources.Identifier texture, int x, int y, float u, float v,
                     int width, int height, int uvWidth, int uvHeight, int textureWidth, int textureHeight) {
        if (failBlit) throw new IllegalStateException("Test skin drawing failure");
        RenderRecorder.add(pose, texture, x, y, width, height, u, v, uvWidth, uvHeight,
                textureWidth, textureHeight, 0xFFFFFFFF, pipeline);
    }
}
