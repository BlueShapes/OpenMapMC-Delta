package net.minecraft.client.gui;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
public final class GuiGraphicsExtractor {
    public record Text(FormattedCharSequence sequence, int x, int y, int color, float dx, float dy) {}
    public final List<Text> texts = new ArrayList<>();
    private final Pose pose = new Pose();
    public Pose pose() { return pose; }
    public void fill(int x1, int y1, int x2, int y2, int color) {}
    public void text(Font font, String value, int x, int y, int color, boolean shadow) {
        text(font, Component.literal(value).getVisualOrderText(), x, y, color, shadow);
    }
    public void text(Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
        texts.add(new Text(value, x, y, color, pose.dx, pose.dy));
    }
    public static final class Pose {
        private final List<float[]> stack = new ArrayList<>();
        private float dx, dy;
        public void pushMatrix() { stack.add(new float[]{dx, dy}); }
        public void popMatrix() { float[] old = stack.remove(stack.size() - 1); dx = old[0]; dy = old[1]; }
        public void translate(float x, float y) { dx += x; dy += y; }
    }
}
