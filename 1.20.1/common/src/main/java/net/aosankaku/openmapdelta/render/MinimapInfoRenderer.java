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
        int available = centered ? MinimapInfoLayout.centeredTextWidth(width, x, size)
                : MinimapInfoLayout.cornerTextWidth(frameWidth);
        float scale = (float) MinimapSizing.textScale(0.6, 1.0,
                mc.getWindow().getGuiScale(), mc.font.lineHeight);
        int textWidth = mc.font.width(text);
        if (textWidth * scale > available) {
            int ellipsisWidth = mc.font.width("…");
            if (ellipsisWidth * scale > available) return;
            int prefixWidth = Math.max(0, (int) Math.floor(available / scale) - ellipsisWidth);
            text = mc.font.plainSubstrByWidth(text, prefixWidth) + "…";
            textWidth = mc.font.width(text);
        }
        if (textWidth * scale > available) return;
        float drawnWidth = textWidth * scale;
        int drawnHeight = (int) Math.ceil(mc.font.lineHeight * scale);
        float left = centered ? MinimapInfoLayout.centeredTextLeft(x, size, drawnWidth)
                : slot == MinimapInfoSlot.TOP_LEFT || slot == MinimapInfoSlot.BOTTOM_LEFT
                ? frameX + 5 : frameX + frameWidth - drawnWidth - 5;
        int top = switch (slot) {
            case TOP -> y - compassOverhang - lineHeight - 3;
            case TOP_LEFT, TOP_RIGHT -> y + 6;
            case BOTTOM -> y + frameHeight + bottomCompassOverhang + 4;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> y + frameHeight - lineHeight - 5;
        };
        if (!centered) left = Math.max(3, Math.min(left, width - drawnWidth - 3));
        top = Math.max(2, Math.min(top, height - drawnHeight - 2));
        // White text and an opaque dark backing remain readable over any terrain.
        graphics.fill((int) Math.floor(left - 2), top - 1, (int) Math.ceil(left + drawnWidth + 2),
                top + drawnHeight + 1, 0xE0000000);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(mc.font, text, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }
}
