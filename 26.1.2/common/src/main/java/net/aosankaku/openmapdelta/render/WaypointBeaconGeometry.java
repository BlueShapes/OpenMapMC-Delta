// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Native-like beacon geometry with both layers rendered translucent for local opacity fades. */
final class WaypointBeaconGeometry {
    private static final float SOLID_RADIUS = 0.15F;
    private static final float GLOW_RADIUS = 0.2F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private WaypointBeaconGeometry() {}

    static void render(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture,
                       float partialTick, long gameTime, int startY, int height, int argb, float opacity) {
        float time = (float) Math.floorMod(gameTime, 40L) + partialTick;
        float scroll = height < 0 ? time : -time;
        float vOffset = Mth.frac(scroll * 0.2F - Mth.floor(scroll * 0.1F));
        float boundedOpacity = Float.isFinite(opacity) ? Math.max(0.0F, Math.min(1.0F, opacity)) : 0.0F;
        int alpha = Math.round(boundedOpacity * 255.0F);
        int coreColor = alpha << 24 | (argb & 0x00FFFFFF);
        int glowColor = Math.round(alpha * 0.125F) << 24 | (argb & 0x00FFFFFF);
        int endY = startY + height;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.25F - 45.0F));
        float coreV = height * (0.5F / SOLID_RADIUS) + vOffset - 1.0F;
        float minV = -1.0F + vOffset;
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(texture, true),
                (pose, vertices) -> renderPart(pose, vertices, coreColor, startY, endY,
                        0.0F, SOLID_RADIUS, SOLID_RADIUS, 0.0F,
                        -SOLID_RADIUS, 0.0F, 0.0F, -SOLID_RADIUS,
                        0.0F, 1.0F, coreV, minV));
        poseStack.popPose();

        float glowV = height + minV;
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(texture, true),
                (pose, vertices) -> renderPart(pose, vertices, glowColor, startY, endY,
                        -GLOW_RADIUS, -GLOW_RADIUS, GLOW_RADIUS, -GLOW_RADIUS,
                        -GLOW_RADIUS, GLOW_RADIUS, GLOW_RADIUS, GLOW_RADIUS,
                        0.0F, 1.0F, glowV, minV));
        poseStack.popPose();
    }

    private static void renderPart(PoseStack.Pose pose, VertexConsumer vertices, int color,
                                   int minY, int maxY,
                                   float x0, float z0, float x1, float z1,
                                   float x2, float z2, float x3, float z3,
                                   float minU, float maxU, float topV, float bottomV) {
        renderQuad(pose, vertices, color, minY, maxY, x0, z0, x1, z1, minU, maxU, topV, bottomV);
        renderQuad(pose, vertices, color, minY, maxY, x3, z3, x2, z2, minU, maxU, topV, bottomV);
        renderQuad(pose, vertices, color, minY, maxY, x1, z1, x3, z3, minU, maxU, topV, bottomV);
        renderQuad(pose, vertices, color, minY, maxY, x2, z2, x0, z0, minU, maxU, topV, bottomV);
    }

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer vertices, int color,
                                   int minY, int maxY, float x0, float z0, float x1, float z1,
                                   float minU, float maxU, float topV, float bottomV) {
        addVertex(pose, vertices, color, maxY, x0, z0, maxU, topV);
        addVertex(pose, vertices, color, minY, x0, z0, maxU, bottomV);
        addVertex(pose, vertices, color, minY, x1, z1, minU, bottomV);
        addVertex(pose, vertices, color, maxY, x1, z1, minU, topV);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer vertices, int color,
                                  int y, float x, float z, float u, float v) {
        vertices.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
