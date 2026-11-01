package net.minecraft.client.renderer;

import net.minecraft.resources.ResourceLocation;

public record RenderType(String shaderPath, ResourceLocation texture) {
    public static RenderType textSeeThrough(ResourceLocation texture) {
        return new RenderType("textSeeThrough", texture);
    }
    public static RenderType textBackgroundSeeThrough() {
        return new RenderType("textBackgroundSeeThrough", null);
    }
}
