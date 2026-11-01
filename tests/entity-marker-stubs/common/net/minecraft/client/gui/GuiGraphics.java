package net.minecraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class GuiGraphics {
    private final GraphicsPose pose = new GraphicsPose();
    public int blitCount;
    public int fillCount;
    public int lastLeft, lastTop, lastWidth, lastHeight, lastU, lastV, lastTextureWidth, lastTextureHeight;
    public int lastAlpha;
    public int lastFillColor;
    public float lastDrawWidth, lastDrawHeight;
    public boolean throwOnBlit;

    public GraphicsPose pose() { return pose; }
    public void flush() {}
    public void renderOutline(int x, int y, int width, int height, int color) {}
    public void outline(int x, int y, int width, int height, int color) {}
    public void renderItem(ItemStack stack, int x, int y) {}
    public void item(ItemStack stack, int x, int y) {}
    public void fill(int left, int top, int right, int bottom, int color) {
        fillCount++;
        lastFillColor = color;
    }

    public void blit(ResourceLocation location, int left, int top, float u, float v,
                     int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        record(left, top, sourceWidth, sourceHeight, u, v, sourceWidth, sourceHeight,
                textureWidth, textureHeight, RenderSystem.getShaderColor()[3]);
    }

    public void blit(RenderPipelines pipeline, Identifier location, int left, int top,
                     float u, float v, int width, int height, int textureWidth, int textureHeight) {
        record(left, top, width, height, u, v, width, height, textureWidth, textureHeight, 1);
    }

    public void blit(RenderPipelines pipeline, Identifier location, int left, int top,
                     float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
        record(left, top, width, height, u, v, width, height, textureWidth, textureHeight,
                (color >>> 24) / 255.0F);
    }

    private void record(int left, int top, int width, int height, float u, float v,
                        int sourceWidth, int sourceHeight, int textureWidth, int textureHeight, float alpha) {
        blitCount++;
        lastLeft = Math.round(pose.translateX + left * pose.scaleX);
        lastTop = Math.round(pose.translateY + top * pose.scaleY);
        lastWidth = sourceWidth;
        lastHeight = sourceHeight;
        lastU = (int) u;
        lastV = (int) v;
        lastTextureWidth = textureWidth;
        lastTextureHeight = textureHeight;
        lastAlpha = Math.round(alpha * 255);
        lastDrawWidth = sourceWidth * pose.scaleX;
        lastDrawHeight = sourceHeight * pose.scaleY;
        if (throwOnBlit) throw new IllegalStateException("Synthetic sprite draw failure");
    }
}
