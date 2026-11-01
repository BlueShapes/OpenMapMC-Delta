package net.aosankaku.openmapdelta.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import testsupport.RenderRecorder;

import java.util.Arrays;

/** Exercises the real RenderUtil against affine recording poses and vanilla's known sprite alpha bounds. */
public final class VanillaPlayerPointerRenderTest {
    private static final int TINT = 0xBFC08040;
    private static final float[] INITIAL_SHADER = {0.82f, 0.61f, 0.47f, 0.73f};
    private static final float[] ANGLES = {0, 30, 45, 90, 135, 180, 270, 337.25f};
    private static final int[] SIZES = {5, 8, 13, 16, 21};
    private static final String VERSION = System.getProperty("pointer.version", "26.2");
    private static final boolean MODERN = VERSION.startsWith("26.");
    private static int checks;

    private VanillaPlayerPointerRenderTest() {}

    public static void main(String[] args) {
        for (float angle : ANGLES) {
            for (int size : SIZES) verifyPointer(angle, size);
        }
        verifyHeadRemainsCentered();
        if (VERSION.equals("26.2")) verifySmallHeadFallbacks();
        System.out.println("VanillaPlayerPointerRenderTest passed " + checks + " checks for " + VERSION + ".");
    }

    private static void verifyPointer(float angle, int size) {
        RenderRecorder.clear();
        RenderSystem.reset();
        RenderSystem.setShaderColor(INITIAL_SHADER[0], INITIAL_SHADER[1], INITIAL_SHADER[2], INITIAL_SHADER[3]);
        GuiGraphics graphics = new GuiGraphics();
        PoseStack pose = graphics.pose();
        seedParentTransform(pose);
        double[] expectedAnchor = pose.snapshot().transform(73, 41);
        RenderUtil.drawVanillaPlayerPointer(graphics, 73, 41, size, angle, TINT);
        verifyResult(pose, expectedAnchor, angle, size);
        if (!MODERN) verifyClassicRenderState();
    }

    private static void seedParentTransform(PoseStack pose) {
        pose.translate(12.25, -6.75, 0);
        pose.scale(1.3f, 0.7f, 1);
    }

    private static void verifyResult(PoseStack pose, double[] expectedAnchor, float angle, int size) {
        check(RenderRecorder.blits.size() == 1, "one blit for angle=" + angle + ", size=" + size);
        RenderRecorder.Blit blit = RenderRecorder.blits.get(0);
        check(blit.x == -4 && blit.y == -4 && blit.width == 8 && blit.height == 8,
                "unchanged 8x8 destination quad");
        check(blit.u == 0 && blit.v == 0 && blit.uvWidth == 8 && blit.uvHeight == 8,
                "unchanged player-pointer UV bounds");
        check(blit.textureWidth == (VERSION.equals("1.20.1") ? 128 : 8) && blit.textureHeight == blit.textureWidth,
                "unchanged " + VERSION + " texture atlas dimensions");
        String expectedTexture = VERSION.equals("1.20.1")
                ? "minecraft:textures/map/map_icons.png"
                : "minecraft:textures/map/decorations/player.png";
        check(expectedTexture.equals(blit.texture.toString()), "unchanged vanilla pointer texture");

        // Alpha occupies pixels x=2..6 and y=0..6: its pixel-edge center in this quad is (0.5,-0.5).
        double localVisibleCenterX = blit.x + blit.width * (4.5 - blit.u) / blit.uvWidth;
        double localVisibleCenterY = blit.y + blit.height * (3.5 - blit.v) / blit.uvHeight;
        double[] visibleCenter = blit.pose.transform(localVisibleCenterX, localVisibleCenterY);
        near(visibleCenter[0], expectedAnchor[0], 1.0e-5,
                "visible pointer center x at angle=" + angle + ", size=" + size);
        near(visibleCenter[1], expectedAnchor[1], 1.0e-5,
                "visible pointer center y at angle=" + angle + ", size=" + size);

        double[] current = pose.snapshot().values();
        check(pose.depth() == 0, "pose stack balanced");
        double[] parent = {1.3, 0, 0, 0.7, 12.25, -6.75};
        for (int i = 0; i < parent.length; i++) near(current[i], parent[i], 1.0e-6, "parent pose restored");
        if (MODERN) {
            check(blit.color == TINT, "modern tint remains on textured blit");
            check(blit.pipeline == net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    "modern GUI textured pipeline unchanged");
        } else {
            float[] expected = {
                    INITIAL_SHADER[0] * ((TINT >> 16 & 0xFF) / 255.0f),
                    INITIAL_SHADER[1] * ((TINT >> 8 & 0xFF) / 255.0f),
                    INITIAL_SHADER[2] * ((TINT & 0xFF) / 255.0f),
                    INITIAL_SHADER[3] * ((TINT >>> 24) / 255.0f)
            };
            for (int i = 0; i < 4; i++) near(blit.shaderColor[i], expected[i], 1.0e-6, "classic tint unchanged");
        }
    }

    private static void verifyClassicRenderState() {
        check(RenderRecorder.events.equals(Arrays.asList("flush", "flush")), "classic flush-before/flush-after order");
        float[] restored = RenderSystem.getShaderColor();
        for (int i = 0; i < 4; i++) near(restored[i], INITIAL_SHADER[i], 1.0e-6, "classic shader color restored");
    }

    private static void verifyHeadRemainsCentered() {
        RenderRecorder.clear();
        GuiGraphics graphics = new GuiGraphics();
        RenderUtil.drawPlayerHead(graphics, 73, 41, 13, 47.0f);
        verifyHeadBlits();
    }

    private static void verifyHeadBlits() {
        check(RenderRecorder.blits.size() == 2, "player head still draws both skin layers");
        for (RenderRecorder.Blit blit : RenderRecorder.blits) {
            double[] center = blit.pose.transform(blit.x + blit.width / 2.0, blit.y + blit.height / 2.0);
            near(center[0], 73, 1.0e-5, "head center x unchanged");
            near(center[1], 41, 1.0e-5, "head center y unchanged");
        }
    }

    private static void verifySmallHeadFallbacks() {
        for (String failure : new String[]{"null-id", "missing-skin", "failed-blit"}) {
            for (int size : new int[]{1, 3, 4, 12}) {
                RenderRecorder.clear();
                GuiGraphics graphics = new GuiGraphics();
                PoseStack pose = graphics.pose();
                seedParentTransform(pose);
                double[] anchor = pose.snapshot().transform(73, 41);
                graphics.failBlit = failure.equals("failed-blit");
                net.aosankaku.openmapdelta.render.WaypointOwnerFace.textureAvailable = !failure.equals("missing-skin");
                try {
                    RenderUtil.drawPlayerHead(graphics, failure.equals("null-id") ? null : new java.util.UUID(0, 1),
                            "self", 73, 41, size, 37.0f, TINT);
                } finally {
                    net.aosankaku.openmapdelta.render.WaypointOwnerFace.textureAvailable = true;
                }
                check(RenderRecorder.fills.size() == 1, "skin failure draws one fallback square: " + failure);
                var fill = RenderRecorder.fills.get(0);
                double[] center = fill.pose().transform((fill.left() + fill.right()) / 2.0,
                        (fill.top() + fill.bottom()) / 2.0);
                near(center[0], anchor[0], 1e-5, "fallback center x matches selectable head");
                near(center[1], anchor[1], 1e-5, "fallback center y matches selectable head");
                double[] first = fill.pose().transform(fill.left(), fill.top());
                double[] right = fill.pose().transform(fill.right(), fill.top());
                double[] bottom = fill.pose().transform(fill.left(), fill.bottom());
                double area = Math.abs((right[0] - first[0]) * (bottom[1] - first[1])
                        - (right[1] - first[1]) * (bottom[0] - first[0]));
                near(area, size * size * 1.3 * 0.7, 1e-4,
                        "fallback retains the full nonzero glyph area, including size one");
                check(fill.color() == TINT, "fallback keeps player tint");
                check(pose.depth() == 0, "skin fallback restores pose depth");
                double[] actual = pose.snapshot().values();
                double[] expected = {1.3, 0, 0, 0.7, 12.25, -6.75};
                for (int i = 0; i < expected.length; i++) near(actual[i], expected[i], 1e-6,
                        "fallback restores the parent pose after " + failure);
            }
        }
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance,
                message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
