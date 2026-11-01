package com.mojang.blaze3d.vertex;

import java.util.ArrayDeque;
import java.util.Deque;

/** Small affine-pose recorder used to execute RenderUtil without Minecraft. */
public final class PoseStack {
    private Matrix matrix = new Matrix();
    private final Deque<Matrix> stack = new ArrayDeque<>();

    public void pushPose() { stack.push(matrix.copy()); }
    public void popPose() { matrix = stack.pop(); }
    public void pushMatrix() { pushPose(); }
    public void popMatrix() { popPose(); }
    public void translate(float x, float y, float z) { matrix.translate(x, y); }
    public void translate(double x, double y, double z) { matrix.translate(x, y); }
    public void translate(int x, int y) { matrix.translate(x, y); }
    public void translate(float x, float y) { matrix.translate(x, y); }
    public void scale(float x, float y, float z) { matrix.scale(x, y); }
    public void scale(float x, float y) { matrix.scale(x, y); }
    public void rotate(float radians) { matrix.rotate(radians); }
    public void mulPose(Object rotation) { matrix.rotate(((com.mojang.math.Axis.Rotation) rotation).radians); }

    public Matrix snapshot() { return matrix.copy(); }
    public int depth() { return stack.size(); }

    public static final class Matrix {
        private double a = 1, b, c, d = 1, tx, ty;

        private Matrix copy() {
            Matrix result = new Matrix();
            result.a = a; result.b = b; result.c = c; result.d = d; result.tx = tx; result.ty = ty;
            return result;
        }

        private void translate(double x, double y) {
            tx += a * x + c * y;
            ty += b * x + d * y;
        }

        private void scale(double x, double y) {
            a *= x; b *= x; c *= y; d *= y;
        }

        private void rotate(double angle) {
            double cosine = Math.cos(angle), sine = Math.sin(angle);
            double nextA = a * cosine + c * sine;
            double nextC = -a * sine + c * cosine;
            double nextB = b * cosine + d * sine;
            double nextD = -b * sine + d * cosine;
            a = nextA; b = nextB; c = nextC; d = nextD;
        }

        public double[] transform(double x, double y) {
            return new double[] {a * x + c * y + tx, b * x + d * y + ty};
        }

        public double[] values() { return new double[] {a, b, c, d, tx, ty}; }
    }
}
