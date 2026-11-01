package net.minecraft.client.gui;

public final class GraphicsPose {
    public int depth;
    public float scaleX = 1, scaleY = 1;
    public float translateX, translateY;
    private final java.util.ArrayDeque<float[]> stack = new java.util.ArrayDeque<>();
    public void pushPose() {
        stack.push(new float[] {scaleX, scaleY, translateX, translateY});
        depth++;
    }
    public void popPose() {
        float[] previous = stack.pop();
        scaleX = previous[0]; scaleY = previous[1];
        translateX = previous[2]; translateY = previous[3];
        depth--;
    }
    public void pushMatrix() { pushPose(); }
    public void popMatrix() { popPose(); }
    public void translate(float x, float y, float z) { translate(x, y); }
    public void translate(float x, float y) { translateX += x * scaleX; translateY += y * scaleY; }
    public void scale(float x, float y, float z) { scale(x, y); }
    public void scale(float x, float y) { scaleX *= x; scaleY *= y; }
}
