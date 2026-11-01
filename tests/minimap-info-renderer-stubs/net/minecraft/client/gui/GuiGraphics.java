// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

public final class GuiGraphics {
    private final double guiScale;
    private final PoseStack pose = new PoseStack();
    public final List<Fill> fills = new ArrayList<>();
    public final List<Text> texts = new ArrayList<>();

    public GuiGraphics(double guiScale) { this.guiScale = guiScale; }
    public PoseStack pose() { return pose; }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        fills.add(new Fill(physicalX(x1), physicalY(y1), physicalX(x2), physicalY(y2), color));
    }

    public void drawString(Font font, String value, int x, int y, int color, boolean shadow) {
        texts.add(new Text(value, physicalX(x), physicalY(y), font.width(value) * pose.scaleX() * guiScale,
                font.lineHeight * pose.scaleY() * guiScale, color));
    }

    private double physicalX(double x) { return (pose.translateX() + x * pose.scaleX()) * guiScale; }
    private double physicalY(double y) { return (pose.translateY() + y * pose.scaleY()) * guiScale; }

    public record Fill(double x1, double y1, double x2, double y2, int color) {}
    public record Text(String value, double x, double y, double width, double height, int color) {}

    public static final class PoseStack {
        private final List<State> states = new ArrayList<>();
        private double translateX;
        private double translateY;
        private double scaleX = 1;
        private double scaleY = 1;

        public void pushPose() { states.add(new State(translateX, translateY, scaleX, scaleY)); }
        public void popPose() {
            State state = states.remove(states.size() - 1);
            translateX = state.translateX;
            translateY = state.translateY;
            scaleX = state.scaleX;
            scaleY = state.scaleY;
        }
        public void translate(double x, double y, double z) {
            translateX += x * scaleX;
            translateY += y * scaleY;
        }
        public void scale(float x, float y, float z) { scaleX *= x; scaleY *= y; }
        public double translateX() { return translateX; }
        public double translateY() { return translateY; }
        public double scaleX() { return scaleX; }
        public double scaleY() { return scaleY; }
        private record State(double translateX, double translateY, double scaleX, double scaleY) {}
    }
}
