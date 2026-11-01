// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** A selected baked item model or cropped flat icon on the world waypoint billboard. */
final class WorldMarkerIconRenderer {
    private WorldMarkerIconRenderer() {}

    static boolean submit(PoseStack poses, SubmitNodeCollector collector, String iconId, int color) {
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(iconId);
        if (entry == null) return false;
        Identifier location;
        float u0 = 0.0F, v0 = 0.0F, u1 = 1.0F, v1 = 1.0F;
        int tint = 0x00FFFFFF | (color & 0xFF000000);
        if (!entry.item().isEmpty()) {
            location = MarkerIconSpriteCache.get(Minecraft.getInstance(), entry);
            if (location == null) return false;
            tint = 0x00FFFFFF | (color & 0xFF000000);
        } else {
            MarkerIconCatalog.Texture texture = MarkerIconCatalog.resolveTexture(entry);
            if (texture == null) return false;
            location = texture.location();
            u0 = texture.u() / (float) texture.textureWidth();
            v0 = texture.v() / (float) texture.textureHeight();
            u1 = (texture.u() + texture.width()) / (float) texture.textureWidth();
            v1 = (texture.v() + texture.height()) / (float) texture.textureHeight();
        }
        int finalTint = tint;
        float finalU0 = u0, finalV0 = v0, finalU1 = u1, finalV1 = v1;
        collector.submitCustomGeometry(poses, RenderTypes.textSeeThrough(location), (pose, vertices) -> {
            vertices.addVertex(pose, -8, 8, 0).setColor(finalTint).setUv(finalU0, finalV1).setLight(0xF000F0);
            vertices.addVertex(pose, 8, 8, 0).setColor(finalTint).setUv(finalU1, finalV1).setLight(0xF000F0);
            vertices.addVertex(pose, 8, -8, 0).setColor(finalTint).setUv(finalU1, finalV0).setLight(0xF000F0);
            vertices.addVertex(pose, -8, -8, 0).setColor(finalTint).setUv(finalU0, finalV0).setLight(0xF000F0);
        });
        return true;
    }

    static void submitFace(PoseStack poses, SubmitNodeCollector collector, Identifier texture,
                           float left, float top, float size, int alpha) {
        if (texture == null || alpha <= 0) return;
        collector.order(WorldWaypointRenderer.FOREGROUND_TEXT_ORDER).submitCustomGeometry(
                poses, RenderTypes.textSeeThrough(texture), (pose, vertices) -> {
                    float right = left + size, bottom = top + size;
                    emitFace(vertices, pose.pose(), left, top, right, bottom, 8.0F / 64.0F,
                            8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, alpha);
                    emitFace(vertices, pose.pose(), left, top, right, bottom, 40.0F / 64.0F,
                            8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F, alpha);
                });
    }

    private static void emitFace(com.mojang.blaze3d.vertex.VertexConsumer vertices, org.joml.Matrix4f pose,
                                 float left, float top, float right, float bottom,
                                 float u0, float v0, float u1, float v1, int alpha) {
        vertices.addVertex(pose, left, bottom, 0.015F).setColor(255, 255, 255, alpha).setUv(u0, v1).setLight(0xF000F0);
        vertices.addVertex(pose, right, bottom, 0.015F).setColor(255, 255, 255, alpha).setUv(u1, v1).setLight(0xF000F0);
        vertices.addVertex(pose, right, top, 0.015F).setColor(255, 255, 255, alpha).setUv(u1, v0).setLight(0xF000F0);
        vertices.addVertex(pose, left, top, 0.015F).setColor(255, 255, 255, alpha).setUv(u0, v0).setLight(0xF000F0);
    }
}
