// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.aosankaku.openmapdelta.client.MinimapInfoProvider;
import net.aosankaku.openmapdelta.config.MinimapInfoSlot;
import net.aosankaku.openmapdelta.config.MinimapInfoType;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;

public final class MinimapInfoRendererCaptureTest {
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 260;
    private static int assertions;

    private MinimapInfoRendererCaptureTest() {}

    public static void main(String[] args) {
        verifyMapEdgeCorners();
        for (double guiScale : new double[]{1.0, 1.25, 1.5, 2.0, 3.0, 4.0}) {
            for (int fontHeight : new int[]{8, 9, 12, 15}) {
                verifyCornerRows(guiScale, fontHeight, false);
                verifyCornerRows(guiScale, fontHeight, true);
            }
        }
        verifyCenteredCornerColumns();
        for (double guiScale : new double[]{1.0, 1.5, 3.0, 4.0}) {
            verifyViewportClamping(guiScale, OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE);
            verifyViewportClamping(guiScale, OpenMapDeltaConfig.InfoCornerAlignment.CENTER);
        }
        verifyTinyViewportSkipsIncompleteRow();
        verifyNarrowMapSkipsCornerLabels();
        verifyCenteredRows();
        System.out.println("MinimapInfoRendererCaptureTest passed " + assertions + " assertions.");
    }

    private static void verifyMapEdgeCorners() {
        for (double guiScale : new double[]{1.0, 1.25, 1.5, 2.0, 3.0, 4.0, 8.0}) {
            for (int mapSize : new int[]{60, 90, 119, 120, 180}) {
                for (int mapX : new int[]{3, 65, SCREEN_WIDTH - mapSize - 3}) {
                    for (boolean longLabels : new boolean[]{false, true}) {
                        resetSlots();
                        configure(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.LEFT_SHORT, "L");
                        configure(MinimapInfoSlot.TOP_RIGHT, MinimapInfoType.RIGHT_SHORT, "R");
                        configure(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.LEFT_LONG,
                                longLabels ? "Day " + "W".repeat(80) : "Day 3");
                        configure(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.RIGHT_LONG,
                                longLabels ? "12:17" + "W".repeat(80) : "12:17");
                        Minecraft mc = new Minecraft(new Font(9), guiScale);
                        GuiGraphics graphics = new GuiGraphics(guiScale);
                        MinimapInfoRenderer.render(graphics, mc, mapX, 20, mapSize,
                                SCREEN_WIDTH, SCREEN_HEIGHT, 0, 0);
                        String context = "MAP_EDGE scale=" + guiScale + ", size=" + mapSize
                                + ", x=" + mapX + ", long=" + longLabels;
                        equal(4, graphics.fills.size(), context + " draws four backings");
                        equal(4, graphics.texts.size(), context + " draws four labels");
                        GuiGraphics.Fill topLeft = graphics.fills.get(0), topRight = graphics.fills.get(1);
                        GuiGraphics.Fill bottomRight = graphics.fills.get(2), bottomLeft = graphics.fills.get(3);
                        double mapLeft = Math.round(mapX * guiScale);
                        double mapRight = Math.round((mapX + mapSize) * guiScale);
                        double padding = Math.max(1, Math.round(2 * guiScale));
                        double inset = Math.round(5 * guiScale) - padding;
                        close(mapLeft + inset, bottomLeft.x1(), 0.001,
                                context + " left backing anchors to actual map edge");
                        close(mapRight - inset, bottomRight.x2(), 0.001,
                                context + " right backing anchors to actual map edge");
                        close(bottomLeft.x1() - mapLeft, mapRight - bottomRight.x2(), 0.001,
                                context + " lower backings have symmetric map insets");
                        close(topLeft.x1(), bottomLeft.x1(), 0.001, context + " left slots share map edge");
                        close(topRight.x2(), bottomRight.x2(), 0.001, context + " right slots share map edge");
                        close(bottomLeft.y1(), bottomRight.y1(), 0.001, context + " lower slots share row");
                        double center = (mapLeft + mapRight) / 2;
                        check(bottomLeft.x2() <= center && bottomRight.x1() >= center,
                                context + " lower backings fit within their map halves");
                        for (GuiGraphics.Text text : graphics.texts) {
                            check(text.x() >= mapLeft && text.x() + text.width() <= mapRight + 1,
                                    context + " text fits within map width");
                            pixel(text.x(), context + " text stays on physical pixel grid");
                        }
                        if (longLabels) {
                            check(graphics.texts.get(2).value().endsWith("…"), context + " truncates right label");
                            check(graphics.texts.get(3).value().endsWith("…"), context + " truncates left label");
                        } else if (guiScale == 8.0) {
                            equal("12:17", graphics.texts.get(2).value(), context + " keeps the clock readable");
                            equal("Day 3", graphics.texts.get(3).value(), context + " keeps the day readable");
                        }
                    }
                }
            }
        }
    }

    private static void verifyCornerRows(double guiScale, int fontHeight, boolean changedWidths) {
        resetSlots();
        String leftTop = changedWidths ? "L" + "W".repeat(80) : "L";
        String rightTop = changedWidths ? "R" : "R" + "W".repeat(12);
        String leftBottom = changedWidths ? "B" : "B" + "M".repeat(36);
        String rightBottom = changedWidths ? "Q" + "M".repeat(80) : "Q";
        configure(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.LEFT_SHORT, leftTop);
        configure(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.LEFT_LONG, leftBottom);
        configure(MinimapInfoSlot.TOP_RIGHT, MinimapInfoType.RIGHT_SHORT, rightTop);
        configure(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.RIGHT_LONG, rightBottom);

        Minecraft mc = new Minecraft(new Font(fontHeight), guiScale);
        GuiGraphics graphics = new GuiGraphics(guiScale);
        int mapX = 65, mapY = 20, mapSize = 180;
        MinimapInfoRenderer.render(graphics, mc, mapX, mapY, mapSize, SCREEN_WIDTH, SCREEN_HEIGHT, 0, 0);
        equal(4, graphics.fills.size(), context(guiScale, fontHeight) + " draws four corner backings");
        equal(4, graphics.texts.size(), context(guiScale, fontHeight) + " draws four corner labels");

        for (int i = 0; i < graphics.texts.size(); i++) {
            GuiGraphics.Fill fill = graphics.fills.get(i);
            GuiGraphics.Text text = graphics.texts.get(i);
            pixel(fill.x1(), context(guiScale, fontHeight) + " backing left edge is on the physical pixel grid");
            pixel(fill.y1(), context(guiScale, fontHeight) + " backing top edge is on the physical pixel grid");
            pixel(fill.x2(), context(guiScale, fontHeight) + " backing right edge is on the physical pixel grid");
            pixel(fill.y2(), context(guiScale, fontHeight) + " backing bottom edge is on the physical pixel grid");
            pixel(text.x(), context(guiScale, fontHeight) + " text origin is on the physical pixel grid");
            pixel(text.y(), context(guiScale, fontHeight) + " text baseline origin is on the physical pixel grid");
            check(text.height() >= MinimapSizing.MIN_TEXT_PIXEL_HEIGHT - 0.01,
                    context(guiScale, fontHeight) + " preserves minimum physical text height");
            check(text.x() >= -0.01 && text.y() >= -0.01
                            && fill.x1() >= -0.01 && fill.y1() >= -0.01
                            && fill.x2() <= SCREEN_WIDTH * guiScale + 0.01
                            && fill.y2() <= SCREEN_HEIGHT * guiScale + 0.01,
                    context(guiScale, fontHeight) + " clamps text and backing inside viewport");
            close((fill.y1() + fill.y2()) / 2, text.y() + text.height() / 2, 1.0,
                    context(guiScale, fontHeight) + " centers text vertically within its backing row");
        }

        // Enum order is TOP, TOP_LEFT, TOP_RIGHT, BOTTOM, BOTTOM_RIGHT, BOTTOM_LEFT.
        GuiGraphics.Fill topLeft = graphics.fills.get(0), topRight = graphics.fills.get(1);
        GuiGraphics.Fill bottomRight = graphics.fills.get(2), bottomLeft = graphics.fills.get(3);
        GuiGraphics.Text topLeftText = graphics.texts.get(0), topRightText = graphics.texts.get(1);
        GuiGraphics.Text bottomRightText = graphics.texts.get(2), bottomLeftText = graphics.texts.get(3);
        int edgeFrameX = MinimapInfoLayout.left(SCREEN_WIDTH, mapX, mapSize);
        int edgeFrameWidth = MinimapInfoLayout.width(SCREEN_WIDTH, mapSize);
        close(Math.round(edgeFrameX * guiScale) + Math.round(5 * guiScale), topLeftText.x(), 1.0,
                "MAP_EDGE default keeps the left text inset on the frame edge");
        close(Math.round((edgeFrameX + edgeFrameWidth) * guiScale) - Math.round(5 * guiScale),
                topRightText.x() + topRightText.width(), 1.0,
                "MAP_EDGE default keeps the right text inset on the frame edge");
        close(topLeft.y1(), topRight.y1(), 0.001, context(guiScale, fontHeight) + " upper corners share row top");
        close(topLeft.y2(), topRight.y2(), 0.001, context(guiScale, fontHeight) + " upper corners share row bottom");
        close(bottomLeft.y1(), bottomRight.y1(), 0.001, context(guiScale, fontHeight) + " lower corners share row top");
        close(bottomLeft.y2(), bottomRight.y2(), 0.001, context(guiScale, fontHeight) + " lower corners share row bottom");
        for (GuiGraphics.Fill fill : graphics.fills) {
            close(topLeft.y2() - topLeft.y1(), fill.y2() - fill.y1(), 0.001,
                    context(guiScale, fontHeight) + " corners share row height");
        }
        double frameTop = Math.round(mapY * guiScale);
        int frameHeight = MinimapInfoLayout.height(mapSize, MinimapInfoRenderer.lineHeight(mc));
        double frameBottom = Math.round((mapY + frameHeight) * guiScale);
        close(topLeft.y1() - frameTop, frameBottom - bottomLeft.y2(), 0.001,
                context(guiScale, fontHeight) + " upper and lower rows have equal frame insets");

        close(topLeft.x1(), bottomLeft.x1(), 0.001, "left backing edges stay anchored when text width changes");
        close(topRight.x2(), bottomRight.x2(), 0.001, "right backing edges stay anchored when text width changes");
        close(topLeftText.x(), bottomLeftText.x(), 0.001,
                "left text origins stay anchored when text width changes");
        close(topRightText.x() + topRightText.width(),
                bottomRightText.x() + bottomRightText.width(), 1.0,
                "right text remains right aligned as text width changes");

        if (changedWidths) {
            check(topLeftText.value().endsWith("…"), "long left text is ellipsized");
            check(bottomRightText.value().endsWith("…"), "long right text is ellipsized");
        }
    }

    private static void verifyCenteredCornerColumns() {
        for (double guiScale : new double[]{1.0, 1.25, 1.5, 2.0, 3.0, 4.0}) {
            for (int fontHeight : new int[]{8, 11, 15}) {
                resetSlots();
                OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.CENTER);
                configure(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.LEFT_SHORT, "L");
                configure(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.LEFT_LONG, "B-short");
                configure(MinimapInfoSlot.TOP_RIGHT, MinimapInfoType.RIGHT_SHORT, "R");
                configure(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.RIGHT_LONG, "Q-short");
                Minecraft mc = new Minecraft(new Font(fontHeight), guiScale);
                GuiGraphics shortGraphics = new GuiGraphics(guiScale);
                int mapX = 65, mapY = 20, mapSize = 60;
                MinimapInfoRenderer.render(shortGraphics, mc, mapX, mapY, mapSize,
                        SCREEN_WIDTH, SCREEN_HEIGHT, 0, 0);
                assertColumnCenters(shortGraphics, guiScale, mapX, mapSize,
                        "short labels, scale=" + guiScale + ", font=" + fontHeight);

                configure(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.LEFT_SHORT, "L" + "W".repeat(90));
                configure(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.LEFT_LONG, "B" + "M".repeat(55));
                configure(MinimapInfoSlot.TOP_RIGHT, MinimapInfoType.RIGHT_SHORT, "R" + "M".repeat(70));
                configure(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.RIGHT_LONG, "Q" + "W".repeat(95));
                GuiGraphics longGraphics = new GuiGraphics(guiScale);
                MinimapInfoRenderer.render(longGraphics, mc, mapX, mapY, mapSize,
                        SCREEN_WIDTH, SCREEN_HEIGHT, 0, 0);
                assertColumnCenters(longGraphics, guiScale, mapX, mapSize,
                        "long labels, scale=" + guiScale + ", font=" + fontHeight);
                for (GuiGraphics.Text text : longGraphics.texts) {
                    check(text.value().endsWith("…"), "centered corner labels remain ellipsized");
                }
            }
        }
    }

    private static void assertColumnCenters(GuiGraphics graphics, double guiScale, int mapX, int mapSize,
                                            String context) {
        equal(4, graphics.texts.size(), context + " draws four corner labels");
        int frameX = MinimapInfoLayout.left(SCREEN_WIDTH, mapX, mapSize);
        int frameWidth = MinimapInfoLayout.width(SCREEN_WIDTH, mapSize);
        double leftColumnCenter = (frameX + frameWidth / 4.0) * guiScale;
        double rightColumnCenter = (frameX + frameWidth * 3.0 / 4.0) * guiScale;
        GuiGraphics.Text topLeft = graphics.texts.get(0), topRight = graphics.texts.get(1);
        GuiGraphics.Text bottomRight = graphics.texts.get(2), bottomLeft = graphics.texts.get(3);
        for (GuiGraphics.Text text : graphics.texts) pixel(text.x(), context + " text origins stay pixel aligned");
        close(leftColumnCenter, topLeft.x() + topLeft.width() / 2, 1.0, context + " upper-left uses quarter column");
        close(leftColumnCenter, bottomLeft.x() + bottomLeft.width() / 2, 1.0, context + " lower-left shares quarter column");
        close(rightColumnCenter, topRight.x() + topRight.width() / 2, 1.0, context + " upper-right uses three-quarter column");
        close(rightColumnCenter, bottomRight.x() + bottomRight.width() / 2, 1.0, context + " lower-right shares three-quarter column");
    }

    private static void verifyViewportClamping(double guiScale,
                                               OpenMapDeltaConfig.InfoCornerAlignment alignment) {
        resetSlots();
        OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(alignment);
        configure(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.LEFT_SHORT, "L" + "W".repeat(80));
        configure(MinimapInfoSlot.TOP_RIGHT, MinimapInfoType.RIGHT_SHORT, "R" + "M".repeat(80));
        configure(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.LEFT_LONG, "B" + "M".repeat(80));
        configure(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.RIGHT_LONG, "Q" + "W".repeat(80));
        Minecraft mc = new Minecraft(new Font(9), guiScale);
        GuiGraphics graphics = new GuiGraphics(guiScale);
        int viewportWidth = 96, viewportHeight = 96;
        MinimapInfoRenderer.render(graphics, mc, 92, 88, 60, viewportWidth, viewportHeight, 0, 0);
        String context = "small viewport, alignment=" + alignment + ", scale=" + guiScale;
        equal(4, graphics.fills.size(), context + " still draws all corner backing rows");
        equal(4, graphics.texts.size(), context + " still draws all corner labels");
        for (int i = 0; i < graphics.texts.size(); i++) {
            GuiGraphics.Fill fill = graphics.fills.get(i);
            GuiGraphics.Text text = graphics.texts.get(i);
            check(fill.x1() >= -0.01 && fill.y1() >= -0.01 && fill.x2() <= viewportWidth * guiScale + 0.01
                            && fill.y2() <= viewportHeight * guiScale + 0.01,
                    context + " clamps complete backing rows: " + fill);
            check(text.x() >= 0 && text.y() >= 0 && text.x() + text.width() <= viewportWidth * guiScale + 1
                            && text.y() + text.height() <= viewportHeight * guiScale + 1,
                    context + " clamps text");
            pixel(fill.x1(), context + " keeps backing left edge on physical pixels");
            pixel(fill.y1(), context + " keeps backing top edge on physical pixels");
            pixel(fill.x2(), context + " keeps backing right edge on physical pixels");
            pixel(fill.y2(), context + " keeps backing bottom edge on physical pixels");
            if (i == 0 || i == 1 || i == 2 || i == 3) {
                check(text.value().endsWith("…"), context + " ellipsizes long corner text");
            }
        }
        GuiGraphics.Fill topLeft = graphics.fills.get(0), topRight = graphics.fills.get(1);
        GuiGraphics.Fill bottomRight = graphics.fills.get(2), bottomLeft = graphics.fills.get(3);
        close(topLeft.y1(), topRight.y1(), 0.001, context + " upper backings share a row");
        close(bottomLeft.y1(), bottomRight.y1(), 0.001, context + " lower backings share a row");
        close(topLeft.y2() - topLeft.y1(), bottomLeft.y2() - bottomLeft.y1(), 0.001,
                context + " upper and lower backing rows have equal height");
    }

    private static void verifyTinyViewportSkipsIncompleteRow() {
        resetSlots();
        configure(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.LEFT_SHORT, "L");
        double guiScale = 1.0;
        Minecraft mc = new Minecraft(new Font(8), guiScale);
        GuiGraphics graphics = new GuiGraphics(guiScale);
        int height = MinimapInfoRenderer.lineHeight(mc);
        MinimapInfoRenderer.render(graphics, mc, 4, 0, 40, 100, height, 0, 0);
        equal(0, graphics.fills.size(), "viewport too short for a padded row skips its backing");
        equal(0, graphics.texts.size(), "viewport too short for a padded row skips its label");
    }

    private static void verifyNarrowMapSkipsCornerLabels() {
        resetSlots();
        configure(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.LEFT_LONG, "Day 3");
        configure(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.RIGHT_LONG, "12:17");
        for (int mapSize : new int[]{1, 8, 24, 40}) {
            Minecraft mc = new Minecraft(new Font(9), 1.0);
            GuiGraphics graphics = new GuiGraphics(1.0);
            MinimapInfoRenderer.render(graphics, mc, 65, 20, mapSize,
                    SCREEN_WIDTH, SCREEN_HEIGHT, 0, 0);
            equal(0, graphics.fills.size(), "map width=" + mapSize + " cannot fit padded corner ellipsis");
            equal(0, graphics.texts.size(), "map width=" + mapSize + " skips overflowing corner labels");
        }
    }

    private static void verifyCenteredRows() {
        double guiScale = 1.5;
        Minecraft mc = new Minecraft(new Font(11), guiScale);
        int mapX = 42, mapY = 30, size = 60;
        GuiGraphics[] captures = new GuiGraphics[2];
        OpenMapDeltaConfig.InfoCornerAlignment[] alignments = {
                OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE, OpenMapDeltaConfig.InfoCornerAlignment.CENTER
        };
        for (int mode = 0; mode < captures.length; mode++) {
            resetSlots();
            OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(alignments[mode]);
            configure(MinimapInfoSlot.TOP, MinimapInfoType.TOP_LINE, "center top");
            configure(MinimapInfoSlot.BOTTOM, MinimapInfoType.BOTTOM_LINE, "center bottom");
            captures[mode] = new GuiGraphics(guiScale);
            MinimapInfoRenderer.render(captures[mode], mc, mapX, mapY, size,
                    SCREEN_WIDTH, SCREEN_HEIGHT, 2, 3);
        }
        GuiGraphics graphics = captures[0];
        equal(2, graphics.fills.size(), "centered top/bottom keep their own rows");
        equal(2, graphics.texts.size(), "centered top/bottom keep their labels");
        for (int i = 0; i < 2; i++) {
            GuiGraphics.Fill fill = graphics.fills.get(i);
            GuiGraphics.Text text = graphics.texts.get(i);
            pixel(fill.x1(), "centered backing left edge is pixel aligned");
            pixel(fill.y1(), "centered backing top edge is pixel aligned");
            pixel(fill.x2(), "centered backing right edge is pixel aligned");
            pixel(fill.y2(), "centered backing bottom edge is pixel aligned");
            pixel(text.x(), "centered label origin is pixel aligned");
            close((mapX + size / 2.0) * guiScale, text.x() + text.width() / 2, 1.0,
                    "TOP/BOTTOM labels remain centered over map");
        }
        int frameHeight = MinimapInfoLayout.height(size, MinimapInfoRenderer.lineHeight(mc));
        check(graphics.fills.get(0).y2() < mapY * guiScale, "TOP remains above minimap frame");
        check(graphics.fills.get(1).y1() > (mapY + frameHeight) * guiScale,
                "BOTTOM remains below minimap frame");
        for (int i = 0; i < graphics.texts.size(); i++) {
            close(graphics.texts.get(i).x(), captures[1].texts.get(i).x(), 0.001,
                    "corner alignment option does not move TOP/BOTTOM text");
            close(graphics.texts.get(i).y(), captures[1].texts.get(i).y(), 0.001,
                    "corner alignment option does not move TOP/BOTTOM rows");
        }
    }

    private static void resetSlots() {
        OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE);
        for (MinimapInfoSlot slot : MinimapInfoSlot.values()) {
            OpenMapDeltaConfig.infoSlot(slot).set(MinimapInfoType.NONE);
        }
        OpenMapDeltaConfig.SHOW_COORDINATES.set(true);
    }

    private static void configure(MinimapInfoSlot slot, MinimapInfoType type, String text) {
        OpenMapDeltaConfig.infoSlot(slot).set(type);
        MinimapInfoProvider.setText(type, text);
    }

    private static String context(double scale, int lineHeight) { return "scale=" + scale + ", font=" + lineHeight; }
    private static void pixel(double value, String message) { close(Math.rint(value), value, 0.0001, message); }
    private static void close(double expected, double actual, double tolerance, String message) {
        assertions++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
