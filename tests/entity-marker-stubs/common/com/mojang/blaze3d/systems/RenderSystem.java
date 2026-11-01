package com.mojang.blaze3d.systems;

public final class RenderSystem {
    private static float[] shaderColor = {1, 1, 1, 1};
    private RenderSystem() {}
    public static float[] getShaderColor() { return shaderColor.clone(); }
    public static void setShaderColor(float red, float green, float blue, float alpha) {
        shaderColor = new float[] {red, green, blue, alpha};
    }
    public static void enableBlend() {}
}
