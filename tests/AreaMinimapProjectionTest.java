package net.aosankaku.openmapdelta.area;

public final class AreaMinimapProjectionTest {
    public static void main(String[] args) {
        var northUp = AreaMinimapProjection.project(110, 200, 100, 200, 2, 0, 100);
        near(northUp.x(), 55); near(northUp.y(), 50);
        var rotated = AreaMinimapProjection.project(110, 200, 100, 200, 2, 90, 100);
        near(rotated.x(), 50); near(rotated.y(), 55);
        var negative = AreaMinimapProjection.project(-110, -205, -100, -200, 2, 0, 100);
        near(negative.x(), 45); near(negative.y(), 47.5);
        check(AreaMinimapProjection.fits(50, 50, 12, 5, 100, true), "central label fits a circular map");
        check(!AreaMinimapProjection.fits(85, 85, 8, 5, 100, true), "label corners cannot leak outside a circular map");
        check(AreaMinimapProjection.fits(85, 85, 8, 5, 100, false), "same label fits a square map");
        check(!AreaMinimapProjection.fits(98, 50, 8, 5, 100, false), "entire label stays inside square map");
        System.out.println("PASS AreaMinimapProjectionTest: 10 checks");
    }
    private static void near(double value, double expected) { check(Math.abs(value - expected) < 1e-9, "rotation and scale stay aligned"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
