// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Draw backdrops through the same see-through shader as waypoint glyphs and icons. */
final class WorldWaypointBackground {
    private static final int FULL_BRIGHT = 0xF000F0;
    // Iris can keep its world-only text-background shader active after the world pass.
    // A white texel preserves our colors while using the working text/icon shader path.
    private static final RenderType TYPE = RenderType.textSeeThrough(ResourceLocation.fromNamespaceAndPath(
            "openmapdelta", "textures/misc/waypoint_background.png"));

    private WorldWaypointBackground() {}

    static void quad(MultiBufferSource buffer, Matrix4f pose, float left, float top,
                     float right, float bottom, float z, int color) {
        VertexConsumer vertices = buffer.getBuffer(TYPE);
        vertex(vertices, pose, left, bottom, z, color);
        vertex(vertices, pose, right, bottom, z, color);
        vertex(vertices, pose, right, top, z, color);
        vertex(vertices, pose, left, top, z, color);
    }

    static void badge(MultiBufferSource buffer, Matrix4f pose, float radius,
                      boolean emphasized, int alphaSource) {
        VertexConsumer vertices = buffer.getBuffer(TYPE);
        WaypointClusterBadge.emit(radius, emphasized, alphaSource, (x, y, color) ->
                vertex(vertices, pose, x, y, 0.01F, color));
    }

    static void flush(MultiBufferSource.BufferSource buffer) {
        buffer.endBatch(TYPE);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, float x, float y, float z, int color) {
        vertices.addVertex(pose, x, y, z).setColor(color).setUv(0.5F, 0.5F).setLight(FULL_BRIGHT);
    }
}
