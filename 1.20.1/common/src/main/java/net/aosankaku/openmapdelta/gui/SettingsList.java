// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A single-column, keyboard-accessible list for map settings. */
final class SettingsList extends ContainerObjectSelectionList<SettingsList.Entry> {
    private static final int ROW_HEIGHT = 28;
    private static final int MAX_ROW_WIDTH = 400;
    private final Font font;

    SettingsList(Minecraft minecraft, Font font, int width, int height, int top, int bottom) {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
        this.font = font;
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        setRenderSelection(false);
    }

    @Override public int getRowWidth() { return Math.max(1, Math.min(MAX_ROW_WIDTH, width - 40)); }

    @Override protected int getScrollbarPosition() { return getRowLeft() + getRowWidth() + 4; }

    double savedScrollAmount() { return getScrollAmount(); }

    void addHeading(Component title) { addEntry(new HeadingEntry(title)); }

    void addWidget(AbstractWidget widget, int requestedWidth, boolean colorPreview) {
        addEntry(new WidgetEntry(widget, requestedWidth, colorPreview));
    }

    abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        @Override public List<? extends GuiEventListener> children() { return List.of(); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(); }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                                     int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {}
    }

    private final class HeadingEntry extends Entry {
        private final Component title;
        private HeadingEntry(Component title) { this.title = title; }

        @Override public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                                     int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.fill(left, top + rowHeight - 1, left + rowWidth, top + rowHeight, 0xFF505060);
            graphics.drawCenteredString(font, title, left + rowWidth / 2, top + (rowHeight - 8) / 2, 0xFFDDDDDD);
        }
    }

    private final class WidgetEntry extends Entry {
        private final AbstractWidget widget;
        private final int requestedWidth;
        private final boolean colorPreview;

        private WidgetEntry(AbstractWidget widget, int requestedWidth, boolean colorPreview) {
            this.widget = widget;
            this.requestedWidth = requestedWidth;
            this.colorPreview = colorPreview;
        }

        @Override public List<? extends GuiEventListener> children() { return List.of(widget); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(widget); }

        @Override public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                                     int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int widgetWidth = requestedWidth <= 0 ? rowWidth : Math.min(requestedWidth, rowWidth);
            int x = left + (rowWidth - widgetWidth) / 2;
            int y = top + (rowHeight - widget.getHeight()) / 2;
            widget.setWidth(widgetWidth);
            widget.setX(x);
            widget.setY(y);
            widget.render(graphics, mouseX, mouseY, partialTick);
            if (colorPreview) {
                int swatchX = x + widgetWidth + 4;
                int swatchY = y + Math.max(0, (widget.getHeight() - 16) / 2);
                graphics.fill(swatchX, swatchY, swatchX + 16, swatchY + 16, OpenMapDeltaConfig.BORDER_COLOR.get());
                graphics.renderOutline(swatchX - 1, swatchY - 1, 18, 18, 0xFFFFFFFF);
            }
        }
    }
}
