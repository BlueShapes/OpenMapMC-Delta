// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Shares the scaled label transform between fullscreen drawing and pointer selection. */
public final class FullscreenMapLabelGeometry {
    public static final double DEFAULT_SCALE = 0.75;

    private FullscreenMapLabelGeometry() {}

    public static Layout place(int centerX, int centerY, int iconSize, int textWidth, int lineHeight,
                               double requestedScale) {
        float scale = safeScale(requestedScale);
        // Keep the whole label background two GUI pixels above the icon at every text scale.
        float y = centerY - iconSize / 2 - 2 - (lineHeight + 2) * scale;
        return new Layout(centerX, y, scale, -textWidth / 2, textWidth, lineHeight);
    }

    public static float safeScale(double scale) {
        return (float) (Double.isFinite(scale) && scale >= 0.25 && scale <= 2.0 ? scale : DEFAULT_SCALE);
    }

    public record Layout(float x, float y, float scale, int textX, int textWidth, int lineHeight) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x + (textX - 2) * scale && mouseX < x + (textX + textWidth + 2) * scale
                    && mouseY >= y - 2 * scale && mouseY < y + (lineHeight + 2) * scale;
        }
    }
}
