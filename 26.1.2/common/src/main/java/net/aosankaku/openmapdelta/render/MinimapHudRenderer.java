// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.client.ClientEvents;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerRenderer;
import net.aosankaku.openmapdelta.marker.MarkerIconSizing;
import net.aosankaku.openmapdelta.marker.PlayerMarkerSizing;
import net.aosankaku.openmapdelta.util.RenderUtil;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.List;

public class MinimapHudRenderer {
    private MinimapHudRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !ClientEvents.isMinimapVisible()) return;
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof FullscreenMapScreen) return;
        if (mc.screen instanceof AbstractContainerScreen<?>) return;
        // Keep the map visible while typing; other screens still honor the hide setting.
        if (OpenMapDeltaConfig.HIDE_WHEN_SCREEN_OPEN.get()
                && mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        if (mc.gui.getDebugOverlay().showDebugScreen()) return;

        float yaw = MapRenderMath.interpolatedYaw(partialTick);
        boolean headingUp = OpenMapDeltaConfig.ROTATION_MODE.get() == OpenMapDeltaConfig.RotationMode.HEADING_UP;
        float rotation = headingUp ? (180 - yaw) : 0;
        int displaySize = MinimapSizing.displaySize(screenWidth, screenHeight, OpenMapDeltaConfig.MINIMAP_SCALE.get());
        if (displaySize <= 0) return;
        int textureSize = MinimapSizing.TEXTURE_SIZE;
        List<Marker> markers = MarkerManager.get().getMapMarkers(mc.level.dimension());
        int borderThickness = OpenMapDeltaConfig.BORDER_THICKNESS.get();
        boolean circular = OpenMapDeltaConfig.SHAPE.get() == OpenMapDeltaConfig.MinimapShape.CIRCLE;
        int renderExtent = MinimapSizing.renderExtent(borderThickness, circular, headingUp);
        int mapInset = (renderExtent - textureSize) / 2;
        float renderScale = displaySize / (float) renderExtent;
        double compassTextScale = MinimapSizing.textScale(1.0, renderScale,
                mc.getWindow().getGuiScale(), mc.font.lineHeight);
        int waypointIconSize = Math.max(2, (int) Math.round(10 * OpenMapDeltaConfig.MINIMAP_WAYPOINT_SCALE.get()));
        int playerIconSize = Math.max(2, (int) Math.round(14 * OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE.get()));
        int edgeIconSize = Math.max(waypointIconSize,
                PlayerMarkerSizing.footprintSize(playerIconSize, true));
        double markerOverhang = Math.max(
                Math.max(0, MarkerIconSizing.minimapEdgeOverhang(
                        OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get(), edgeIconSize) - mapInset) * renderScale,
                MarkerRenderer.clusterBadgeOverhang(mc, markers, textureSize, mapInset, renderScale, headingUp, yaw));
        double compassOverhang = Math.max(markerOverhang, OpenMapDeltaConfig.SHOW_COMPASS.get()
                ? Math.max(0, (6 - mapInset) * renderScale + 4 * compassTextScale * renderScale) : 0);
        double bottomCompassOverhang = Math.max(markerOverhang, OpenMapDeltaConfig.SHOW_COMPASS.get()
                ? Math.max(0, (6 - mapInset) * renderScale
                        + (mc.font.lineHeight - 4) * compassTextScale * renderScale) : 0);
        int margin = MinimapSizing.margin(screenWidth, screenHeight, displaySize,
                Math.max(compassOverhang, bottomCompassOverhang));
        int[] pos = MapRenderMath.hudPosition(displaySize, margin);
        int x = pos[0];
        int y = pos[1];
        int infoLineHeight = MinimapInfoRenderer.lineHeight(mc);
        int topReserve = OpenMapDeltaConfig.INFO_TOP.get() != net.aosankaku.openmapdelta.config.MinimapInfoType.NONE
                ? MinimapInfoLayout.topReserve(infoLineHeight, (int) Math.ceil(compassOverhang)) : 0;
        int bottomReserve = OpenMapDeltaConfig.INFO_BOTTOM.get() != net.aosankaku.openmapdelta.config.MinimapInfoType.NONE
                ? MinimapInfoLayout.bottomReserve(infoLineHeight, (int) Math.ceil(bottomCompassOverhang)) : 0;
        y = Math.max(topReserve, Math.min(y, Math.max(topReserve,
                screenHeight - MinimapInfoLayout.height(displaySize, infoLineHeight) - bottomReserve)));
        OpenMapDeltaClient.getMinimapTexture().compose(mc, textureSize,
                textureSize * renderScale * mc.getWindow().getGuiScale());

        float alpha = OpenMapDeltaConfig.TRANSPARENCY.get().floatValue();
        int tint = ARGB.white(alpha);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(renderScale, renderScale);
        graphics.pose().translate(mapInset, mapInset);

        graphics.pose().pushMatrix();
        graphics.pose().translate(textureSize / 2.0f, textureSize / 2.0f);
        if (rotation != 0) {
            graphics.pose().rotate((float) Math.toRadians(rotation));
        }
        graphics.pose().translate(-textureSize / 2.0f, -textureSize / 2.0f);

        var tex = OpenMapDeltaClient.getMinimapTexture().getTextureLocation();
        int textureWidth = OpenMapDeltaClient.getMinimapTexture().getTextureWidth();
        int textureHeight = OpenMapDeltaClient.getMinimapTexture().getTextureHeight();
        if (tex != null && textureWidth > 0 && textureHeight > 0) {
            graphics.pose().scale(textureSize / (float) textureWidth, textureSize / (float) textureHeight);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, textureWidth, textureHeight,
                    textureWidth, textureHeight, tint);
        }
        graphics.pose().popMatrix();

        drawBorder(graphics, 0, 0, textureSize);
        if (OpenMapDeltaConfig.SHOW_COMPASS.get()) {
            drawCompass(graphics, 0, 0, textureSize, headingUp ? (180 - yaw) : 0, compassTextScale);
        }

        MarkerRenderer.renderHud(graphics, mc, 0, 0, textureSize, partialTick, headingUp, yaw, renderScale, markers);

        graphics.pose().popMatrix();
        MinimapInfoRenderer.render(graphics, mc, x, y, displaySize, screenWidth, screenHeight,
                (int) Math.ceil(compassOverhang), (int) Math.ceil(bottomCompassOverhang));
    }

    public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int size) {
        int color = OpenMapDeltaConfig.BORDER_COLOR.get();
        int t = OpenMapDeltaConfig.BORDER_THICKNESS.get();
        if (t <= 0) return;
        if (OpenMapDeltaConfig.SHAPE.get() == OpenMapDeltaConfig.MinimapShape.CIRCLE) {
            if (OpenMapDeltaConfig.BORDER_STYLE.get() == OpenMapDeltaConfig.BorderStyle.DOTTED) {
                RenderUtil.drawCircleBorder(graphics, x, y, size, t, color);
            } else {
                RenderUtil.drawSolidCircleBorder(graphics, x, y, size, t, color);
            }
        } else if (OpenMapDeltaConfig.BORDER_STYLE.get() == OpenMapDeltaConfig.BorderStyle.DOTTED) {
            for (int offset = -t; offset < size + t; offset += 4) {
                int end = Math.min(size + t, offset + 2);
                graphics.fill(x + offset, y - t, x + end, y, color);
                graphics.fill(x + offset, y + size, x + end, y + size + t, color);
                graphics.fill(x - t, y + offset, x, y + end, color);
                graphics.fill(x + size, y + offset, x + size + t, y + end, color);
            }
        } else {
            graphics.fill(x - t, y - t, x + size + t, y, color);
            graphics.fill(x - t, y + size, x + size + t, y + size + t, color);
            graphics.fill(x - t, y, x, y + size, color);
            graphics.fill(x + size, y, x + size + t, y + size, color);
        }
    }

    public static void drawCompass(GuiGraphicsExtractor graphics, int x, int y, int size, float yaw, double textScale) {
        Minecraft mc = Minecraft.getInstance();
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 + 6;
        float rad = (float) Math.toRadians(-yaw);
        String[] labels = {"N", "E", "S", "W"};
        float[] angles = {0, 90, 180, 270};
        for (int i = 0; i < 4; i++) {
            float a = (float) Math.toRadians(angles[i]) + rad;
            int lx = cx + (int) (Math.sin(a) * r);
            int ly = cy - (int) (Math.cos(a) * r);
            graphics.pose().pushMatrix();
            graphics.pose().translate(lx, ly);
            graphics.pose().scale((float) textScale, (float) textScale);
            graphics.centeredText(mc.font, labels[i], 0, -4, 0xFFDDDDDD);
            graphics.pose().popMatrix();
        }
    }
}
