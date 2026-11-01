// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Records the actual backdrop emitter without starting Minecraft or an OpenGL context. */
public final class WorldWaypointBackgroundTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var texture = ImageIO.read(Path.of(System.getProperty("waypoint.background.texture")).toFile());
        check(texture != null && texture.getWidth() == 1 && texture.getHeight() == 1
                && texture.getRGB(0, 0) == 0xFFFFFFFF, "backdrop texture is one opaque white texel");

        for (int color : new int[]{0x80000000, 0x40000000, 0x00000000, 0x7055FFFF, 0xFFFFFFFF}) {
            Recorder recorder = new Recorder();
            var buffer = new MultiBufferSource.BufferSource(recorder);
            WorldWaypointBackground.quad(buffer, new Matrix4f(), -20, -11, 20, -1, 0.01F, color);
            check(recorder.vertices.size() == 4, "one backdrop remains one quad");
            float[][] corners = {{-20, -1}, {20, -1}, {20, -11}, {-20, -11}};
            for (int i = 0; i < corners.length; i++) {
                Vertex vertex = recorder.vertices.get(i);
                check(vertex.x == corners[i][0] && vertex.y == corners[i][1] && vertex.z == 0.01F,
                        "backdrop bounds and depth retain the 1.20.1 geometry");
                check(vertex.color == color, "black alpha, fading, and halo/frame colors remain unchanged");
                checkVertex(vertex);
            }
            checkPipeline(buffer);
        }

        for (boolean emphasized : new boolean[]{false, true}) {
            Recorder recorder = new Recorder();
            var buffer = new MultiBufferSource.BufferSource(recorder);
            WorldWaypointBackground.badge(buffer, new Matrix4f(), 12, emphasized, 0x80FFFFFF);
            List<Vertex> expected = new ArrayList<>();
            WaypointClusterBadge.emit(12, emphasized, 0x80FFFFFF, (x, y, color) -> {
                Vertex vertex = new Vertex(x, y, 0.01F);
                vertex.color = color;
                expected.add(vertex);
            });
            check(recorder.vertices.size() == expected.size(), "cluster ring and disk vertex count retained");
            for (int i = 0; i < expected.size(); i++) {
                Vertex vertex = recorder.vertices.get(i);
                Vertex reference = expected.get(i);
                check(vertex.x == reference.x && vertex.y == reference.y && vertex.color == reference.color,
                        "cluster badge keeps its shared geometry, color, and opacity");
                checkVertex(vertex);
            }
            checkPipeline(buffer);
        }
        System.out.println("World waypoint background tests passed (" + checks + " checks).");
    }

    private static void checkPipeline(MultiBufferSource.BufferSource buffer) {
        check(buffer.requested.shaderPath().equals("textSeeThrough"),
                "background uses the working glyph shader instead of Iris's world background shader");
        check(buffer.requested.texture().namespace().equals("openmapdelta")
                        && buffer.requested.texture().path().equals("textures/misc/waypoint_background.png"),
                "shader samples the bundled white texture");
        WorldWaypointBackground.flush(buffer);
        check(buffer.flushed == buffer.requested && buffer.flushes == 1,
                "explicit flush submits the backdrop material before subsequent glyphs");
    }

    private static void checkVertex(Vertex vertex) {
        check(vertex.u == 0.5F && vertex.v == 0.5F, "every vertex samples the center of the white texel");
        check(vertex.light == 0xF000F0, "backdrop remains full-bright like the text");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Vertex {
        final float x, y, z;
        int color, light;
        float u = Float.NaN, v = Float.NaN;
        Vertex(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;
        public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
            current = new Vertex(x, y, z);
            vertices.add(current);
            return this;
        }
        public VertexConsumer setColor(int argb) { current.color = argb; return this; }
        public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; return this; }
        public VertexConsumer setLight(int light) { current.light = light; return this; }
    }
}
