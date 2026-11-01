package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;

public final class DynamicTexture implements AutoCloseable {
    private final NativeImage image;
    private int uploadCount;

    public DynamicTexture(NativeImage image) { this.image = image; }
    public NativeImage image() { return image; }
    public int uploadCount() { return uploadCount; }
    public void upload() { uploadCount++; }
    @Override public void close() {}
}
