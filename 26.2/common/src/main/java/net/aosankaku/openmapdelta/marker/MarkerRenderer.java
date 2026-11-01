// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.render.FullscreenMapProjection;
import net.aosankaku.openmapdelta.render.FullscreenMapMarkerGeometry;
import net.aosankaku.openmapdelta.render.FullscreenMapZoom;
import net.aosankaku.openmapdelta.render.FullscreenMapLabelGeometry;
import net.aosankaku.openmapdelta.render.WaypointClusterBadge;
import net.aosankaku.openmapdelta.render.MinimapPlayerIconMath;
import net.aosankaku.openmapdelta.render.MinimapSizing;

import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.render.MapRenderMath;
import net.aosankaku.openmapdelta.navigation.NavigationMapSelection;
import net.aosankaku.openmapdelta.util.MathUtil;
import net.aosankaku.openmapdelta.util.RenderUtil;
import net.aosankaku.openmapdelta.render.WaypointOwnerFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;

public final class MarkerRenderer {
    public static final int FULLSCREEN_MARKER_SIZE = 5;
    private MarkerRenderer() {}

    public static void renderHud(GuiGraphicsExtractor graphics, Minecraft mc, int mapX, int mapY, int size,
                                  float partialTick, boolean headingUp, float yaw, float parentScale,
                                  List<Marker> markers) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        int centerX = mapX + size / 2;
        int centerY = mapY + size / 2;
        int stride = RuntimeClientState.getZoomBlocksPerPixel();
        float radius = MarkerIconSizing.minimapEdgeRadius(size, OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get());
        float waypointScale = OpenMapDeltaConfig.MINIMAP_WAYPOINT_SCALE.get().floatValue();
        float playerScale = OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE.get().floatValue();
        float labelScale = (float) MinimapSizing.textScale(waypointScale, parentScale,
                mc.getWindow().getGuiScale(), mc.font.lineHeight);
        List<HudMarker> hudMarkers = new ArrayList<>();
        List<MinimapMarkerLabelSelection.Candidate<java.util.UUID>> labelCandidates = new ArrayList<>();

        if (!markers.isEmpty()) {
            for (Marker marker : markers) {
                // The local player arrow is drawn once below, with its navigation emphasis if needed.
                if (marker.getId().equals(player.getUUID())) continue;
                boolean navigating = MarkerManager.get().isNavigating(marker.getId());
                float dx = (float) ((marker.getX() - player.getX()) / stride);
                float dz = (float) ((marker.getZ() - player.getZ()) / stride);
                boolean outsideMap = MinimapPlayerLabel.isOutsideWorld(player.getX(), player.getZ(),
                        marker.getX(), marker.getZ(), stride, size,
                        OpenMapDeltaConfig.SHAPE.get() == OpenMapDeltaConfig.MinimapShape.CIRCLE);
                boolean entityMarker = marker.getType() == MarkerType.ENTITY;
                // Live entities are only useful inside the actual terrain footprint; never edge-clamp them.
                if (entityMarker && outsideMap) continue;

                if (headingUp) {
                    float rad = (float) Math.toRadians(yaw);
                    float rx = -dx * Mth.cos(rad) - dz * Mth.sin(rad);
                    float rz = dx * Mth.sin(rad) - dz * Mth.cos(rad);
                    dx = rx;
                    dz = rz;
                }

                float[] clamped = new float[2];
                boolean notice = marker.getType() == MarkerType.LOCATION_NOTICE;
                if (!entityMarker && (navigating || notice || OpenMapDeltaConfig.STICK_MARKERS_TO_BORDER.get())) {
                    MapRenderMath.clampToCircle(dx, dz, radius, clamped);
                } else {
                    clamped[0] = dx;
                    clamped[1] = dz;
                }

                int px = centerX + (int) clamped[0];
                int py = centerY + (int) clamped[1];

                boolean inView = entityMarker || navigating || notice || OpenMapDeltaConfig.STICK_MARKERS_TO_BORDER.get()
                        || Math.abs(dx) <= radius && Math.abs(dz) <= radius;
                boolean emphasized = navigating || notice;
                boolean ordinaryWaypoint = marker.getType().isWorldWaypoint() && !notice && !navigating;
                double distance = navigating ? MarkerManager.get().navigationDistance(mc, marker.getId())
                        : MathUtil.distanceXZ(player.getX(), player.getZ(), marker.getX(), marker.getZ());
                if (!entityMarker) labelCandidates.add(new MinimapMarkerLabelSelection.Candidate<>(marker.getId(),
                        String.valueOf(marker.getId()), distance, inView, ordinaryWaypoint, emphasized));
                if (!inView) continue;

                boolean playerMarker = marker.getType() == MarkerType.PLAYER;
                int iconSize = playerMarker ? scaledSize(14, playerScale)
                        : entityMarker ? EntityMarkerStyle.minimapSize(waypointScale)
                        : scaledSize(MarkerIconRenderer.hasIcon(marker) ? 10 : 4, waypointScale);
                hudMarkers.add(new HudMarker(marker, px, py, iconSize, navigating, notice,
                        ordinaryWaypoint, distance, outsideMap));
            }
        }

        Set<java.util.UUID> labeledWaypoints = new HashSet<>(MinimapMarkerLabelSelection.select(
                labelCandidates, OpenMapDeltaConfig.MINIMAP_LABEL_LIMIT.get()));
        // Prefer highlighted representatives without combining nearby individual waypoints.
        hudMarkers.sort(Comparator.comparingInt(entry -> entry.navigating() ? 0 : entry.notice() ? 1
                : entry.marker().getType() == MarkerType.ENTITY ? 2 : 3));
        List<MinimapWaypointLayout.Point<HudMarker>> waypointPoints = new ArrayList<>();
        List<HudMarker> otherMarkers = new ArrayList<>();
        for (HudMarker entry : hudMarkers) {
            if (entry.marker().getType().isWorldWaypoint() || entry.marker().getType() == MarkerType.NAVIGATION) {
                waypointPoints.add(new MinimapWaypointLayout.Point<>(entry, entry.x(), entry.y(),
                        Math.max(entry.iconSize() / 2.0, 10.0 * waypointScale), entry.distance()));
            } else otherMarkers.add(entry);
        }
        List<MinimapWaypointLayout.Label<HudLabelBlock>> labels = new ArrayList<>();
        // Put live entities below waypoint and player icons so they remain unobtrusive.
        for (HudMarker other : otherMarkers) {
            if (other.marker().getType() == MarkerType.ENTITY) {
                drawHudMarker(graphics, mc, other, labeledWaypoints, labelScale, labels, headingUp, yaw, partialTick);
            }
        }
        otherMarkers.removeIf(entry -> entry.marker().getType() == MarkerType.ENTITY);
        int selfSize = scaledSize(14, playerScale);
        for (MinimapWaypointLayout.Group<HudMarker> group : MinimapWaypointLayout.group(
                waypointPoints, OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get())) {
            if (group.count() == 1) {

                drawHudMarker(graphics, mc, group.representative().value(), labeledWaypoints, labelScale, labels,
                        headingUp, yaw, partialTick);
                continue;
            }
            HudMarker anchor = group.representative().value();
            boolean emphasized = group.members().stream().anyMatch(point -> point.value().navigating() || point.value().notice());
            float badgeRadius = drawHudClusterBadge(graphics, mc, anchor.x(), anchor.y(),
                    group.count(), labelScale, emphasized);
            List<HudLabelRow> rows = group.members().stream().map(point -> new HudLabelRow(
                    point.value().marker().getDisplayLabel(), 0xFFFFFFFF,
                    point.value().navigating() || point.value().notice(), point.value().marker())).toList();
            addHudLabelBlock(mc, labels, anchor.x(), anchor.y() - badgeRadius - labelScale,
                    rows, labelScale);
        }
        for (HudMarker other : otherMarkers) {

            drawHudMarker(graphics, mc, other, labeledWaypoints, labelScale, labels, headingUp, yaw, partialTick);
        }
        boolean selfNavigation = MarkerManager.get().isNavigating(player.getUUID());
        int selfFootprint = minimapPlayerFootprintSize(selfSize, OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get(),
                OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get());
        if (selfNavigation) drawNavigationHalo(graphics, centerX, centerY, selfFootprint);
        drawPlayerIcon(graphics, mc, player.getUUID(), player.getGameProfile().name(), centerX, centerY, selfSize,
                OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get(), OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get(),
                headingUp, yaw, partialTick);
        // Labels stay attached to their points and render in front of every icon, including the local arrow.
        graphics.nextStratum();
        for (MinimapWaypointLayout.PlacedLabel<HudLabelBlock> placed : MinimapWaypointLayout.placeLabels(labels)) {
            HudLabelBlock block = placed.label().value();
            int top = (int) Math.floor(placed.top()) + block.padding();
            for (int row = 0; row < block.rows().size(); row++) {
            HudLabelRow label = block.rows().get(row);
            drawHudLabel(graphics, mc, label.text(), block.x(), top + row * block.rowHeight(),
                    labelScale, label.color(), label.emphasized(), label.marker());
            }
        }

        if (selfNavigation) {
            Marker self = MarkerManager.get().getMapMarker(player.getUUID());
            if (self != null) drawHudLabel(graphics, mc, navigationLabel(mc, self), centerX,
                    hudLabelY(mc, centerY, selfFootprint, labelScale), labelScale, 0xFFFFFFFF, true, self);
        }
    }

    /** Space required by the largest cluster badge that can appear on the minimap edge. */
    public static double clusterBadgeOverhang(Minecraft mc, List<Marker> markers, int size, int mapInset,
                                               float renderScale, boolean headingUp, float yaw) {
        double nameDistance = OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get();
        if (nameDistance <= 0 || markers.isEmpty() || mc.player == null) return 0;

        LocalPlayer player = mc.player;
        int stride = RuntimeClientState.getZoomBlocksPerPixel();
        float edgeRadius = MarkerIconSizing.minimapEdgeRadius(size, OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get());
        boolean stickToBorder = OpenMapDeltaConfig.STICK_MARKERS_TO_BORDER.get();
        int eligibleVisible = 0;
        for (Marker marker : markers) {
            if (marker.getId().equals(player.getUUID())) continue;
            if (!marker.getType().isWorldWaypoint() && marker.getType() != MarkerType.NAVIGATION) continue;

            boolean navigating = MarkerManager.get().isNavigating(marker.getId());
            boolean notice = marker.getType() == MarkerType.LOCATION_NOTICE;
            float dx = (float) ((marker.getX() - player.getX()) / stride);
            float dz = (float) ((marker.getZ() - player.getZ()) / stride);
            if (headingUp) {
                float rad = (float) Math.toRadians(yaw);
                float rx = -dx * Mth.cos(rad) - dz * Mth.sin(rad);
                float rz = dx * Mth.sin(rad) - dz * Mth.cos(rad);
                dx = rx;
                dz = rz;
            }
            boolean inView = navigating || notice || stickToBorder
                    || Math.abs(dx) <= edgeRadius && Math.abs(dz) <= edgeRadius;
            if (inView) eligibleVisible++;
        }
        if (eligibleVisible < 2) return 0;

        int digits = Integer.toString(eligibleVisible).length();
        int widestDigit = 0;
        for (char digit = '0'; digit <= '9'; digit++) {
            widestDigit = Math.max(widestDigit, mc.font.width(Character.toString(digit)));
        }
        int countWidth = (int) Math.min(Integer.MAX_VALUE, (long) widestDigit * digits);
        int badgeRadius = (int) WaypointClusterBadge.radius(countWidth, mc.font.lineHeight);
        float labelScale = (float) MinimapSizing.textScale(OpenMapDeltaConfig.MINIMAP_WAYPOINT_SCALE.get(),
                renderScale, mc.getWindow().getGuiScale(), mc.font.lineHeight);
        return Math.max(0, OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get()
                + (badgeRadius + 1) * labelScale - mapInset) * renderScale;
    }

    private static void drawHudMarker(GuiGraphicsExtractor graphics, Minecraft mc, HudMarker hudMarker,
                                      Set<java.util.UUID> labeledWaypoints, float labelScale,
                                      List<MinimapWaypointLayout.Label<HudLabelBlock>> labels,
                                      boolean headingUp, float localYaw, float partialTick) {
        Marker marker = hudMarker.marker();
        boolean emphasized = hudMarker.navigating() || hudMarker.notice();
        MinimapWaypointLabel.Label label = null;
        if (marker.getType() == MarkerType.PLAYER) {
            OpenMapDeltaConfig.MinimapPlayerLabelMode mode = hudMarker.outsideMap()
                    ? OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get()
                    : OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get();
            String text = MinimapPlayerLabel.text(mode, marker.getDisplayLabel(), hudMarker.distance());
            if (!text.isEmpty()) {
                label = new MinimapWaypointLabel.Label(text,
                        mode == OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY);
            }
        } else if (marker.getType() != MarkerType.ENTITY) {
            boolean showLabel = emphasized
                    || hudMarker.ordinaryWaypoint() && labeledWaypoints.contains(marker.getId())
                    || !hudMarker.ordinaryWaypoint() && OpenMapDeltaConfig.SHOW_MARKER_DISTANCE.get();
            if (showLabel) {
                if (hudMarker.navigating()) {
                    label = navigationHudLabel(mc, marker);
                } else if (hudMarker.ordinaryWaypoint() || hudMarker.notice()) {
                    label = waypointHudLabel(marker, hudMarker.distance());
                } else {
                    label = new MinimapWaypointLabel.Label(marker.getDisplayLabel() + " " + (int) hudMarker.distance() + "m", false);
                }
            }
        }
        boolean distanceOnly = label != null && label.distanceOnly() && !label.text().isEmpty();
        int displayColor = distanceOnly ? MinimapWaypointLabel.distanceColor(marker.getColor()) : marker.getColor();
        int labelFootprint = marker.getType() == MarkerType.PLAYER
                ? minimapPlayerFootprintSize(hudMarker.iconSize(), OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get(),
                OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get()) : hudMarker.iconSize();
        if (emphasized) drawNavigationHalo(graphics, hudMarker.x(), hudMarker.y(), labelFootprint);
        if (marker.getType() == MarkerType.PLAYER) {
            drawPlayerIcon(graphics, mc, marker, hudMarker.x(), hudMarker.y(), hudMarker.iconSize(),
                    OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get(), OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get(),
                    headingUp, localYaw, partialTick);
        } else if (marker.getType() == MarkerType.ENTITY) {
            MarkerIconRenderer.drawEntity(graphics, marker, hudMarker.x(), hudMarker.y(), hudMarker.iconSize(),
                    EntityMarkerStyle.alpha(mc.player.getY(), marker.getY()));
        } else {
            MarkerIconRenderer.draw(graphics, marker, hudMarker.x(), hudMarker.y(), hudMarker.iconSize(), emphasized, displayColor);
        }
        if (label == null || label.text().isEmpty()) return;
        Marker labelMarker = label.distanceOnly() ? null : marker;
        addHudLabelBlock(mc, labels, hudMarker.x(), hudMarker.y() - labelFootprint / 2.0 - labelScale,
                List.of(new HudLabelRow(label.text(), distanceOnly ? displayColor : 0xFFFFFFFF,
                        emphasized, labelMarker)), labelScale);
    }

    private static void drawPlayerIcon(GuiGraphicsExtractor graphics, Minecraft mc, Marker marker, int x, int y, int size,
                                       OpenMapDeltaConfig.MinimapPlayerIcon icon, boolean rotateHead,
                                       boolean headingUp, float localYaw, float partialTick) {
        String name = mc.player != null && marker.getId().equals(mc.player.getUUID())
                ? mc.player.getGameProfile().name() : marker.getLabel();
        drawPlayerIcon(graphics, mc, marker.getId(), name, x, y, size, icon, rotateHead,
                headingUp, localYaw, partialTick);
    }

    private static void drawPlayerIcon(GuiGraphicsExtractor graphics, Minecraft mc, java.util.UUID playerId, String name,
                                       int x, int y, int size, OpenMapDeltaConfig.MinimapPlayerIcon icon,
                                       boolean rotateHead, boolean headingUp, float localYaw, float partialTick) {
        var player = mc.player != null && playerId.equals(mc.player.getUUID())
                ? mc.player : mc.level == null ? null : mc.level.getPlayerByUUID(playerId);
        float playerYaw = player == null ? 0.0f : Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
        float rotation = player == null ? 0.0f : MinimapPlayerIconMath.rotationDegrees(
                icon == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD, rotateHead,
                headingUp, playerYaw, localYaw);
        int color = net.aosankaku.openmapdelta.render.PlayerIconColor.fromUuid(playerId);
        switch (icon) {
            case VANILLA_POINTER -> RenderUtil.drawVanillaPlayerPointer(graphics, x, y, size, rotation, color);
            case TRIANGLE -> RenderUtil.drawPlayerArrow(graphics, x, y, size, rotation);
            case PLAYER_HEAD -> RenderUtil.drawPlayerHead(graphics, playerId, name, x, y, size, rotation, color);
        }
    }

    private static int minimapPlayerFootprintSize(int glyphSize, OpenMapDeltaConfig.MinimapPlayerIcon icon,
                                                   boolean rotateHead) {
        boolean canRotate = icon != OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD || rotateHead;
        return PlayerMarkerSizing.footprintSize(glyphSize, canRotate);
    }

    private static void addHudLabelBlock(Minecraft mc, List<MinimapWaypointLayout.Label<HudLabelBlock>> labels,
                                        int x, double bottom, List<HudLabelRow> rows, float scale) {
        int rowHeight = Math.max(1, (int) Math.ceil((mc.font.lineHeight + 2) * scale));
        double width = rows.stream().mapToInt(row -> mc.font.width(row.text()) + faceWidth(row.marker())).max().orElse(0)
                * scale + 4 * scale;
        int padding = Math.max(1, (int) Math.ceil(scale));
        int height = (int) Math.ceil(mc.font.lineHeight * scale) + rowHeight * (rows.size() - 1) + 2 * padding;
        labels.add(new MinimapWaypointLayout.Label<>(new HudLabelBlock(x, rows, rowHeight, padding),
                x, bottom - height, width, height));
    }

    private static float drawHudClusterBadge(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y,
                                            int count, float scale, boolean emphasized) {
        String text = Integer.toString(count);
        int radius = (int) WaypointClusterBadge.radius(mc.font.width(text), mc.font.lineHeight);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            drawHudDisk(graphics, radius, 0xFF000000);
            drawHudDisk(graphics, radius - 1, emphasized ? 0xFFFFFF55 : 0xFFFFFFFF);
            drawHudDisk(graphics, radius - 2, 0xE6000000);
            graphics.centeredText(mc.font, text, 0, -mc.font.lineHeight / 2, 0xFFFFFFFF);
        } finally {
            graphics.pose().popMatrix();
        }
        return radius * scale;
    }

    private static void drawHudDisk(GuiGraphicsExtractor graphics, int radius, int color) {
        for (int y = -radius; y < radius; y++) {
            double edge = y < 0 ? y + 1.0 : y;
            int half = (int) Math.floor(Math.sqrt(radius * radius - edge * edge));
            graphics.fill(-half, y, half + 1, y + 1, color);
        }
    }

    private static int scaledSize(int baseSize, float scale) {
        return Math.max(2, Math.round(baseSize * scale));
    }

    private static int hudLabelY(Minecraft mc, int markerY, int iconSize, float scale) {
        return markerY - (int) Math.ceil(iconSize / 2.0 + mc.font.lineHeight * scale + 2.0 * scale);
    }

    private static MinimapWaypointLabel.Label waypointHudLabel(Marker marker, double distance) {
        return MinimapWaypointLabel.label(marker.getDisplayLabel(), (int) distance + "m", distance,
                OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get(), OpenMapDeltaConfig.SHOW_MARKER_DISTANCE.get());
    }

    private static MinimapWaypointLabel.Label navigationHudLabel(Minecraft mc, Marker marker) {
        if (!marker.getType().isWorldWaypoint() && marker.getType() != MarkerType.NAVIGATION) {
            return new MinimapWaypointLabel.Label(navigationLabel(mc, marker), false);
        }
        double distance = MarkerManager.get().navigationDistance(mc, marker.getId());
        return MinimapWaypointLabel.label(marker.getDisplayLabel(), navigationDistanceText(distance), distance,
                OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get(), true);
    }

    private static void drawHudLabel(GuiGraphicsExtractor graphics, Minecraft mc, String text, int x, int y,
                                     float scale, int color, boolean emphasized, Marker marker) {
        int face = faceWidth(marker);
        float totalWidth = mc.font.width(text) + face;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            MarkerLabelRenderer.draw(graphics, mc.font, text, -(int) Math.floor(totalWidth / 2.0F), 0,
                    color, emphasized, marker);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int faceWidth(Marker marker) { return marker != null && marker.getOwnerId() != null ? 10 : 0; }

    private record HudMarker(Marker marker, int x, int y, int iconSize, boolean navigating,
                             boolean notice, boolean ordinaryWaypoint, double distance, boolean outsideMap) {}
    private record HudLabelRow(String text, int color, boolean emphasized, Marker marker) {}
    private record HudLabelBlock(int x, List<HudLabelRow> rows, int rowHeight, int padding) {}

    public static void renderFullscreen(GuiGraphicsExtractor graphics, Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                         double centerWorldX, double centerWorldZ, double blocksPerPixel,
                                         float partialTick, int mouseX, int mouseY) {
        if (mc.player == null || mc.level == null) return;
        double bpp = FullscreenMapZoom.safeBlocksPerPixel(blocksPerPixel);
        List<FullscreenMarker> drawn = fullscreenDrawnMarkers(mc, mapX, mapY, mapW, mapH, centerWorldX, centerWorldZ, bpp);
        LocalHead self = fullscreenLocalHead(mc, mapX, mapY, mapW, mapH, centerWorldX, centerWorldZ,
                bpp, drawn, mouseX, mouseY);
        Marker hovered = findFullscreenSelectableMarker(mc, mapX, mapY, mapW, mapH, centerWorldX, centerWorldZ,
                bpp, drawn, self, mouseX, mouseY);
        for (FullscreenMarker entry : drawn) {
            if (entry.marker().getType() == MarkerType.ENTITY) {
                Marker marker = entry.marker();
                MarkerIconRenderer.drawEntity(graphics, marker, entry.x(), entry.y(), entry.size(),
                        EntityMarkerStyle.alpha(mc.player.getY(), marker.getY()));
            }
        }
        if (self != null) {
            boolean navigating = MarkerManager.get().isNavigating(self.marker().getId());
            if (navigating) drawNavigationHalo(graphics, self.x(), self.y(), self.footprintSize());
            drawPlayerIcon(graphics, mc, self.marker(), self.x(), self.y(), self.size(), self.icon(),
                    OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get(), false, 0.0f, partialTick);
            boolean highlighted = sameMarker(self.marker(), hovered);
            if (highlighted) drawFrame(graphics, self.x(), self.y(), self.footprintSize() + 2, 0xFFFFFFFF);
            if (navigating && !self.edge()) drawLabel(graphics, mc, self.marker(), self.x(), self.y(),
                    self.footprintSize(), highlighted);
        }
        for (FullscreenMarker entry : drawn) {
            Marker marker = entry.marker();
            if (marker.getType() == MarkerType.ENTITY) continue;
            int px = entry.x();
            int py = entry.y();
            int iconSize = entry.size();
            boolean highlight = sameMarker(marker, hovered);
            if (entry.navigation()) drawNavigationHalo(graphics, px, py, entry.footprintSize());
            if (marker.getType() == MarkerType.PLAYER) {
                drawPlayerHoverOutline(graphics, marker, px, py, entry.footprintSize(), highlight);
                drawPlayerIcon(graphics, mc, marker, px, py, iconSize,
                        OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get(), OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get(),
                        false, 0.0f, partialTick);
            } else {
                MarkerIconRenderer.draw(graphics, marker, px, py, iconSize, highlight);
            }
            if (!entry.edge() && marker.getType() != MarkerType.ENTITY && fullscreenLabelVisible(marker, bpp)) drawLabel(graphics, mc, marker, px, py,
                    entry.footprintSize(), highlight);
        }
    }

    private static void drawLabel(GuiGraphicsExtractor graphics, Minecraft mc, Marker marker,
                                  int x, int y, int iconSize, boolean highlight) {
        String label = mapLabel(mc, marker);
        var layout = FullscreenMapLabelGeometry.place(x, y, iconSize, mc.font.width(label) + faceWidth(marker), mc.font.lineHeight,
                fullscreenLabelScale(marker));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(layout.x(), layout.y());
            graphics.pose().scale(layout.scale(), layout.scale());
            MarkerLabelRenderer.draw(graphics, mc.font, label, layout.textX(), 0, marker.getColor(), highlight, marker);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static boolean sameMarker(Marker first, Marker second) {
        return first != null && second != null && java.util.Objects.equals(first.getId(), second.getId());
    }

    private static String navigationLabel(Minecraft mc, Marker marker) {
        double distance = MarkerManager.get().navigationDistance(mc, marker.getId());
        return marker.getDisplayLabel() + " " + navigationDistanceText(distance);
    }

    private static String navigationDistanceText(double distance) {
        String meters = Double.isFinite(distance) ? Long.toString(Math.round(distance)) : "?";
        return Component.translatable("gui.odysseymap.navigation.distance", meters).getString();
    }

    private static String mapLabel(Minecraft mc, Marker marker) {
        return MarkerManager.get().isNavigating(marker.getId()) ? navigationLabel(mc, marker) : marker.getDisplayLabel();
    }

    private static void drawNavigationHalo(GuiGraphicsExtractor graphics, int x, int y, int size) {
        int half = (size + 6) / 2;
        graphics.fill(x - half, y - half, x + half + 1, y + half + 1, 0x3055FFFF);
        drawFrame(graphics, x, y, size + 6, 0xFF55FFFF);
    }

    private static void drawPlayerHoverOutline(GuiGraphicsExtractor graphics, Marker marker, int x, int y,
                                               int size, boolean hovered) {
        if (!hovered) return;
        int displayColor = net.aosankaku.openmapdelta.render.PlayerIconColor.fromUuid(marker.getId());
        int outlineColor = MarkerHoverStyle.outlineColor(displayColor);
        drawFrame(graphics, x, y, size + 4, outlineColor ^ 0x00FFFFFF);
        drawFrame(graphics, x, y, size + 2, outlineColor);
    }

    private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        int left = x - size / 2;
        int top = y - size / 2;
        int right = left + size;
        int bottom = top + size;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    /** Includes remote players, map destinations and the local head only when it is actually drawn. */
    public static Marker findFullscreenSelectableMarker(Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                                          double centerX, double centerZ, double bpp,
                                                          double mouseX, double mouseY) {
        if (mc.player == null || mc.level == null) return null;
        double safeBpp = FullscreenMapZoom.safeBlocksPerPixel(bpp);
        List<FullscreenMarker> drawn = fullscreenDrawnMarkers(mc, mapX, mapY, mapW, mapH, centerX, centerZ, safeBpp);
        LocalHead self = fullscreenLocalHead(mc, mapX, mapY, mapW, mapH, centerX, centerZ,
                safeBpp, drawn, (int) Math.floor(mouseX), (int) Math.floor(mouseY));
        return findFullscreenSelectableMarker(mc, mapX, mapY, mapW, mapH, centerX, centerZ,
                safeBpp, drawn, self, mouseX, mouseY);
    }

    private static Marker findFullscreenSelectableMarker(Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                                          double centerX, double centerZ, double bpp, List<FullscreenMarker> drawn,
                                                          LocalHead self, double mouseX, double mouseY) {
        Marker hit = null;
        boolean selfHead = self != null && self.icon() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD;
        boolean selfIconHit = self != null && (selfHead
                ? FullscreenLocalPlayerSizing.contains(mouseX, mouseY, self.x(), self.y(), self.size(),
                OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get())
                : MarkerIconSizing.contains(mouseX, mouseY, self.x(), self.y(), self.footprintSize()));
        if (self != null && (selfIconHit
                || MarkerManager.get().isNavigating(self.marker().getId())
                && !self.edge() && labelContains(mc, self.marker(), self.x(), self.y(), self.footprintSize(), mouseX, mouseY))) {
            hit = self.marker();
        }
        for (FullscreenMarker entry : drawn) {
            Marker marker = entry.marker();
            if (!marker.getType().isMapSelectable()) continue;
            // The local head is submitted first; the last actually drawn marker wins an overlap.
            if (MarkerIconSizing.contains(mouseX, mouseY, entry.x(), entry.y(), entry.footprintSize())
                    || !entry.edge() && fullscreenLabelVisible(marker, bpp)
                    && labelContains(mc, marker, entry.x(), entry.y(), entry.footprintSize(), mouseX, mouseY)) hit = marker;
        }
        return hit;
    }

    /** Compatibility entry point; selection now supports all map interaction targets. */
    public static Marker findFullscreenWaypoint(Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                                double centerX, double centerZ, double bpp,
                                                double mouseX, double mouseY) {
        return findFullscreenSelectableMarker(mc, mapX, mapY, mapW, mapH, centerX, centerZ, bpp, mouseX, mouseY);
    }

    private static boolean fullscreenLabelVisible(Marker marker, double bpp) {
        return !(marker.getType().isWorldWaypoint() || marker.getType() == MarkerType.NAVIGATION)
                || FullscreenMapZoom.waypointLabelsVisible(bpp, OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM.get());
    }

    private static double fullscreenLabelScale(Marker marker) {
        return marker.getType().isWorldWaypoint() || marker.getType() == MarkerType.NAVIGATION
                ? OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.get() : 1.0;
    }

    private static boolean labelContains(Minecraft mc, Marker marker, int x, int y, int size,
                                         double mouseX, double mouseY) {
        return FullscreenMapLabelGeometry.place(x, y, size, mc.font.width(mapLabel(mc, marker)) + faceWidth(marker),
                mc.font.lineHeight, fullscreenLabelScale(marker)).contains(mouseX, mouseY);
    }

    private record FullscreenMarker(Marker marker, int x, int y, int size, int footprintSize,
                                    boolean edge, boolean navigation) {}
    private record LocalHead(Marker marker, int x, int y, int size, int footprintSize,
                             OpenMapDeltaConfig.MinimapPlayerIcon icon, boolean edge) {}

    private static LocalHead fullscreenLocalHead(Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                                  double centerX, double centerZ, double bpp,
                                                  List<FullscreenMarker> drawn, double mouseX, double mouseY) {
        boolean navigating = MarkerManager.get().isNavigating(mc.player.getUUID());
        boolean waypointOverlaps = false;
        if (!navigating) {
            for (FullscreenMarker entry : drawn) {
                Marker marker = entry.marker();
                if (NavigationMapSelection.suppressesLocalHead(marker.getType(), marker.getTrackedPlayerId())
                        && MathUtil.distanceXZ(mc.player.getX(), mc.player.getZ(),
                        marker.getX(), marker.getZ()) < 2.0) { waypointOverlaps = true; break; }
            }
        }
        Marker marker = MarkerManager.get().getMapMarker(mc.player.getUUID());
        if (marker == null) return null;
        OpenMapDeltaConfig.MinimapPlayerIcon icon = OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get();
        boolean head = icon == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD;
        int size = fullscreenLocalPlayerSize(bpp, head);
        boolean inside = isInside(mapX, mapY, mapW, mapH,
                FullscreenMapProjection.pixel(mapX, mapW, centerX, mc.player.getX(), bpp),
                FullscreenMapProjection.pixel(mapY, mapH, centerZ, mc.player.getZ(), bpp));
        if (!NavigationMapSelection.localHeadVisible(OpenMapDeltaConfig.MAP_SHOW_PLAYER_MARKER.get(),
                navigating, waypointOverlaps, inside || navigating)) return null;
        FullscreenMapMarkerGeometry.Position position = fullscreenMarkerPosition(mc, mapX, mapY, mapW, mapH,
                centerX, centerZ, bpp, marker);
        boolean rotateHead = OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get();
        if (head && !position.edge() && inside && isInside(mapX, mapY, mapW, mapH,
                (int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
            double distance = Math.hypot(mouseX - position.x(), mouseY - position.y());
            size = FullscreenLocalPlayerSizing.size(size, bpp, distance);
        }
        int footprintSize = fullscreenPlayerFootprintSize(size, icon, rotateHead);
        return new LocalHead(marker, position.x(), position.y(), size, footprintSize, icon, position.edge());
    }

    private static int fullscreenLocalPlayerSize(double bpp, boolean head) {
        int maximum = OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.get();
        if (head) return FullscreenLocalPlayerSizing.normalSize(bpp, maximum);
        return fullscreenPlayerSize(bpp, false);
    }

    private static int fullscreenPlayerSize(double bpp, boolean head) {
        int maximum = OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.get();
        int size = PlayerMarkerSizing.fullscreenSize(bpp, head, maximum);
        return head ? size : Math.max(2, Math.min(maximum, size / 2 * 2));
    }

    private static int fullscreenIconSize(Minecraft mc, double bpp, Marker marker) {
        if (mc.player != null && marker.getId().equals(mc.player.getUUID())) {
            boolean head = OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD;
            return fullscreenLocalPlayerSize(bpp, head);
        }
        if (marker.getType() == MarkerType.PLAYER) {
            boolean head = OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD;
            return fullscreenPlayerSize(bpp, head);
        }
        if (marker.getType() == MarkerType.ENTITY) return EntityMarkerStyle.fullscreenSize(bpp);
        int size = MarkerIconSizing.fullscreenSize(bpp, MarkerIconRenderer.hasIcon(marker));
        return size;
    }

    private static int fullscreenPlayerFootprintSize(int glyphSize, OpenMapDeltaConfig.MinimapPlayerIcon icon,
                                                       boolean rotateHead) {
        boolean canRotate = icon != OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD || rotateHead;
        return PlayerMarkerSizing.footprintSize(glyphSize, canRotate);
    }

    /** The exact marker center used by fullscreen drawing and pointer hit testing. */
    public static FullscreenMapMarkerGeometry.Position fullscreenMarkerPosition(Minecraft mc, int mapX, int mapY,
            int mapW, int mapH, double centerX, double centerZ, double bpp, Marker marker) {
        boolean navigation = MarkerManager.get().isNavigating(marker.getId());
        int px = FullscreenMapProjection.pixel(mapX, mapW, centerX, marker.getX(), bpp);
        int py = FullscreenMapProjection.pixel(mapY, mapH, centerZ, marker.getZ(), bpp);
        return FullscreenMapMarkerGeometry.place(mapX, mapY, mapW, mapH, px, py,
                fullscreenFootprintSize(mc, bpp, marker), navigation);
    }

    private static int fullscreenFootprintSize(Minecraft mc, double bpp, Marker marker) {
        int glyphSize = fullscreenIconSize(mc, bpp, marker);
        boolean localPlayer = mc.player != null && marker.getId().equals(mc.player.getUUID());
        if (!localPlayer && marker.getType() != MarkerType.PLAYER) return glyphSize;
        OpenMapDeltaConfig.MinimapPlayerIcon icon = OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get();
        return fullscreenPlayerFootprintSize(glyphSize, icon, OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get());
    }

    private static boolean isInside(int x, int y, int width, int height, int px, int py) {
        return width > 0 && height > 0 && px >= x && py >= y
                && (long) px < (long) x + width && (long) py < (long) y + height;
    }

    private static List<FullscreenMarker> fullscreenDrawnMarkers(Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                                       double centerX, double centerZ, double bpp) {
        if (mc.player == null || mc.level == null) return List.of();
        List<FullscreenMarker> candidates = new java.util.ArrayList<>();
        Set<java.util.UUID> selectedOrdinary = new HashSet<>();
        List<java.util.UUID> offscreenOrdinary = new java.util.ArrayList<>();
        int ordinaryCount = 0;
        int ordinaryLimit = Math.max(0, OpenMapDeltaConfig.MAP_MAX_WAYPOINTS_RENDERED.get());
        for (Marker marker : MarkerManager.get().getMapMarkers(mc.level.dimension())) {
            if (marker.getId().equals(mc.player.getUUID())) continue;
            boolean navigation = MarkerManager.get().isNavigating(marker.getId());
            boolean player = marker.getType() == MarkerType.PLAYER;
            boolean entity = marker.getType() == MarkerType.ENTITY;
            if (!navigation && player && (!OpenMapDeltaConfig.SHOW_PLAYERS.get() || !OpenMapDeltaConfig.MAP_SHOW_PLAYER_MARKER.get())) continue;
            boolean notice = marker.getType() == MarkerType.LOCATION_NOTICE;
            if (!navigation && !notice && !player && !entity && !OpenMapDeltaConfig.MAP_SHOW_WAYPOINTS.get()) continue;
            FullscreenMapMarkerGeometry.Position position = fullscreenMarkerPosition(mc, mapX, mapY, mapW, mapH,
                    centerX, centerZ, bpp, marker);
            if (position.edge() && !navigation && !notice && (player || !marker.getType().isWorldWaypoint())) continue;
            int iconSize = fullscreenIconSize(mc, bpp, marker);
            int footprintSize = fullscreenFootprintSize(mc, bpp, marker);
            candidates.add(new FullscreenMarker(marker, position.x(), position.y(), iconSize, footprintSize,
                    position.edge(), navigation));
            if (navigation || notice || player || entity) continue;
            if (position.edge()) {
                offscreenOrdinary.add(marker.getId());
            } else if (ordinaryCount < ordinaryLimit) {
                ordinaryCount++;
                selectedOrdinary.add(marker.getId());
            }
        }
        int remaining = Math.max(0, ordinaryLimit - ordinaryCount);
        for (int i = 0; i < Math.min(remaining, offscreenOrdinary.size()); i++) {
            selectedOrdinary.add(offscreenOrdinary.get(i));
        }
        List<FullscreenMarker> drawn = new java.util.ArrayList<>();
        for (FullscreenMarker entry : candidates) {
            Marker marker = entry.marker();
            if (entry.navigation() || marker.getType() == MarkerType.LOCATION_NOTICE
                    || marker.getType() == MarkerType.PLAYER || marker.getType() == MarkerType.ENTITY
                    || selectedOrdinary.contains(marker.getId())) {
                drawn.add(entry);
            }
        }
        return drawn;
    }
}
