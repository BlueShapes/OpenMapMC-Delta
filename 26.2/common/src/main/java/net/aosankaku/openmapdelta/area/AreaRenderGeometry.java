// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.List;

/** Produces bounded GUI-pixel spans for optional map area fills. */
public final class AreaRenderGeometry {
    public static final int MAX_VIEWPORT_DIMENSION = 8192;
    public static final int MAX_VISIBLE_SCANLINES = 8192;
    public static final int MAX_SPANS = 131_072;

    private AreaRenderGeometry() {}

    /** All map geometry is north-up. Preview panning is quantized to GUI pixels. */
    public record Projection(int x, int y, int width, int height, double panX, double panZ,
                             double blocksPerPixel) {
        public Projection {
            if (width < 0 || height < 0 || width > MAX_VIEWPORT_DIMENSION || height > MAX_VIEWPORT_DIMENSION
                    || !Double.isFinite(panX) || !Double.isFinite(panZ)
                    || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0) {
                throw new IllegalArgumentException("Invalid area viewport projection");
            }
        }

        public double screenX(double worldX) {
            return x + width / 2.0 + (worldX - panX) / blocksPerPixel;
        }

        public double screenY(double worldZ) {
            return y + height / 2.0 + (worldZ - panZ) / blocksPerPixel;
        }

        public double worldX(double screenX) {
            return panX + (screenX - x - width / 2.0) * blocksPerPixel;
        }

        public double worldZ(double screenY) {
            return panZ + (screenY - y - height / 2.0) * blocksPerPixel;
        }
    }

    public record Clip(int x, int y, int width, int height) {
        public Clip {
            if (width < 0 || height < 0 || width > MAX_VIEWPORT_DIMENSION || height > MAX_VIEWPORT_DIMENSION) {
                throw new IllegalArgumentException("Invalid area render clip");
            }
        }
    }

    /** A half-open horizontal run. */
    public record Span(int y, int xStart, int xEnd) {
        public Span {
            if (xEnd <= xStart) throw new IllegalArgumentException("Empty area fill span");
        }
    }

    private record Edge(double x, double dxPerRow, int firstRow, int afterLastRow, int polygonIndex) {}

    /**
     * Rasterizes polygon rings at pixel centers, including interior holes, while returning only
     * pixels inside the current viewport clip. It never allocates a world-sized texture or loops
     * across off-screen world dimensions.
     */
    public static List<Span> scanlines(MapArea.Geometry geometry, Projection projection, Clip clip) {
        if (geometry.shape() != MapArea.Shape.POLYGON || projection.width() == 0 || projection.height() == 0
                || clip.width() == 0 || clip.height() == 0) return List.of();
        long clipRightLong = (long) clip.x() + clip.width();
        long clipBottomLong = (long) clip.y() + clip.height();
        int clipLeft = Math.max(projection.x(), clip.x());
        int clipTop = Math.max(projection.y(), clip.y());
        int clipRight = (int) Math.min((long) projection.x() + projection.width(), clipRightLong);
        int clipBottom = (int) Math.min((long) projection.y() + projection.height(), clipBottomLong);
        if (clipRight <= clipLeft || clipBottom <= clipTop) return List.of();

        var edges = new ArrayList<Edge>();
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int polygonIndex = 0; polygonIndex < geometry.polygons().size(); polygonIndex++) {
            MapArea.Polygon polygon = geometry.polygons().get(polygonIndex);
            var rings = new ArrayList<List<MapArea.Point>>(1 + polygon.holes().size());
            rings.add(polygon.outer());
            rings.addAll(polygon.holes());
            for (List<MapArea.Point> ring : rings) {
                if (ring.size() < 3) continue;
                MapArea.Point previous = ring.get(ring.size() - 1);
                for (MapArea.Point current : ring) {
                    double x1 = projection.screenX(previous.x());
                    double y1 = projection.screenY(previous.z());
                    double x2 = projection.screenX(current.x());
                    double y2 = projection.screenY(current.z());
                    if (Double.isFinite(x1) && Double.isFinite(y1) && Double.isFinite(x2) && Double.isFinite(y2)) {
                        minY = Math.min(minY, Math.min(y1, y2));
                        maxY = Math.max(maxY, Math.max(y1, y2));
                        if (y1 != y2) {
                            double top = Math.min(y1, y2);
                            double bottom = Math.max(y1, y2);
                            int first = clampCeil(top - 0.5, clipTop, clipBottom);
                            int after = clampCeil(bottom - 0.5, clipTop, clipBottom);
                            if (after > first) {
                                double deltaY = y2 - y1;
                                edges.add(new Edge(x1 + ((first + 0.5 - y1) / deltaY) * (x2 - x1),
                                        (x2 - x1) / deltaY, first, after, polygonIndex));
                            }
                        }
                    }
                    previous = current;
                }
            }
        }
        if (edges.isEmpty() || !Double.isFinite(minY) || !Double.isFinite(maxY)) return List.of();
        long rawRowCount = (long) clampCeil(Math.min(maxY - 0.5, clipBottom), clipTop, clipBottom)
                - clampCeil(Math.max(minY - 0.5, clipTop), clipTop, clipBottom);
        if (rawRowCount > MAX_VISIBLE_SCANLINES) return List.of();

        edges.sort(java.util.Comparator.comparingInt(Edge::firstRow));
        var byPolygon = new ArrayList<ArrayList<Double>>(geometry.polygons().size());
        for (int i = 0; i < geometry.polygons().size(); i++) byPolygon.add(new ArrayList<>());
        var result = new ArrayList<Span>();
        var rowSpans = new ArrayList<Span>();
        for (int row = clipTop; row < clipBottom; row++) {
            for (ArrayList<Double> intersections : byPolygon) intersections.clear();
            for (Edge edge : edges) {
                if (edge.firstRow() > row) break;
                if (row < edge.afterLastRow()) {
                    double x = edge.x() + (row - edge.firstRow()) * edge.dxPerRow();
                    if (Double.isFinite(x)) byPolygon.get(edge.polygonIndex()).add(x);
                }
            }
            rowSpans.clear();
            for (ArrayList<Double> intersections : byPolygon) {
                intersections.sort(Double::compare);
                for (int i = 0; i + 1 < intersections.size(); i += 2) {
                    int left = Math.max(clipLeft, clampCeil(intersections.get(i) - 0.5, clipLeft, clipRight));
                    int right = Math.min(clipRight, clampCeil(intersections.get(i + 1) - 0.5, clipLeft, clipRight));
                    if (right > left) rowSpans.add(new Span(row, left, right));
                }
            }
            rowSpans.sort(java.util.Comparator.comparingInt(Span::xStart));
            for (int i = 0; i < rowSpans.size();) {
                Span first = rowSpans.get(i++);
                int right = first.xEnd();
                while (i < rowSpans.size() && rowSpans.get(i).xStart() <= right) {
                    right = Math.max(right, rowSpans.get(i++).xEnd());
                }
                if (result.size() == MAX_SPANS) return List.of();
                result.add(new Span(row, first.xStart(), right));
            }
        }
        return List.copyOf(result);
    }

    private static int clampCeil(double value, int minimum, int maximum) {
        if (!Double.isFinite(value)) return value < 0 ? minimum : maximum;
        if (value <= minimum) return minimum;
        if (value >= maximum) return maximum;
        return (int) Math.ceil(value);
    }
}
