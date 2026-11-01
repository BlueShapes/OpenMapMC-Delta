// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Keeps Controllable's button image font intact through clipping and GUI extraction. */
public final class ControllerHintRenderer {
    private static final int ROW_HEIGHT = 14;
    private ControllerHintRenderer() {}

    public static int visibleRows(int count, int width, int height) {
        return width <= 8 ? 0 : Math.min(count, Math.max(0, (height - 24) / ROW_HEIGHT));
    }

    public static int draw(GuiGraphicsExtractor graphics, Font font, List<Component> hints,
                           int left, int top, int width, int height) {
        int rows = visibleRows(hints.size(), width, height);
        int x = left + 4;
        int y = top + 4;
        for (int row = 0; row < rows; row++) {
            var text = Language.getInstance().getVisualOrder(font.substrByWidth(hints.get(row), width - 8));
            graphics.fill(x - 2, y - 1, x + font.width(text) + 2, y + 12, 0xA0000000);
            graphics.text(font, text, x, y, 0xFFCCCCCC, true);
            y += ROW_HEIGHT;
        }
        return rows;
    }
}
