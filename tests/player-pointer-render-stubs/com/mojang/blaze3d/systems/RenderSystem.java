package com.mojang.blaze3d.systems;

import java.util.Arrays;

public final class RenderSystem {
    private static float[] shaderColor = {1, 1, 1, 1};
    private static Object texture;
    private RenderSystem() {}
    public static float[] getShaderColor() { return shaderColor.clone(); }
    public static void setShaderColor(float red, float green, float blue, float alpha) {
        shaderColor = new float[] {red, green, blue, alpha};
    }
    public static void enableBlend() {}
    public static void setShaderTexture(int slot, Object value) { texture = value; }
    public static Object currentTexture() { return texture; }
    public static void reset() { shaderColor = new float[] {1, 1, 1, 1}; texture = null; }
}
