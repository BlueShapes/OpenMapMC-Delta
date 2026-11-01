package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Supplier;

public final class DynamicTexture implements AutoCloseable {
    public final NativeImage image;
    public boolean uploaded;
    public boolean closed;
    public boolean throwOnUpload;
    public boolean throwOnClose;
    public DynamicTexture(Supplier<String> name, NativeImage image) { this.image = image; }
    public void upload() {
        if (throwOnUpload) throw new IllegalStateException("synthetic upload failure");
        uploaded = true;
    }
    @Override public void close() {
        closed = true;
        image.close();
        if (throwOnClose) throw new IllegalStateException("synthetic close failure");
    }
}
