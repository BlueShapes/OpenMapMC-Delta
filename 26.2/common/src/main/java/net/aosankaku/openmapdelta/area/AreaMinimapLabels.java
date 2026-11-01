// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.area;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.render.MinimapSizing;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;

/** Optional upright place names; minimap terrain and player markers retain their existing transforms. */
public final class AreaMinimapLabels {
    private record Box(double x, double y, double width, double height) {
        boolean intersects(Box other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }
    private AreaMinimapLabels() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft mc, int size,
                              boolean headingUp, float yaw, float parentScale) {
        if (!AreaManager.enabled() || !OpenMapDeltaConfig.MINIMAP_AREAS_ENABLED.get()
                || OpenMapDeltaClient.getSelectedMinimapLayer() != OpenMapDeltaClient.SURFACE_LAYER
                || mc.player == null || mc.level == null) return;
        int stride = RuntimeClientState.getZoomBlocksPerPixel();
        boolean circular = OpenMapDeltaConfig.SHAPE.get() == OpenMapDeltaConfig.MinimapShape.CIRCLE;
        double scale = MinimapSizing.textScale(0.65, parentScale, mc.getWindow().getGuiScale(), mc.font.lineHeight);
        var areas = new ArrayList<>(AreaManager.areas(mc.level.dimension().identifier().toString()));
        areas.sort(Comparator.comparingDouble((MapArea area) -> area.style().priority() * 1000.0 + rank(area.kind())).reversed());
        var occupied = new ArrayList<Box>();
        occupied.add(new Box(size / 2.0 - 14, size / 2.0 - 14, 28, 28));
        int count = 0;
        for (MapArea area : areas) {
            if (!area.style().visible() || !area.style().labels()
                    || stride < area.style().minBpp() || stride > area.style().maxBpp()) continue;
            var point = AreaMinimapProjection.project(area.label().x(), area.label().z(),
                    mc.player.getX(), mc.player.getZ(), stride, headingUp ? 180 - yaw : 0, size);
            int textWidth = mc.font.width(area.name());
            double labelWidth = (textWidth + 4) * scale, labelHeight = (mc.font.lineHeight + 2) * scale;
            if (!AreaMinimapProjection.fits(point.x(), point.y(), labelWidth / 2, labelHeight / 2, size, circular)) continue;
            Box box = new Box(point.x() - labelWidth / 2, point.y() - labelHeight / 2, labelWidth, labelHeight);
            if (occupied.stream().anyMatch(box::intersects)) continue;
            occupied.add(box);
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate((float) (point.x() - textWidth * scale / 2),
                        (float) (point.y() - mc.font.lineHeight * scale / 2));
                graphics.pose().scale((float) scale, (float) scale);
                graphics.fill(-2, -1, textWidth + 2, mc.font.lineHeight + 1, 0xC0101418);
                graphics.text(mc.font, area.name(), 0, 0, 0xFF000000 | area.style().color(), true);
            } finally { graphics.pose().popMatrix(); }
            if (++count >= 5) break;
        }
    }

    private static int rank(MapArea.Kind kind) {
        return switch (kind) {
            case COUNTRY -> 100;
            case SEA -> 80;
            case LAND -> 70;
            case REGION -> 60;
            case RIVER -> 40;
            case POND -> 20;
            case OTHER -> 0;
        };
    }
}
