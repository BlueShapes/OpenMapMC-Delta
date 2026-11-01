// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.client.MinimapInfoProvider;
import net.aosankaku.openmapdelta.config.MinimapInfoSlot;
import net.aosankaku.openmapdelta.config.MinimapInfoType;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Information stays in GUI space so changing map size does not create enormous text. */
public final class MinimapInfoRenderer {
    public static final int LINE_HEIGHT = 12;
    private MinimapInfoRenderer() {}

    public static int lineHeight(Minecraft mc) {
        return MinimapInfoLayout.lineHeight(mc.font.lineHeight, mc.getWindow().getGuiScale());
    }

    public static void render(GuiGraphics graphics, Minecraft mc, int x, int y, int size,
                              int screenWidth, int screenHeight, int compassOverhang, int bottomCompassOverhang) {
        for (MinimapInfoSlot slot : MinimapInfoSlot.values()) {
            MinimapInfoType type = OpenMapDeltaConfig.infoSlot(slot).get();
            if (type == MinimapInfoType.NONE || !slot.accepts(type)) continue;
            if (type.isCoordinates() && !OpenMapDeltaConfig.SHOW_COORDINATES.get()) continue;
            draw(graphics, mc, MinimapInfoProvider.text(mc, type), slot, x, y, size,
                    screenWidth, screenHeight, compassOverhang, bottomCompassOverhang);
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft mc, String text, MinimapInfoSlot slot,
                             int x, int y, int size, int width, int height, int compassOverhang, int bottomCompassOverhang) {
        int lineHeight = lineHeight(mc);
        if (text == null || text.isEmpty() || width < 12 || height < lineHeight) return;
        boolean centered = slot == MinimapInfoSlot.TOP || slot == MinimapInfoSlot.BOTTOM;
        int frameWidth = MinimapInfoLayout.width(width, size);
        int frameHeight = MinimapInfoLayout.height(size, lineHeight);
        int frameX = MinimapInfoLayout.left(width, x, size);
        boolean centerCorners = OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get()
                == OpenMapDeltaConfig.InfoCornerAlignment.CENTER;
        int available = centered ? MinimapInfoLayout.centeredTextWidth(width, x, size)
                : MinimapInfoLayout.cornerTextWidth(centerCorners ? frameWidth : size);
        double guiScale = mc.getWindow().getGuiScale();
        float localTextScale = (float) MinimapSizing.textScale(0.6, 1.0, guiScale, mc.font.lineHeight);
        int textWidth = mc.font.width(text);
        if (textWidth * localTextScale > available) {
            int ellipsisWidth = mc.font.width("…");
            if (ellipsisWidth * localTextScale > available) return;
            int prefixWidth = Math.max(0, (int) Math.floor(available / localTextScale) - ellipsisWidth);
            text = mc.font.plainSubstrByWidth(text, prefixWidth) + "…";
            textWidth = mc.font.width(text);
        }
        if (textWidth * localTextScale > available) return;
        MinimapInfoLayout.PhysicalPlacement placement = MinimapInfoLayout.physicalPlacement(
                slot, centerCorners,
                width, height, x, y, size, frameX, frameWidth, frameHeight,
                mc.font.lineHeight, compassOverhang, bottomCompassOverhang, textWidth, guiScale, localTextScale);
        if (placement == null) return;

        // White text and an opaque dark backing remain readable over any terrain.
        graphics.pose().pushPose();
        graphics.pose().scale((float) (1.0 / guiScale), (float) (1.0 / guiScale), 1);
        graphics.fill(placement.boxLeft(), placement.boxTop(), placement.boxRight(), placement.boxBottom(), 0xE0000000);
        graphics.pose().pushPose();
        graphics.pose().translate(placement.textX(), placement.textY(), 0);
        graphics.pose().scale((float) placement.textScale(), (float) placement.textScale(), 1);
        graphics.drawString(mc.font, text, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
        graphics.pose().popPose();
    }
}
