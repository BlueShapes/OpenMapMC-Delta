package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Supplier;

public class DynamicTexture implements AutoCloseable {
    private static DynamicTexture lastUploaded;
    private static int uploadCount;
    private static boolean failNextUpload;
    private final NativeImage image;
    private boolean closed;

    public DynamicTexture(NativeImage image) { this.image = image; }
    public DynamicTexture(Supplier<String> name, NativeImage image) { this.image = image; }
    public void upload() {
        if (failNextUpload) {
            failNextUpload = false;
            throw new IllegalStateException("simulated upload failure");
        }
        lastUploaded = this;
        uploadCount++;
    }
    public NativeImage image() { return image; }
    public boolean isClosed() { return closed; }
    public static DynamicTexture lastUploaded() { return lastUploaded; }
    public static int uploadCount() { return uploadCount; }
    public static void failNextUpload() { failNextUpload = true; }
    @Override public void close() { closed = true; }
}
