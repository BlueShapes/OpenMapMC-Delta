package net.aosankaku.openmapdelta.marker;

public final class EntityIconFramingTest {
    private static int checks;

    public static void main(String[] args) {
        twoBlockEntityUsesMostOfTheCaptureCanvas();
        frameKeepsTopAboveFeetAndCentersTheEntity();
        projectedModelDepthPreventsSideViewFishClipping();
        System.out.println("Entity icon framing checks passed (" + checks + ").");
    }

    private static void twoBlockEntityUsesMostOfTheCaptureCanvas() {
        EntityIconFraming.Frame frame = EntityIconFraming.fit(0.8F, 2.0F, 64, 6.0F);
        check(Math.abs(frame.scale() - 26.0F) < 0.001F,
                "a two-block entity is scaled in block units, not model-part sixteenths");
        check(Math.abs(frame.heightPixels() - 52.0F) < 0.001F,
                "a two-block entity fills the 52px inner height of the 64px image");
    }

    private static void frameKeepsTopAboveFeetAndCentersTheEntity() {
        EntityIconFraming.Frame frame = EntityIconFraming.fit(1.0F, 2.0F, 64, 6.0F);
        float feet = frame.screenY(0.0F);
        float center = frame.screenY(1.0F);
        float top = frame.screenY(2.0F);
        check(feet > center && center > top, "top, center, and feet stay upright in screen coordinates");
        check(Math.abs(top - 6.0F) < 0.001F && Math.abs(feet - 58.0F) < 0.001F,
                "positive world height maps upward while preserving equal frame padding");
    }

    private static void projectedModelDepthPreventsSideViewFishClipping() {
        EntityIconFraming.Frame cod = EntityIconFraming.fitProjectedBounds(
                -0.46875F, -0.22F, 0.46875F, 0.22F, 64, 6.0F);
        EntityIconFraming.Frame salmon = EntityIconFraming.fitProjectedBounds(
                -0.78F, -0.31F, 0.78F, 0.31F, 64, 6.0F);
        check(Math.abs(cod.widthPixels() - 52.0F) < 0.001F,
                "cod side-view length from projected model depth fits the inner canvas");
        check(Math.abs(salmon.widthPixels() - 52.0F) < 0.001F,
                "salmon variant length from projected model depth fits the inner canvas");
        check(cod.heightPixels() < cod.widthPixels() && salmon.heightPixels() < salmon.widthPixels(),
                "fish keep their model-derived horizontal aspect ratio without cropping the tail");
    }

    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
}
