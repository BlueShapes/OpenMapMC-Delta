package testsupport;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;

public final class RenderRecorder {
    public static final List<Blit> blits = new ArrayList<>();
    public static final List<Fill> fills = new ArrayList<>();
    public static final List<String> events = new ArrayList<>();
    private RenderRecorder() {}
    public static void clear() { blits.clear(); fills.clear(); events.clear(); }
    public record Fill(PoseStack.Matrix pose, int left, int top, int right, int bottom, int color) {}

    public static void add(PoseStack pose, Object texture, int x, int y, int width, int height,
                           float u, float v, int uvWidth, int uvHeight, int textureWidth,
                           int textureHeight, int color, Object pipeline) {
        blits.add(new Blit(pose.snapshot(), texture, x, y, width, height, u, v, uvWidth,
                uvHeight, textureWidth, textureHeight, color, pipeline, RenderSystem.getShaderColor()));
    }

    public static final class Blit {
        public final PoseStack.Matrix pose;
        public final Object texture;
        public final int x, y, width, height, uvWidth, uvHeight, textureWidth, textureHeight, color;
        public final float u, v;
        public final Object pipeline;
        public final float[] shaderColor;
        public Blit(PoseStack.Matrix pose, Object texture, int x, int y, int width, int height,
                    float u, float v, int uvWidth, int uvHeight, int textureWidth, int textureHeight,
                    int color, Object pipeline, float[] shaderColor) {
            this.pose = pose; this.texture = texture; this.x = x; this.y = y; this.width = width;
            this.height = height; this.u = u; this.v = v; this.uvWidth = uvWidth; this.uvHeight = uvHeight;
            this.textureWidth = textureWidth; this.textureHeight = textureHeight; this.color = color;
            this.pipeline = pipeline; this.shaderColor = shaderColor;
        }
    }
}
