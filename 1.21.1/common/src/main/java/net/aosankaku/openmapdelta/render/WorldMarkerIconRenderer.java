// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** A selected baked item model or cropped flat icon on the world waypoint billboard. */
final class WorldMarkerIconRenderer {
    private WorldMarkerIconRenderer() {}

    static boolean draw(Matrix4f pose, MultiBufferSource buffer, String iconId, int color) {
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(iconId);
        if (entry == null) return false;
        ResourceLocation location;
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
        VertexConsumer vertices = buffer.getBuffer(RenderType.textSeeThrough(location));
        vertices.addVertex(pose, -8, 8, 0).setColor(tint).setUv(u0, v1).setLight(0xF000F0);
        vertices.addVertex(pose, 8, 8, 0).setColor(tint).setUv(u1, v1).setLight(0xF000F0);
        vertices.addVertex(pose, 8, -8, 0).setColor(tint).setUv(u1, v0).setLight(0xF000F0);
        vertices.addVertex(pose, -8, -8, 0).setColor(tint).setUv(u0, v0).setLight(0xF000F0);
        return true;
    }

    static void drawFace(Matrix4f pose, MultiBufferSource buffer, ResourceLocation texture,
                         float left, float top, float size, int alpha) {
        if (texture == null || alpha <= 0) return;
        VertexConsumer vertices = buffer.getBuffer(RenderType.textSeeThrough(texture));
        emitFace(vertices, pose, left, top, left + size, top + size, 8.0F / 64.0F,
                8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, alpha);
        emitFace(vertices, pose, left, top, left + size, top + size, 40.0F / 64.0F,
                8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F, alpha);
    }

    private static void emitFace(VertexConsumer vertices, Matrix4f pose, float left, float top,
                                 float right, float bottom, float u0, float v0, float u1, float v1, int alpha) {
        vertices.addVertex(pose, left, bottom, 0.015F).setColor(255, 255, 255, alpha).setUv(u0, v1).setLight(0xF000F0);
        vertices.addVertex(pose, right, bottom, 0.015F).setColor(255, 255, 255, alpha).setUv(u1, v1).setLight(0xF000F0);
        vertices.addVertex(pose, right, top, 0.015F).setColor(255, 255, 255, alpha).setUv(u1, v0).setLight(0xF000F0);
        vertices.addVertex(pose, left, top, 0.015F).setColor(255, 255, 255, alpha).setUv(u0, v0).setLight(0xF000F0);
    }
}
