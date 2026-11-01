// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerHoverStyle;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WorldWaypointRenderer {
    private static final int MAX_RENDERED_WAYPOINTS = 64;
    private static final int TEXT_BACKGROUND = 0x80000000;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float LABEL_GAP_PIXELS = 2.0F;
    private static final int OUTLINE_TEXT_ORDER = 100;
    static final int FOREGROUND_TEXT_ORDER = 101;

    public static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        return OpenMapDeltaConfig.MAP_MOD_ENABLED.get() && !mc.gui.hud.isHidden()
                && (OpenMapDeltaConfig.SHOW_WAYPOINTS.get() || OpenMapDeltaConfig.SHOW_SHARED_WAYPOINTS.get()
                || OpenMapDeltaConfig.SHOW_BEDS.get()
                || OpenMapDeltaConfig.SHOW_DEATH.get()
                || MarkerManager.get().hasLocationNotices() || MarkerManager.get().hasActiveNavigationPoints());
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector submitCollector,
                              Font font, Camera camera, Level level, Vec3 camPos,
                              float partialTick, long gameTime) {
        if (!shouldRender()) return;
        List<Marker> waypoints = MarkerManager.get().getRenderableWaypointsInDimension(level.dimension());
        if (waypoints.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

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
        double beamBottomDy = level.getMinY() - 64.0 - camPos.y;
        double beamTopDy = level.getMaxY() - camPos.y;
        List<WaypointRenderMath.ViewCandidate<WaypointCandidate>> selected = new ArrayList<>(
                WaypointRenderMath.prioritizeBeamCandidates(candidates, camera.yRot(), camera.xRot(),
                        Integer.MAX_VALUE, beamBottomDy, beamTopDy));
        List<WaypointClustering.Entry<WaypointCandidate>> entries = new ArrayList<>(selected.size());
        for (WaypointRenderMath.ViewCandidate<WaypointCandidate> view : selected) {
            WaypointCandidate waypoint = view.value();
            boolean centerInFront = WaypointRenderMath.isInFront(view.dx(), view.dy(), view.dz(),
                    camera.yRot(), camera.xRot());
            boolean labelVisible = MarkerManager.get().isNavigating(waypoint.marker().getId())
                    || waypoint.marker().getType() == MarkerType.LOCATION_NOTICE
                    || WaypointRenderMath.isLookingAt(view.dx(), view.dy(), view.dz(), camera.yRot(),
                    camera.xRot(), WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES);
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
                entries, camera.yRot(), camera.xRot(), projectionDistance, MAX_RENDERED_WAYPOINTS,
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
                    camera.yRot(), camera.xRot());
            boolean beamInFront = WaypointRenderMath.isBeamInFront(candidate.dx(), candidate.dz(),
                    beamBottomDy, beamTopDy, camera.yRot(), camera.xRot());
            boolean emphasized = MarkerManager.get().isNavigating(marker.getId())
                    || marker.getType() == MarkerType.LOCATION_NOTICE;
            double displayDistance = WaypointRenderMath.projectedDistance(
                    candidate.distance(), projectionDistance);

            poseStack.pushPose();
            poseStack.translate(candidate.dx(), candidate.dy(), candidate.dz());

            if (beamOpacity > 0.0F && beamInFront) {
                renderBeam(poseStack, submitCollector, partialTick, gameTime, level,
                        camPos.y + candidate.dy() - WaypointRenderMath.ICON_HEIGHT,
                        cluster.beamColor(marker.getColor()), beamOpacity, containsPulsingMarker, marker.getType() == MarkerType.DEATH);
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
                    labels.add(new PendingWaypointLabel(member.label(), textOpacity, rowEmphasized,
                            member.marker()));
                }
                int remaining = cluster.labelSelection().remaining();
                if (remaining > 0) {
                    float textOpacity = (float) cluster.namedEntries().stream()
                            .mapToDouble(entry -> entry.value().opacities().label()).max().orElse(0.0);
                    labels.add(new PendingWaypointLabel(
                            Component.translatable("odysseymap.waypoint.cluster_remaining", remaining).getString(),
                            textOpacity, false, null));
                }
            }
            if (centerInFront && (iconOpacity > 0.0F || !labels.isEmpty())) renderWaypoint(poseStack, submitCollector,
                    font, camera, marker.getColor(), iconOpacity, billboardScale, marker.getIconId(), emphasized,
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
        if (marker.getType() == MarkerType.LOCATION_NOTICE) return 1;
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

    private static void renderBeam(PoseStack poseStack, SubmitNodeCollector submitCollector,
                                   float partialTick, long gameTime, Level level, double waypointY,
                                   int color, float opacity,
                                   boolean notify, boolean death) {
        int baseY = (int) Math.floor(waypointY);
        int start = WaypointRenderMath.beamStartOffset(level.getMinY(), baseY);
        int height = WaypointRenderMath.beamHeight(level.getMinY(), level.getMaxY());
        poseStack.pushPose();
        poseStack.translate(-0.5, -WaypointRenderMath.ICON_HEIGHT, -0.5);
        if (notify || death || opacity < 1.0F) {
            float pulse = death ? WaypointRenderMath.deathBeaconPulse(gameTime, partialTick,
                    OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.get(), OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.get())
                    : notify ? WaypointRenderMath.notifyBeaconPulse(gameTime, partialTick) : 1.0F;
            WaypointBeaconGeometry.render(poseStack, submitCollector, BeaconRenderer.BEAM_LOCATION,
                    partialTick, gameTime, start, height, color,
                    WaypointRenderMath.beaconOpacity(opacity, pulse));
        } else {
            BeaconRenderer.submitBeaconBeam(poseStack, submitCollector, BeaconRenderer.BEAM_LOCATION, 1.0F,
                    gameTime, start, height, color, 0.15F, 0.2F);
        }
        poseStack.popPose();
    }

    private static void renderWaypoint(PoseStack poseStack, SubmitNodeCollector submitCollector,
                                       Font font, Camera camera, int color, float opacity,
                                       float scale, String iconId, boolean emphasized, int count,
                                       float badgeRadius, float verticalOffset,
                                       List<PendingWaypointLabel> labels) {
        renderBillboard(poseStack, camera, scale);
        FormattedCharSequence icon = Component.literal("◆").getVisualOrderText();
        float x = -font.width(icon) / 2.0F;
        boolean clustered = count > 1;
        FormattedCharSequence countText = clustered
                ? Component.literal(Integer.toString(count)).getVisualOrderText() : null;
        boolean customIcon = net.aosankaku.openmapdelta.marker.MarkerIconCatalog.find(iconId) != null;
        float iconY = clustered || !customIcon ? -font.lineHeight / 2.0F : -8.0F;
        int iconColor = WaypointRenderMath.scaleAlpha(color, opacity);
        int backgroundColor = WaypointRenderMath.scaleAlpha(TEXT_BACKGROUND, opacity);
        int textColor = WaypointRenderMath.scaleAlpha(0xFFFFFFFF, opacity);
        if (clustered && ((iconColor >>> 24) & 0xFF) >= 4) {
            submitClusterBadge(poseStack, submitCollector, badgeRadius, emphasized, iconColor);
        }
        if (((iconColor >>> 24) & 0xFF) >= 4) {
            if (!clustered && emphasized) {
                submitEmphasisDecoration(poseStack, submitCollector, iconColor, customIcon);
            }
            if (clustered) {
                var foregroundCollector = submitCollector.order(FOREGROUND_TEXT_ORDER);
                foregroundCollector.submitText(poseStack, -font.width(countText) / 2.0F, -font.lineHeight / 2.0F,
                        countText, false, Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, textColor, 0, 0);
            } else if (customIcon && WorldMarkerIconRenderer.submit(poseStack, submitCollector, iconId,
                    WaypointRenderMath.scaleAlpha(0xFFFFFFFF, opacity))) {
                // The selected icon texture is submitted inside its double frame.
            } else if (emphasized) {
                submitOutlinedText(submitCollector, poseStack, icon, x, iconY, iconColor,
                        WaypointRenderMath.scaleAlpha(MarkerHoverStyle.outlineColor(color), opacity));
            } else {
                submitCollector.submitText(poseStack, x, iconY, icon, true, Font.DisplayMode.SEE_THROUGH,
                        FULL_BRIGHT, iconColor, backgroundColor, 0);
            }
        }
        for (int row = 0; row < labels.size(); row++) {
            PendingWaypointLabel label = labels.get(row);
            FormattedCharSequence sequence = Component.literal(label.text()).getVisualOrderText();
            float prefixWidth = label.marker() == null || label.marker().getOwnerId() == null ? 0.0F : 10.0F;
            float textWidth = font.width(sequence);
            float faceX = -(textWidth + prefixWidth) / 2.0F;
            float labelX = faceX + prefixWidth;
            float labelY = clustered
                    ? -badgeRadius - LABEL_GAP_PIXELS - font.lineHeight - row * (font.lineHeight + LABEL_GAP_PIXELS)
                    : iconY - font.lineHeight - LABEL_GAP_PIXELS;
            labelY -= verticalOffset;
            int rowTextColor = WaypointRenderMath.scaleAlpha(0xFFFFFFFF, label.opacity());
            int rowBackground = WaypointRenderMath.scaleAlpha(TEXT_BACKGROUND, label.opacity());
            if (((rowTextColor >>> 24) & 0xFF) >= 4) {
                if (label.emphasized()) {
                    submitOutlinedText(submitCollector, poseStack, sequence, labelX, labelY, rowTextColor,
                            WaypointRenderMath.scaleAlpha(0xFF000000, label.opacity()));
                } else {
                    if (prefixWidth > 0.0F) submitLabelBackground(submitCollector, poseStack,
                            faceX, labelY, textWidth + prefixWidth, rowBackground);
                    submitCollector.submitText(poseStack, labelX, labelY, sequence, true,
                            Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, rowTextColor,
                            prefixWidth > 0.0F ? 0 : rowBackground, 0);
                }
                if (label.marker() != null && prefixWidth > 0.0F) {
                    var face = WaypointOwnerFace.texture(label.marker().getOwnerId(), label.marker().getOwnerName());
                    WorldMarkerIconRenderer.submitFace(poseStack, submitCollector, face, faceX + 1.0F,
                            labelY + 0.5F, 8.0F, (rowTextColor >>> 24) & 0xFF);
                }
            }
        }
        finishBillboard(poseStack);
    }

    private static void submitClusterBadge(PoseStack poses, SubmitNodeCollector collector,
                                          float radius, boolean emphasized, int alphaSource) {
        collector.submitCustomGeometry(poses, RenderTypes.textBackgroundSeeThrough(), (pose, vertices) ->
                WaypointClusterBadge.emit(radius, emphasized, alphaSource, (x, y, color) ->
                        vertices.addVertex(pose.pose(), x, y, 0.01F).setColor(color).setLight(FULL_BRIGHT)));
    }

    private static void submitLabelBackground(SubmitNodeCollector collector, PoseStack poses,
                                              float left, float top, float width, int color) {
        int alpha = color >>> 24;
        if (alpha <= 0) return;
        collector.submitCustomGeometry(poses, RenderTypes.textBackgroundSeeThrough(), (pose, vertices) -> {
            org.joml.Matrix4f matrix = pose.pose();
            float right = left + width;
            int red = (color >>> 16) & 0xFF, green = (color >>> 8) & 0xFF, blue = color & 0xFF;
            vertices.addVertex(matrix, left - 1.0F, top + 9.0F, 0.01F).setColor(red, green, blue, alpha).setLight(FULL_BRIGHT);
            vertices.addVertex(matrix, right + 1.0F, top + 9.0F, 0.01F).setColor(red, green, blue, alpha).setLight(FULL_BRIGHT);
            vertices.addVertex(matrix, right + 1.0F, top - 1.0F, 0.01F).setColor(red, green, blue, alpha).setLight(FULL_BRIGHT);
            vertices.addVertex(matrix, left - 1.0F, top - 1.0F, 0.01F).setColor(red, green, blue, alpha).setLight(FULL_BRIGHT);
        });
    }

    private static void submitOutlinedText(SubmitNodeCollector collector, PoseStack poses,
                                           FormattedCharSequence text, float x, float y,
                                           int color, int outlineColor) {
        var outlineCollector = collector.order(OUTLINE_TEXT_ORDER);
        FormattedCharSequence outline = net.aosankaku.openmapdelta.compat.EmojifulTextCompat.outlineSequence(text);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                outlineCollector.submitText(poses, x + dx, y + dy, outline, false, Font.DisplayMode.SEE_THROUGH,
                        FULL_BRIGHT, outlineColor, 0, 0);
            }
        }
        // 26.2 builds a separate font batch for each order phase, so these groups preserve glyph order.
        var foregroundCollector = collector.order(FOREGROUND_TEXT_ORDER);
        foregroundCollector.submitText(poses, x, y, text, false, Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT, color, 0, 0);
    }

    private static void submitEmphasisDecoration(PoseStack poses, SubmitNodeCollector collector,
                                                 int iconColor, boolean customIcon) {
        float opacity = ((iconColor >>> 24) & 0xFF) / 255.0F;
        int haloColor = (iconColor & 0x00FFFFFF) | (Math.min(0x70, (iconColor >>> 24) / 2) << 24);
        int black = WaypointRenderMath.scaleAlpha(0xFF000000, opacity);
        int white = WaypointRenderMath.scaleAlpha(0xFFFFFFFF, opacity);
        collector.submitCustomGeometry(poses, RenderTypes.textBackgroundSeeThrough(), (pose, vertices) -> {
            org.joml.Matrix4f matrix = pose.pose();
            submitQuad(vertices, matrix, -10.5F, -10.5F, 10.5F, 10.5F, 0.02F, haloColor);
            if (customIcon) {
                submitSquareFrame(vertices, matrix, 10.0F, 1.0F, black);
                submitSquareFrame(vertices, matrix, 8.75F, 0.75F, white);
            }
        });
    }

    private static void submitSquareFrame(com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                          org.joml.Matrix4f pose, float halfSize, float width, int color) {
        float inner = halfSize - width;
        submitQuad(vertices, pose, -halfSize, -halfSize, halfSize, -inner, 0.01F, color);
        submitQuad(vertices, pose, -halfSize, inner, halfSize, halfSize, 0.01F, color);
        submitQuad(vertices, pose, -halfSize, -inner, -inner, inner, 0.01F, color);
        submitQuad(vertices, pose, inner, -inner, halfSize, inner, 0.01F, color);
    }

    private static void submitQuad(com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                   org.joml.Matrix4f pose, float left, float top, float right, float bottom,
                                   float z, int color) {
        vertices.addVertex(pose, left, bottom, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(pose, right, bottom, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(pose, right, top, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(pose, left, top, z).setColor(color).setLight(FULL_BRIGHT);
    }

    private static void renderBillboard(PoseStack poseStack, Camera camera, float scale) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        poseStack.scale(-scale, -scale, scale);
    }

    private static void finishBillboard(PoseStack poseStack) {
        poseStack.popPose();
    }

    private record WaypointCandidate(Marker marker, WaypointRenderMath.Opacities opacities,
                                     String label, boolean nearName, double playerDistance) {
    }

    private record PendingWaypointLabel(String text, float opacity, boolean emphasized, Marker marker) {
    }

}
