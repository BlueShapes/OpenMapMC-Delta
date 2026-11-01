// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import net.aosankaku.openmapdelta.gui.FullscreenMapPanel;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.render.FullscreenRailGeometry;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Renders user-owned area geometry over the fullscreen terrain and below navigation markers. */
public final class AreaRenderer {
    private static final int MAX_LABELS = 64;
    private static final int MAX_CACHED_KEYS = 64;
    private static final int MAX_CACHED_SPANS = 45_000;
    private static final int LABEL_PLAYER_PADDING = 12;
    private static int cachedSpanCount;
    private record AreaKey(MapArea.Scope scope, UUID id) {}
    private static final LinkedHashMap<CacheKey, List<AreaRenderGeometry.Span>> FILL_CACHE =
            new LinkedHashMap<>(16, 0.75f, true);

    private AreaRenderer() {}

    /** Called between terrain and the ordinary grid and rail overlays. */
    public static void renderFills(GuiGraphicsExtractor graphics, FullscreenMapScreen screen,
                                   FullscreenMapPanel panel, List<MapArea> areas,
                                   List<MapArea> preview) {
        if (!shouldRender(screen, panel, areas) || areas == null) return;
        AreaRenderGeometry.Projection projection = projection(screen, panel);
        AreaRenderGeometry.Clip clip = clip(panel);
        List<MapArea> renderAreas = mergeAreas(areas, preview);
        graphics.enableScissor(clip.x(), clip.y(), clip.x() + clip.width(), clip.y() + clip.height());
        try {
            for (MapArea area : renderAreas) {
                if (!renderable(area, screen, projection) || !area.style().fill()
                        || area.geometry().shape() != MapArea.Shape.POLYGON) continue;
                List<AreaRenderGeometry.Span> spans = spans(area, projection, clip);
                int fill = (area.style().color() & 0x00FFFFFF) | 0x30000000;
                for (AreaRenderGeometry.Span span : spans) {
                    graphics.fill(span.xStart(), span.y(), span.xEnd(), span.y() + 1, fill);
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** Called after grid and rails so paths and names stay clear above the terrain. */
    public static void renderDetails(GuiGraphicsExtractor graphics, FullscreenMapScreen screen,
                                     FullscreenMapPanel panel, List<MapArea> areas,
                                     List<MapArea> preview) {
        if (!shouldRender(screen, panel, areas) || areas == null) return;
        AreaRenderGeometry.Projection projection = projection(screen, panel);
        AreaRenderGeometry.Clip clip = clip(panel);
        List<MapArea> renderAreas = mergeAreas(areas, preview);
        var segments = visibleSegments(renderAreas, projection, clip);

        graphics.enableScissor(clip.x(), clip.y(), clip.x() + clip.width(), clip.y() + clip.height());
        try {
            for (ScreenSegment segment : segments) {
                drawSegment(graphics, segment.x1(), segment.y1(), segment.x2(), segment.y2(), 2.0f,
                        0xC0101418);
                drawSegment(graphics, segment.x1(), segment.y1(), segment.x2(), segment.y2(), 1.0f,
                        segment.color());
            }
            renderLabels(graphics, screen, panel, renderAreas, projection, clip);
        } finally {
            graphics.disableScissor();
        }
    }

    public static void clearCache() {
        FILL_CACHE.clear();
        cachedSpanCount = 0;
    }

    private static List<MapArea> mergeAreas(List<MapArea> areas, List<MapArea> preview) {
        LinkedHashMap<AreaKey, MapArea> merged = new LinkedHashMap<>();
        for (MapArea area : areas) if (area != null) merged.put(new AreaKey(area.scope(), area.id()), area);
        if (preview != null) for (MapArea area : preview) if (area != null) merged.put(new AreaKey(area.scope(), area.id()), area);
        return List.copyOf(merged.values());
    }

    private static boolean shouldRender(FullscreenMapScreen screen, FullscreenMapPanel panel, List<MapArea> areas) {
        return panel != null && panel.getWidth() > 0 && panel.getHeight() > 0 && areas != null
                && AreaManager.enabled() && screen != null
                && OpenMapDeltaClient.getSelectedMapLayer() == OpenMapDeltaClient.SURFACE_LAYER;
    }

    private static boolean renderable(MapArea area, FullscreenMapScreen screen,
                                      AreaRenderGeometry.Projection projection) {
        return area != null && screen != null && area.style().visible()
                && Objects.equals(area.dimension(), AreaManager.dimension())
                && projection.blocksPerPixel() >= area.style().minBpp()
                && projection.blocksPerPixel() <= area.style().maxBpp()
                && (area.scope() != MapArea.Scope.SHARED || AreaManager.available(MapArea.Scope.SHARED));
    }

    private static List<AreaRenderGeometry.Span> spans(MapArea area,
                                                       AreaRenderGeometry.Projection projection,
                                                       AreaRenderGeometry.Clip clip) {
        CacheKey key = new CacheKey(area.id(), area.scope(), area.geometry(), projection, clip);
        List<AreaRenderGeometry.Span> cached = FILL_CACHE.get(key);
        if (cached != null) return cached;
        List<AreaRenderGeometry.Span> computed = AreaRenderGeometry.scanlines(area.geometry(), projection, clip);
        if (computed.size() <= MAX_CACHED_SPANS) {
            while (!FILL_CACHE.isEmpty() && (FILL_CACHE.size() >= MAX_CACHED_KEYS
                    || cachedSpanCount + computed.size() > MAX_CACHED_SPANS)) {
                Map.Entry<CacheKey, List<AreaRenderGeometry.Span>> eldest = FILL_CACHE.entrySet().iterator().next();
                cachedSpanCount -= eldest.getValue().size();
                FILL_CACHE.remove(eldest.getKey());
            }
            FILL_CACHE.put(key, computed);
            cachedSpanCount += computed.size();
        }
        return computed;
    }

    private static AreaRenderGeometry.Projection projection(FullscreenMapScreen screen, FullscreenMapPanel panel) {
        return new AreaRenderGeometry.Projection(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(),
                screen.getPanX(), screen.getPanZ(), screen.getBlocksPerPixel());
    }

    private static AreaRenderGeometry.Clip clip(FullscreenMapPanel panel) {
        return new AreaRenderGeometry.Clip(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
    }

    private static List<ScreenSegment> visibleSegments(List<MapArea> areas,
                                                        AreaRenderGeometry.Projection projection,
                                                        AreaRenderGeometry.Clip clip) {
        ArrayList<ScreenSegment> output = new ArrayList<>();
        double left = clip.x() - 2.0;
        double top = clip.y() - 2.0;
        double right = clip.x() + (double) clip.width() + 2.0;
        double bottom = clip.y() + (double) clip.height() + 2.0;
        for (MapArea area : areas) {
            if (output.size() >= FullscreenRailGeometry.MAX_VISIBLE_SEGMENTS
                    || !area.style().visible() || !area.style().border()
                    || !area.dimension().equals(AreaManager.dimension())
                    || projection.blocksPerPixel() < area.style().minBpp()
                    || projection.blocksPerPixel() > area.style().maxBpp()
                    || (area.scope() == MapArea.Scope.SHARED && !AreaManager.available(MapArea.Scope.SHARED))) continue;
            MapArea.Geometry geometry = area.geometry();
            if (geometry.shape() == MapArea.Shape.LINE) {
                for (List<MapArea.Point> path : geometry.paths()) {
                    for (int i = 1; i < path.size() && output.size() < FullscreenRailGeometry.MAX_VISIBLE_SEGMENTS; i++) {
                        MapArea.Point first = path.get(i - 1);
                        MapArea.Point second = path.get(i);
                        addClippedSegment(output, projection.screenX(first.x()), projection.screenY(first.z()),
                                projection.screenX(second.x()), projection.screenY(second.z()),
                                left, top, right, bottom, area.style().color());
                    }
                }
            } else if (geometry.shape() == MapArea.Shape.POLYGON) {
                for (MapArea.Polygon polygon : geometry.polygons()) {
                    if (output.size() >= FullscreenRailGeometry.MAX_VISIBLE_SEGMENTS) break;
                    addRing(output, polygon.outer(), projection, left, top, right, bottom, area.style().color());
                    for (List<MapArea.Point> hole : polygon.holes()) {
                        if (output.size() >= FullscreenRailGeometry.MAX_VISIBLE_SEGMENTS) break;
                        addRing(output, hole, projection, left, top, right, bottom, area.style().color());
                    }
                }
            }
        }
        return output;
    }

    private static void addRing(List<ScreenSegment> output, List<MapArea.Point> ring,
                                AreaRenderGeometry.Projection projection, double left, double top,
                                double right, double bottom, int color) {
        MapArea.Point previous = ring.get(ring.size() - 1);
        for (MapArea.Point point : ring) {
            if (output.size() >= FullscreenRailGeometry.MAX_VISIBLE_SEGMENTS) return;
            addClippedSegment(output, projection.screenX(previous.x()), projection.screenY(previous.z()),
                    projection.screenX(point.x()), projection.screenY(point.z()), left, top, right, bottom, color);
            previous = point;
        }
    }

    private static void addClippedSegment(List<ScreenSegment> output, double x1, double y1, double x2, double y2,
                                          double left, double top, double right, double bottom, int color) {
        ScreenSegment segment = clipSegment(x1, y1, x2, y2, left, top, right, bottom, color);
        if (segment != null) output.add(segment);
    }

    private static ScreenSegment clipSegment(double x1, double y1, double x2, double y2,
                                             double left, double top, double right, double bottom, int color) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) return null;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double start = 0.0;
        double end = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - left, right - x1, y1 - top, bottom - y1};
        for (int i = 0; i < p.length; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null;
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0.0) start = Math.max(start, ratio);
            else end = Math.min(end, ratio);
            if (start > end) return null;
        }
        return new ScreenSegment(x1 + start * dx, y1 + start * dy,
                x1 + end * dx, y1 + end * dy, color);
    }

    private static void renderLabels(GuiGraphicsExtractor graphics, FullscreenMapScreen screen,
                                     FullscreenMapPanel panel, List<MapArea> areas,
                                     AreaRenderGeometry.Projection projection,
                                     AreaRenderGeometry.Clip clip) {
        ArrayList<MapArea> candidates = new ArrayList<>();
        for (MapArea area : areas) {
            if (area.style().visible() && area.style().labels()
                    && area.dimension().equals(AreaManager.dimension())
                    && projection.blocksPerPixel() >= area.style().minBpp()
                    && projection.blocksPerPixel() <= area.style().maxBpp()
                    && (area.scope() != MapArea.Scope.SHARED || AreaManager.available(MapArea.Scope.SHARED))) {
                candidates.add(area);
            }
        }
        candidates.sort((first, second) -> {
            int priority = Integer.compare(second.style().priority(), first.style().priority());
            return priority != 0 ? priority : first.name().compareToIgnoreCase(second.name());
        });
        Minecraft mc = Minecraft.getInstance();
        ArrayList<LabelBox> accepted = new ArrayList<>();
        double playerX = mc.player == null ? Double.NaN : projection.screenX(mc.player.getX());
        double playerY = mc.player == null ? Double.NaN : projection.screenY(mc.player.getZ());
        int fontHeight = mc.font.lineHeight;
        for (MapArea area : candidates) {
            if (accepted.size() >= MAX_LABELS) break;
            int labelX = (int) Math.round(projection.screenX(area.label().x()));
            int labelY = (int) Math.round(projection.screenY(area.label().z()));
            int labelWidth = mc.font.width(area.name());
            if (labelX < clip.x() || labelY < clip.y()
                    || labelX + labelWidth > clip.x() + clip.width()
                    || labelY + fontHeight > clip.y() + clip.height()) continue;
            if (Double.isFinite(playerX) && Double.isFinite(playerY)
                    && Math.abs(playerX - (labelX + labelWidth / 2.0)) < LABEL_PLAYER_PADDING
                    && Math.abs(playerY - (labelY + fontHeight / 2.0)) < LABEL_PLAYER_PADDING) continue;
            LabelBox bounds = new LabelBox(labelX - 3, labelY - 2, labelX + labelWidth + 3, labelY + fontHeight + 2);
            if (accepted.stream().anyMatch(other -> bounds.intersects(other, 2))) continue;
            accepted.add(bounds);
            int backgroundColor = ((area.style().color() >>> 24) & 0xFF) > 0
                    ? (area.style().color() & 0x00FFFFFF) | 0xA0000000 : 0xA0000000;
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), backgroundColor);
            graphics.text(mc.font, area.name(), labelX, labelY, 0xFFFFFFFF, false);
        }
    }

    private static void drawSegment(GuiGraphicsExtractor graphics, double x1, double y1,
                                    double x2, double y2, float thickness, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (!(length > 0.0f) || !Float.isFinite(length) || !Double.isFinite(x1) || !Double.isFinite(y1)) return;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) x1, (float) y1);
            graphics.pose().rotate((float) Math.atan2(dy, dx));
            graphics.pose().scale(length, thickness);
            graphics.pose().translate(0, -0.5f);
            graphics.fill(0, 0, 1, 1, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private record ScreenSegment(double x1, double y1, double x2, double y2, int color) {}

    private record LabelBox(int left, int top, int right, int bottom) {
        boolean intersects(LabelBox other, int spacing) {
            return left < other.right() + spacing && right + spacing > other.left()
                    && top < other.bottom() + spacing && bottom + spacing > other.top();
        }
    }

    private static final class CacheKey {
        private final UUID areaId;
        private final MapArea.Scope scope;
        private final MapArea.Geometry geometry;
        private final AreaRenderGeometry.Projection projection;
        private final AreaRenderGeometry.Clip clip;
        private final int hash;

        CacheKey(UUID areaId, MapArea.Scope scope, MapArea.Geometry geometry,
                 AreaRenderGeometry.Projection projection, AreaRenderGeometry.Clip clip) {
            this.areaId = areaId;
            this.scope = scope;
            this.geometry = geometry;
            this.projection = projection;
            this.clip = clip;
            hash = 31 * (31 * (31 * areaId.hashCode() + scope.hashCode())
                    + System.identityHashCode(geometry)) + 31 * projection.hashCode() + clip.hashCode();
        }

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other) {
            return other == this || other instanceof CacheKey key && areaId.equals(key.areaId)
                    && scope == key.scope && geometry == key.geometry
                    && projection.equals(key.projection) && clip.equals(key.clip);
        }
    }
}
