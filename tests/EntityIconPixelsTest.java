package net.aosankaku.openmapdelta.marker;

import java.nio.ByteBuffer;
import com.mojang.blaze3d.platform.NativeImage;

public final class EntityIconPixelsTest {
    private static int checks;
    public static void main(String[] args) {
        cropsPaddingAndUnpremultipliesBottomUpRgba();
        transparentImageIsRejected();
        malformedReadbackIsRejected();
        System.out.println("Entity icon pixel checks passed (" + checks + ").");
    }
    private static void cropsPaddingAndUnpremultipliesBottomUpRgba() {
        int width = 7, height = 6;
        ByteBuffer bytes = ByteBuffer.allocate(width * height * 4);
        // GPU readback is bottom-up RGBA premultiplied. Place red/green after vertical normalization.
        rgba(bytes, width, height, 2, 1, 128, 0, 0, 128);
        rgba(bytes, width, height, 4, 3, 0, 32, 0, 64);
        NativeImage image = EntityIconPixels.crop(bytes, width, height, 1);
        check(image != null, "visible readback creates an icon");
        check(image.getWidth() == 5 && image.getHeight() == 5, "alpha bounds retain a one-pixel margin");
        check(image.getPixel(1, 1) == 0x80FF0000, "red alpha is unpremultiplied and vertically flipped");
        check(image.getPixel(3, 3) == 0x40008000,
                "green alpha is unpremultiplied and vertically flipped: " + Integer.toHexString(image.getPixel(3, 3)));
        check(image.getPixel(0, 0) == 0, "transparent padding stays transparent");
        image.close();
    }
    private static void transparentImageIsRejected() {
        NativeImage image = EntityIconPixels.crop(ByteBuffer.allocate(16 * 4), 4, 4, 1);
        check(image == null, "all-transparent capture is treated as a failed icon");
    }
    private static void malformedReadbackIsRejected() {
        check(EntityIconPixels.crop(ByteBuffer.allocate(15), 2, 2, 1) == null,
                "short readback cannot publish an invalid texture");
    }
    private static void rgba(ByteBuffer bytes, int width, int height, int x, int y,
                             int red, int green, int blue, int alpha) {
        int bottomUp = (height - 1 - y) * width * 4 + x * 4;
        bytes.put(bottomUp, (byte) red); bytes.put(bottomUp + 1, (byte) green);
        bytes.put(bottomUp + 2, (byte) blue); bytes.put(bottomUp + 3, (byte) alpha);
    }
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
}
