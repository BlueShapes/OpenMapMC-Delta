// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerHoverStyle;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WorldWaypointRenderer {
    private static final int MAX_RENDERED_WAYPOINTS = 64;
    private static final int TEXT_BACKGROUND = 0x80000000;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float LABEL_GAP_PIXELS = 2.0F;
    private static final ResourceLocation BEAM_TEXTURE = new ResourceLocation("textures/entity/beacon_beam.png");
    private static final MultiBufferSource.BufferSource WAYPOINT_TEXT_BUFFER =
            MultiBufferSource.immediate(new BufferBuilder(256));
    private static final List<PendingWaypointText> PENDING_WAYPOINT_TEXTS = new ArrayList<>();
    private static Level pendingSourceLevel;
    private static Matrix4f pendingModelView;

    public static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        return OpenMapDeltaConfig.MAP_MOD_ENABLED.get() && !mc.options.hideGui
                && (OpenMapDeltaConfig.SHOW_WAYPOINTS.get() || OpenMapDeltaConfig.SHOW_SHARED_WAYPOINTS.get()
                || OpenMapDeltaConfig.SHOW_BEDS.get() || OpenMapDeltaConfig.SHOW_DEATH.get()
                || MarkerManager.get().hasLocationNotices() || MarkerManager.get().hasActiveNavigationPoints());
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                              Font font, Camera camera, Level level, Vec3 camPos,
                              float partialTick, long gameTime) {
        PENDING_WAYPOINT_TEXTS.clear();
        pendingSourceLevel = null;
        pendingModelView = null;
        if (!shouldRender()) return;
        List<Marker> waypoints = MarkerManager.get().getRenderableWaypointsInDimension(level.dimension());
        if (waypoints.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        pendingSourceLevel = level;
        // The later overlay hook runs after the renderer restores this world's camera view.
        pendingModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        int projectionDistance = WaypointRenderMath.projectionDistance(mc.options.renderDistance().get());
        List<WaypointRenderMath.ViewCandidate<WaypointCandidate>> candidates = new ArrayList<>(waypoints.size());
        Vec3 playerFeet = mc.player.getPosition(partialTick);
        double hideDistance = OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get();
        double labelFadeDistance = OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get();
        double nameCutoff = OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get();
        for (Marker marker : waypoints) {
            if (!marker.getType().isWorldWaypoint()) continue;

            var death = marker.getDeathPoint();
            var motion = death != null && death.isSmart() ? death.motion(marker.getX(), marker.getY(), marker.getZ()) : null;
            double wx = motion == null ? marker.getX() + 0.5 : motion.renderX(partialTick);
            double wz = motion == null ? marker.getZ() + 0.5 : motion.renderZ(partialTick);
            double wy = motion == null ? resolveY(level, marker, playerFeet.y) : motion.renderY(partialTick);
            double dx = wx - camPos.x;
            double dy = wy - camPos.y;
            double dz = wz - camPos.z;
            double playerDx = wx - playerFeet.x;
            double playerDy = wy - playerFeet.y;
            double playerDz = wz - playerFeet.z;
            double playerDistance = Math.hypot(Math.hypot(playerDx, playerDy), playerDz);
            boolean nearName = WaypointClustering.isWithinNameRange(playerDistance, nameCutoff);
            WaypointRenderMath.Opacities opacities = WaypointRenderMath.proximityOpacities(
                    playerDx, playerDy, playerDz, hideDistance, labelFadeDistance);
            if (!opacities.visible()) continue;
            String label = marker.getDisplayLabel() + " (" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m)";
            WaypointCandidate waypoint = new WaypointCandidate(marker, opacities, label, nearName, playerDistance);
            candidates.add(WaypointRenderMath.projectCandidate(waypoint, dx, dy, dz, projectionDistance));
        }
        double beamBottomDy = level.getMinBuildHeight() - 64.0 - camPos.y;
        double beamTopDy = level.getMaxBuildHeight() - camPos.y;
        List<WaypointRenderMath.ViewCandidate<WaypointCandidate>> selected = new ArrayList<>(
                WaypointRenderMath.prioritizeBeamCandidates(candidates, camera.getYRot(), camera.getXRot(),
                        Integer.MAX_VALUE, beamBottomDy, beamTopDy));
        List<WaypointClustering.Entry<WaypointCandidate>> entries = new ArrayList<>(selected.size());
        for (WaypointRenderMath.ViewCandidate<WaypointCandidate> view : selected) {
            WaypointCandidate waypoint = view.value();
            boolean centerInFront = WaypointRenderMath.isInFront(view.dx(), view.dy(), view.dz(),
                    camera.getYRot(), camera.getXRot());
            boolean labelVisible = MarkerManager.get().isNavigating(waypoint.marker().getId())
                    || waypoint.marker().getType() == MarkerType.LOCATION_NOTICE
                    || WaypointRenderMath.isLookingAt(view.dx(), view.dy(), view.dz(), camera.getYRot(),
                    camera.getXRot(), WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES);
            boolean customIcon = net.aosankaku.openmapdelta.marker.MarkerIconCatalog.find(waypoint.marker().getIconId()) != null;
            double labelAnchorOffset = customIcon ? 8.0 + font.lineHeight + LABEL_GAP_PIXELS - 4.0
                    : 1.5 * font.lineHeight + LABEL_GAP_PIXELS - 4.0;
            boolean textVisible = ((WaypointRenderMath.scaleAlpha(0xFFFFFFFF, waypoint.opacities().label()) >>> 24)
                    & 0xFF) >= 4;
            entries.add(new WaypointClustering.Entry<>(view,
                    font.width(Component.literal(waypoint.label()).getVisualOrderText())
                            + (waypoint.marker().getOwnerId() == null ? 0 : 10), waypoint.nearName() || waypoint.marker().getType() == MarkerType.DEATH,
                    labelVisible, centerInFront && textVisible, labelAnchorOffset, textVisible));
        }
        entries.sort(Comparator.comparingInt(WorldWaypointRenderer::renderPriority));
        WaypointClusterOrder.Order labelOrder = OpenMapDeltaConfig.WORLD_WAYPOINT_CLUSTER_LABEL_ORDER.get();
        List<WaypointClustering.LayoutStack<WaypointCandidate>> clusters = WaypointClustering.layout(
                entries, camera.getYRot(), camera.getXRot(), projectionDistance, MAX_RENDERED_WAYPOINTS,
                font.lineHeight, LABEL_GAP_PIXELS,
                count -> font.width(Component.literal(Integer.toString(count)).getVisualOrderText()),
                remaining -> font.width(Component.translatable("odysseymap.waypoint.cluster_remaining", remaining)
                        .getVisualOrderText()),
                named -> WaypointClusterLabels.select(WaypointClusterOrder.sorted(named, labelOrder,
                                entry -> entry.value().playerDistance(),
                                entry -> entry.value().marker().getLabel(),
                                entry -> entry.value().marker().getId()),
                        entry -> isEmphasized(entry.value().marker())));
        for (WaypointClustering.LayoutStack<WaypointCandidate> cluster : clusters) {
            WaypointRenderMath.ViewCandidate<WaypointCandidate> candidate = cluster.anchor();
            Marker marker = cluster.representative().value().marker();
            float beamOpacity = 0.0F;
            float iconOpacity = 0.0F;
            boolean containsPulsingMarker = false;
            for (WaypointClustering.Entry<WaypointCandidate> member : cluster.members()) {
                beamOpacity = Math.max(beamOpacity, member.value().opacities().beam());
                iconOpacity = Math.max(iconOpacity, member.value().opacities().label());
                MarkerType type = member.value().marker().getType();
                containsPulsingMarker |= type == MarkerType.LOCATION_NOTICE || type == MarkerType.TEMPORARY_WAYPOINT
                        || type == MarkerType.NAVIGATION_POINT;
            }
            boolean centerInFront = WaypointRenderMath.isInFront(candidate.dx(), candidate.dy(), candidate.dz(),
                    camera.getYRot(), camera.getXRot());
            boolean beamInFront = WaypointRenderMath.isBeamInFront(candidate.dx(), candidate.dz(),
                    beamBottomDy, beamTopDy, camera.getYRot(), camera.getXRot());
            boolean emphasized = MarkerManager.get().isNavigating(marker.getId())
                    || marker.getType() == MarkerType.LOCATION_NOTICE;
            double displayDistance = WaypointRenderMath.projectedDistance(
                    candidate.distance(), projectionDistance);

            poseStack.pushPose();
            poseStack.translate(candidate.dx(), candidate.dy(), candidate.dz());

            if (beamOpacity > 0.0F && beamInFront) {
                renderBeam(poseStack, buffer, partialTick, gameTime, level,
                        camPos.y + candidate.dy() - WaypointRenderMath.ICON_HEIGHT,
                        cluster.beamColor(marker.getColor()), beamOpacity, containsPulsingMarker,
                        marker.getType() == MarkerType.DEATH);
            }

            float billboardScale = (float) WaypointRenderMath.billboardScale(displayDistance);
            List<PendingWaypointLabel> labels = new ArrayList<>();
            if (centerInFront) {
                List<WaypointClustering.Entry<WaypointCandidate>> named = cluster.labelSelection().names();
                for (int row = 0; row < named.size(); row++) {
                    WaypointCandidate member = named.get(row).value();
                    float textOpacity = member.opacities().label();
                    if (textOpacity <= 0.0F) continue;
                    boolean rowEmphasized = MarkerManager.get().isNavigating(member.marker().getId())
                            || member.marker().getType() == MarkerType.LOCATION_NOTICE;
                    labels.add(new PendingWaypointLabel(member.label(),
                            WaypointRenderMath.scaleAlpha(0xFFFFFFFF, textOpacity),
                            WaypointRenderMath.scaleAlpha(0xFF000000, textOpacity),
                            WaypointRenderMath.scaleAlpha(TEXT_BACKGROUND, textOpacity), rowEmphasized,
                            member.marker()));
                }
                int remaining = cluster.labelSelection().remaining();
                if (remaining > 0) {
                    float textOpacity = (float) cluster.namedEntries().stream()
                            .mapToDouble(entry -> entry.value().opacities().label()).max().orElse(0.0);
                    labels.add(new PendingWaypointLabel(
                            Component.translatable("odysseymap.waypoint.cluster_remaining", remaining).getString(),
                            WaypointRenderMath.scaleAlpha(0xFFFFFFFF, textOpacity),
                            WaypointRenderMath.scaleAlpha(0xFF000000, textOpacity),
                            WaypointRenderMath.scaleAlpha(TEXT_BACKGROUND, textOpacity), false, null));
                }
            }
            if (centerInFront && (iconOpacity > 0.0F || !labels.isEmpty())) queueWaypointText(poseStack, camera,
                    marker.getColor(), iconOpacity, billboardScale, marker.getIconId(), emphasized,
                    cluster.aggregated() ? cluster.count() : 1, (float) cluster.badgeRadius(),
                    (float) cluster.verticalOffset(), labels);
            poseStack.popPose();
        }
    }

    public static double resolveY(Level level, Marker marker) {
        return resolveY(level, marker, level.getSeaLevel());
    }

    private static int renderPriority(WaypointClustering.Entry<WaypointCandidate> candidate) {
        Marker marker = candidate.value().marker();
        if (MarkerManager.get().isNavigating(marker.getId())) return 0;
        if (marker.getType() == MarkerType.LOCATION_NOTICE || marker.getType() == MarkerType.DEATH) return 1;
        return candidate.near() ? 2 : 3;
    }

    private static boolean isEmphasized(Marker marker) {
        return MarkerManager.get().isNavigating(marker.getId()) || marker.getType() == MarkerType.LOCATION_NOTICE;
    }

    private static double resolveY(Level level, Marker marker, double unloadedFallbackY) {
        if (marker.hasKnownY()) return marker.getY();
        // Height queries against an unloaded client chunk can synchronously request terrain.
        if (!level.hasChunkAt(marker.getX(), marker.getZ())) return unloadedFallbackY;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, marker.getX(), marker.getZ());
    }

    private static void renderBeam(PoseStack poseStack, MultiBufferSource buffer, float partialTick,
                                   long gameTime, Level level, double waypointY, int argb, float opacity,
                                   boolean notify, boolean death) {
        int baseY = (int) Math.floor(waypointY);
        int start = WaypointRenderMath.beamStartOffset(level.getMinBuildHeight(), baseY);
        int height = WaypointRenderMath.beamHeight(level.getMinBuildHeight(), level.getMaxBuildHeight());
        float[] color = new float[] {((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F};
        poseStack.pushPose();
        poseStack.translate(-0.5, -WaypointRenderMath.ICON_HEIGHT, -0.5);
        if (notify || death || opacity < 1.0F) {
            float pulse = death ? WaypointRenderMath.deathBeaconPulse(gameTime, partialTick,
                    OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.get(), OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.get())
                    : notify ? WaypointRenderMath.notifyBeaconPulse(gameTime, partialTick) : 1.0F;
            WaypointBeaconGeometry.render(poseStack, buffer, BEAM_TEXTURE, partialTick, gameTime,
                    start, height, argb, WaypointRenderMath.beaconOpacity(opacity, pulse));
        } else {
            BeaconRenderer.renderBeaconBeam(poseStack, buffer, BEAM_TEXTURE, partialTick,
                    1.0F, gameTime, start, height, color, 0.15F, 0.2F);
        }
        poseStack.popPose();
    }

    private static void queueWaypointText(PoseStack poseStack, Camera camera,
                                          int color, float opacity, float scale, String iconId,
                                          boolean emphasized, int count, float badgeRadius,
                                          float verticalOffset, List<PendingWaypointLabel> labels) {
        renderBillboard(poseStack, camera, scale);
        try {
            PENDING_WAYPOINT_TEXTS.add(new PendingWaypointText(
                    new Matrix4f(poseStack.last().pose()),
                    WaypointRenderMath.scaleAlpha(color, opacity),
                    WaypointRenderMath.scaleAlpha(0xFFFFFFFF, opacity),
                    WaypointRenderMath.scaleAlpha(TEXT_BACKGROUND, opacity), iconId, color, emphasized,
                    count, badgeRadius, verticalOffset, List.copyOf(labels)));
        } finally {
            finishBillboard(poseStack);
        }
    }

    public static void renderLabels(Font font) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (pendingSourceLevel == null || pendingSourceLevel != mc.level
                    || pendingModelView == null || PENDING_WAYPOINT_TEXTS.isEmpty()
                    || mc.player == null || !shouldRender()) {
                return;
            }

            float[] shaderColor = RenderSystem.getShaderColor().clone();
            boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            try {
                RenderSystem.disableDepthTest();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                PoseStack modelViewStack = RenderSystem.getModelViewStack();
                modelViewStack.pushPose();
                try {
                    modelViewStack.last().pose().set(pendingModelView);
                    RenderSystem.applyModelViewMatrix();
                    // Selection order starts with preferred candidates; reverse it so they finish on top.
                    for (int i = PENDING_WAYPOINT_TEXTS.size() - 1; i >= 0; i--) {
                        drawWaypointText(font, PENDING_WAYPOINT_TEXTS.get(i));
                    }
                } finally {
                    try {
                        modelViewStack.popPose();
                    } finally {
                        RenderSystem.applyModelViewMatrix();
                    }
                }
            } finally {
                try {
                    RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
                } finally {
                    if (depthTestEnabled) {
                        RenderSystem.enableDepthTest();
                    } else {
                        RenderSystem.disableDepthTest();
                    }
                }
            }
        } finally {
            PENDING_WAYPOINT_TEXTS.clear();
            pendingSourceLevel = null;
            pendingModelView = null;
        }
    }

    private static void drawWaypointText(Font font, PendingWaypointText waypointText) {
        FormattedCharSequence icon = Component.literal("◆").getVisualOrderText();
        float x = -font.width(icon) / 2.0F;
        boolean clustered = waypointText.count() > 1;
        FormattedCharSequence countText = clustered
                ? Component.literal(Integer.toString(waypointText.count())).getVisualOrderText() : null;
        float badgeRadius = clustered ? WaypointClusterBadge.radius(font.width(countText), font.lineHeight) : 0.0F;
        boolean customIcon = net.aosankaku.openmapdelta.marker.MarkerIconCatalog.find(waypointText.iconId()) != null;
        float iconY = clustered || !customIcon ? -font.lineHeight / 2.0F : -8.0F;
        Matrix4f pose = waypointText.pose();
        if (clustered && ((waypointText.color() >>> 24) & 0xFF) >= 4) {
            drawClusterBadge(pose, badgeRadius, waypointText.emphasized(), waypointText.color());
        } else if (waypointText.emphasized() && ((waypointText.color() >>> 24) & 0xFF) >= 4) {
            drawEmphasisHalo(pose, waypointText.markerColor(), waypointText.color());
            if (customIcon) drawCustomIconFrame(pose, waypointText.color());
        } else if (!clustered && !customIcon && ((waypointText.color() >>> 24) & 0xFF) >= 4) {
            drawTextBackground(pose, font, icon, x, iconY, waypointText.backgroundColor());
        }

        List<FormattedWaypointLabel> visibleLabels = new ArrayList<>(waypointText.labels().size());
        for (int row = 0; row < waypointText.labels().size(); row++) {
            PendingWaypointLabel label = waypointText.labels().get(row);
            if (((label.textColor() >>> 24) & 0xFF) < 4) continue;
            FormattedCharSequence sequence = Component.literal(label.text()).getVisualOrderText();
            float prefixWidth = label.marker() == null || label.marker().getOwnerId() == null ? 0.0F : 10.0F;
            float textWidth = font.width(sequence);
            float faceX = -(textWidth + prefixWidth) / 2.0F;
            float labelX = faceX + prefixWidth;
            float labelY = clustered
                    ? -badgeRadius - LABEL_GAP_PIXELS - font.lineHeight - row * (font.lineHeight + LABEL_GAP_PIXELS)
                    : iconY - font.lineHeight - LABEL_GAP_PIXELS;
            labelY -= waypointText.verticalOffset();
            if (!label.emphasized()) drawTextBackground(pose, font, faceX, labelY,
                    textWidth + prefixWidth, label.backgroundColor());
            visibleLabels.add(new FormattedWaypointLabel(sequence, labelX, labelY, faceX, label));
        }
        // Sorted font backdrops can cover glyphs, so submit this waypoint's backdrop first.
        WAYPOINT_TEXT_BUFFER.endBatch(RenderType.textBackgroundSeeThrough());
        for (FormattedWaypointLabel label : visibleLabels) {
            Marker owner = label.style().marker();
            if (owner == null) continue;
            if (owner.getOwnerId() == null) continue;
            ResourceLocation face = WaypointOwnerFace.texture(owner.getOwnerId(), owner.getOwnerName());
            WorldMarkerIconRenderer.drawFace(pose, WAYPOINT_TEXT_BUFFER, face, label.faceX() + 1.0F,
                    label.y() + 0.5F, 8.0F, (label.style().textColor() >>> 24) & 0xFF);
        }

        if (clustered) {
            if (((waypointText.textColor() >>> 24) & 0xFF) >= 4) {
                font.drawInBatch(countText, -font.width(countText) / 2.0F, -font.lineHeight / 2.0F,
                        waypointText.textColor(), false, pose, WAYPOINT_TEXT_BUFFER,
                        Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
            }
        } else if (((waypointText.color() >>> 24) & 0xFF) >= 4 && (!customIcon
                || !WorldMarkerIconRenderer.draw(pose, WAYPOINT_TEXT_BUFFER, waypointText.iconId(), waypointText.textColor()))) {
            if (waypointText.emphasized()) {
                drawOutlinedText(font, icon, x, iconY, waypointText.color(),
                        WaypointRenderMath.scaleAlpha(MarkerHoverStyle.outlineColor(waypointText.markerColor()),
                                (waypointText.color() >>> 24) / 255.0F), pose);
            } else {
                font.drawInBatch(icon, x, iconY, waypointText.color(), false, pose, WAYPOINT_TEXT_BUFFER,
                        Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
            }
        }
        for (FormattedWaypointLabel label : visibleLabels) {
            if (label.style().emphasized()) {
                drawOutlinedText(font, label.sequence(), label.x(), label.y(), label.style().textColor(),
                        label.style().outlineColor(), pose);
            } else {
                font.drawInBatch(label.sequence(), label.x(), label.y(), label.style().textColor(), false, pose, WAYPOINT_TEXT_BUFFER,
                        Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
            }
        }
        WAYPOINT_TEXT_BUFFER.endBatch();
    }

    private static void drawClusterBadge(Matrix4f pose, float radius, boolean emphasized, int alphaSource) {
        VertexConsumer vertices = WAYPOINT_TEXT_BUFFER.getBuffer(RenderType.textBackgroundSeeThrough());
        WaypointClusterBadge.emit(radius, emphasized, alphaSource, (x, y, color) ->
                vertices.vertex(pose, x, y, 0.01F)
                        .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF,
                                (color >>> 24) & 0xFF)
                        .uv2(FULL_BRIGHT).endVertex());
    }

    private static void drawTextBackground(Matrix4f pose, Font font, FormattedCharSequence text,
                                           float x, float y, int backgroundColor) {
        drawTextBackground(pose, font, x, y, font.width(text), backgroundColor);
    }

    private static void drawTextBackground(Matrix4f pose, Font font, float x, float y,
                                           float width, int backgroundColor) {
        float left = x - 1.0F;
        float top = y - 1.0F;
        float right = x + width + 1.0F;
        float bottom = y + 9.0F;
        int red = (backgroundColor >> 16) & 0xFF;
        int green = (backgroundColor >> 8) & 0xFF;
        int blue = backgroundColor & 0xFF;
        int alpha = (backgroundColor >>> 24) & 0xFF;
        VertexConsumer vertices = WAYPOINT_TEXT_BUFFER.getBuffer(RenderType.textBackgroundSeeThrough());
        vertices.vertex(pose, left, bottom, 0.01F).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
        vertices.vertex(pose, right, bottom, 0.01F).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
        vertices.vertex(pose, right, top, 0.01F).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
        vertices.vertex(pose, left, top, 0.01F).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
    }

    private static void drawOutlinedText(Font font, FormattedCharSequence text, float x, float y,
                                         int color, int outlineColor, Matrix4f pose) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                font.drawInBatch(text, x + dx, y + dy, outlineColor, false, pose, WAYPOINT_TEXT_BUFFER,
                        Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
            }
        }
        // See-through text atlases sort transparent glyph quads on upload; isolate the outline batch
        // so its black copies cannot be sorted over the foreground glyph.
        WAYPOINT_TEXT_BUFFER.endBatch();
        font.drawInBatch(text, x, y, color, false, pose, WAYPOINT_TEXT_BUFFER,
                Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
    }

    private static void drawEmphasisHalo(Matrix4f pose, int markerColor, int foregroundColor) {
        int haloColor = (markerColor & 0x00FFFFFF) | (Math.min(0x70, (foregroundColor >>> 24) / 2) << 24);
        drawColoredQuad(pose, -10.5F, -10.5F, 10.5F, 10.5F, 0.02F, haloColor);
    }

    private static void drawCustomIconFrame(Matrix4f pose, int blackAlphaSource) {
        float opacity = ((blackAlphaSource >>> 24) & 0xFF) / 255.0F;
        int black = WaypointRenderMath.scaleAlpha(0xFF000000, opacity);
        int white = WaypointRenderMath.scaleAlpha(0xFFFFFFFF, opacity);
        drawSquareFrame(pose, 10.0F, 1.0F, black);
        drawSquareFrame(pose, 8.75F, 0.75F, white);
    }

    private static void drawSquareFrame(Matrix4f pose, float halfSize, float width, int color) {
        float inner = halfSize - width;
        drawColoredQuad(pose, -halfSize, -halfSize, halfSize, -inner, 0.01F, color);
        drawColoredQuad(pose, -halfSize, inner, halfSize, halfSize, 0.01F, color);
        drawColoredQuad(pose, -halfSize, -inner, -inner, inner, 0.01F, color);
        drawColoredQuad(pose, inner, -inner, halfSize, inner, 0.01F, color);
    }

    private static void drawColoredQuad(Matrix4f pose, float left, float top, float right, float bottom,
                                        float z, int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        VertexConsumer vertices = WAYPOINT_TEXT_BUFFER.getBuffer(RenderType.textBackgroundSeeThrough());
        vertices.vertex(pose, left, bottom, z).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
        vertices.vertex(pose, right, bottom, z).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
        vertices.vertex(pose, right, top, z).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
        vertices.vertex(pose, left, top, z).color(red, green, blue, alpha).uv2(FULL_BRIGHT).endVertex();
    }

    private static void renderBillboard(PoseStack poseStack, Camera camera, float scale) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.scale(-scale, -scale, scale);
    }

    private static void finishBillboard(PoseStack poseStack) {
        poseStack.popPose();
    }

    private record WaypointCandidate(Marker marker, WaypointRenderMath.Opacities opacities,
                                     String label, boolean nearName, double playerDistance) {
    }

    private record PendingWaypointText(Matrix4f pose, int color, int textColor,
                                       int backgroundColor, String iconId, int markerColor,
                                       boolean emphasized, int count, float badgeRadius,
                                       float verticalOffset, List<PendingWaypointLabel> labels) {
    }

    private record PendingWaypointLabel(String text, int textColor, int outlineColor,
                                        int backgroundColor, boolean emphasized, Marker marker) {
    }

    private record FormattedWaypointLabel(FormattedCharSequence sequence, float x, float y, float faceX,
                                          PendingWaypointLabel style) {
    }

}
