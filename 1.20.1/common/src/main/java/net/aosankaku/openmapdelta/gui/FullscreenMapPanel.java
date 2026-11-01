// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import com.mojang.math.Axis;
import net.aosankaku.openmapdelta.compat.TransitRail;
import net.aosankaku.openmapdelta.compat.TransitRailManager;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.MarkerRenderer;
import net.aosankaku.openmapdelta.render.FullscreenChunkGrid;
import net.aosankaku.openmapdelta.render.FullscreenMapRenderer;
import net.aosankaku.openmapdelta.render.FullscreenRailGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class FullscreenMapPanel extends AbstractWidget {
    private final FullscreenMapScreen parent;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public FullscreenMapPanel(int x, int y, int width, int height, FullscreenMapScreen parent) {
        super(x, y, width, height, Component.empty());
        this.parent = parent;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xE0101018);

        var texture = FullscreenMapRenderer.getTexture();
        var viewport = FullscreenMapRenderer.getComposedViewport();
        var tex = texture.getTextureLocation();
        if (tex != null && viewport != null) {
            int tw = texture.getTextureWidth();
            int th = texture.getTextureHeight();
            double bpp = parent.getBlocksPerPixel();
            float scale = (float) viewport.renderScale(bpp);
            graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
            graphics.pose().pushPose();
            try {
                graphics.pose().translate((float) viewport.drawX(getX(), parent.getPanX(), bpp, getWidth()),
                        (float) viewport.drawY(getY(), parent.getPanZ(), bpp, getHeight()), 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.blit(tex, 0, 0, 0f, 0f, tw, th, tw, th);
            } finally {
                graphics.pose().popPose();
                graphics.disableScissor();
            }
        } else {
            String loading = "Loading map...";
            int textW = mc.font.width(loading);
            graphics.drawString(mc.font, loading,
                    getX() + (getWidth() - textW) / 2,
                    getY() + getHeight() / 2 - 4,
                    0xFFAAAAAA, false);
        }

        if (OpenMapDeltaConfig.FULLSCREEN_SHOW_GRID.get()) {
            drawGrid(graphics, parent.getBlocksPerPixel());
        }
        drawTransitRails(graphics, mc);

        MarkerRenderer.renderFullscreen(graphics, mc, getX(), getY(), getWidth(), getHeight(),
                parent.getPanX(), parent.getPanZ(), parent.getBlocksPerPixel(), partialTick, mouseX, mouseY);
    }

    private void drawTransitRails(GuiGraphics graphics, Minecraft mc) {
        boolean showMtr = OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get();
        boolean showCreate = OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get();
        if (!showMtr && !showCreate) return;
        var rails = TransitRailManager.get().rails(mc.level.dimension());
        var segments = FullscreenRailGeometry.visibleSegments(rails,
                new FullscreenRailGeometry.Viewport(getX(), getY(), getWidth(), getHeight(),
                        parent.getPanX(), parent.getPanZ(), parent.getBlocksPerPixel()), showMtr, showCreate);
        if (segments.isEmpty()) return;
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            graphics.drawManaged(() -> {
                for (FullscreenRailGeometry.Segment segment : segments) drawRailSegment(graphics, segment, 3.0f, 0xD0101418);
                for (FullscreenRailGeometry.Segment segment : segments) {
                    int color = segment.source() == TransitRail.Source.MTR
                            ? FullscreenRailGeometry.MTR_COLOR : FullscreenRailGeometry.CREATE_COLOR;
                    drawRailSegment(graphics, segment, 1.5f, color);
                }
            });
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawRailSegment(GuiGraphics graphics, FullscreenRailGeometry.Segment segment,
                                        float thickness, int color) {
        double dx = segment.x2() - segment.x1();
        double dy = segment.y2() - segment.y1();
        float length = (float) Math.hypot(dx, dy);
        if (!(length > 0.0f) || !Float.isFinite(length)) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate((float) segment.x1(), (float) segment.y1(), 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(Math.atan2(dy, dx))));
            graphics.pose().scale(length, thickness, 1);
            graphics.pose().translate(0, -0.5f, 0);
            graphics.fill(0, 0, 1, 1, color);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void drawGrid(GuiGraphics graphics, double blocksPerPixel) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        FullscreenChunkGrid.Layout grid = FullscreenChunkGrid.layout(blocksPerPixel,
                Math.max(getWidth(), getHeight()), OpenMapDeltaConfig.FULLSCREEN_GRID_THICKNESS.get());
        double spacing = grid.lineSpacing();
        float thickness = grid.lineThickness();
        double firstX = grid.firstLineOffset(getWidth(), parent.getPanX());
        double firstZ = grid.firstLineOffset(getHeight(), parent.getPanZ());
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            for (int line = 0, count = grid.lineCount(getWidth(), firstX); line < count; line++) {
                double offset = firstX + line * spacing;
                drawGridLine(graphics, (float) (getX() + offset), getY(),
                        Math.min(thickness, (float) (getWidth() - offset)), getHeight());
            }
            for (int line = 0, count = grid.lineCount(getHeight(), firstZ); line < count; line++) {
                double offset = firstZ + line * spacing;
                drawGridLine(graphics, getX(), (float) (getY() + offset),
                        getWidth(), Math.min(thickness, (float) (getHeight() - offset)));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawGridLine(GuiGraphics graphics, float x, float y, float width, float height) {
        // Scaling a unit rectangle preserves fractional GUI pixel thickness.
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width, height, 1);
        graphics.fill(0, 0, 1, 1, 0x40FFFFFF);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0) return false;
        if (mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY() && mouseY < getY() + getHeight()) {
            dragging = true;
            setFocused(true);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || button != 0) return false;
        double bpp = parent.getBlocksPerPixel();
        double deltaX = mouseX - lastMouseX;
        double deltaY = mouseY - lastMouseY;
        parent.addPan(-deltaX * bpp, -deltaY * bpp);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY() && mouseY < getY() + getHeight()) {
            parent.adjustZoom(delta);
            return true;
        }
        return false;
    }

    public void cancelDrag() {
        dragging = false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
