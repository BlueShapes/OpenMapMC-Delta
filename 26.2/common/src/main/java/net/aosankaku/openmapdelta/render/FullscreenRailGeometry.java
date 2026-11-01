// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.compat.TransitRail;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects and clips immutable transit-rail snapshots for the fullscreen map. */
public final class FullscreenRailGeometry {
    public static final int CREATE_COLOR = 0xFFFFB45C;
    public static final int MTR_COLOR = 0xFF55CFFF;
    public static final int MAX_VISIBLE_SEGMENTS = 8192;
    private static final double CLIP_PADDING = 2.0;
    private static final double MAX_SCREEN_COORDINATE = 1.0e12;
    private static final int MAX_CACHED_SNAPSHOTS = 4;
    private static final ReferenceQueue<List<TransitRail>> COLLECTED = new ReferenceQueue<>();
    private static final Map<IdentityReference<List<TransitRail>>, List<PreparedRail>> PREPARED =
            new LinkedHashMap<>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<IdentityReference<List<TransitRail>>, List<PreparedRail>> eldest) {
                    return size() > MAX_CACHED_SNAPSHOTS;
                }
            };

    private FullscreenRailGeometry() { }

    public record Viewport(int x, int y, int width, int height, double panX, double panZ, double blocksPerPixel) { }
    public record Segment(double x1, double y1, double x2, double y2, TransitRail.Source source) { }

    /** Returns at most {@value #MAX_VISIBLE_SEGMENTS} segments, with coordinates clipped to the map viewport. */
    public static List<Segment> visibleSegments(List<TransitRail> rails, Viewport viewport,
                                                boolean showMtr, boolean showCreate) {
        if (rails == null || rails.isEmpty() || viewport == null || viewport.width() <= 0 || viewport.height() <= 0
                || !Double.isFinite(viewport.panX()) || !Double.isFinite(viewport.panZ())) return List.of();
        double bpp = FullscreenMapZoom.safeBlocksPerPixel(viewport.blocksPerPixel());
        double left = viewport.x() - CLIP_PADDING;
        double top = viewport.y() - CLIP_PADDING;
        double right = viewport.x() + (double) viewport.width() + CLIP_PADDING;
        double bottom = viewport.y() + (double) viewport.height() + CLIP_PADDING;
        List<PreparedRail> prepared = prepared(rails);
        ArrayList<Segment> result = new ArrayList<>(Math.min(prepared.size() * 2, MAX_VISIBLE_SEGMENTS));

        for (PreparedRail entry : prepared) {
            TransitRail rail = entry.rail();
            if ((rail.source() == TransitRail.Source.MTR && !showMtr)
                    || (rail.source() == TransitRail.Source.CREATE && !showCreate)) continue;
            double minX = projectX(entry.minX(), viewport, bpp);
            double maxX = projectX(entry.maxX(), viewport, bpp);
            double minY = projectZ(entry.minZ(), viewport, bpp);
            double maxY = projectZ(entry.maxZ(), viewport, bpp);
            if (!finiteScreen(minX) || !finiteScreen(maxX) || !finiteScreen(minY) || !finiteScreen(maxY)
                    || Math.max(minX, maxX) < left || Math.min(minX, maxX) > right
                    || Math.max(minY, maxY) < top || Math.min(minY, maxY) > bottom) continue;

            List<TransitRail.Point> points = rail.points();
            double previousX = projectX(points.get(0).x(), viewport, bpp);
            double previousY = projectZ(points.get(0).z(), viewport, bpp);
            for (int i = 1; i < points.size() && result.size() < MAX_VISIBLE_SEGMENTS; i++) {
                TransitRail.Point point = points.get(i);
                double x = projectX(point.x(), viewport, bpp);
                double y = projectZ(point.z(), viewport, bpp);
                Segment clipped = clip(previousX, previousY, x, y, left, top, right, bottom, rail.source());
                if (clipped != null) result.add(clipped);
                previousX = x;
                previousY = y;
            }
            if (result.size() >= MAX_VISIBLE_SEGMENTS) break;
        }
        return result;
    }

    private static List<PreparedRail> prepared(List<TransitRail> rails) {
        synchronized (PREPARED) {
            IdentityReference<?> collected;
            while ((collected = (IdentityReference<?>) COLLECTED.poll()) != null) PREPARED.remove(collected);
            IdentityReference<List<TransitRail>> lookup = new IdentityReference<>(rails, null);
            List<PreparedRail> cached = PREPARED.get(lookup);
            if (cached != null) return cached;

            ArrayList<PreparedRail> built = new ArrayList<>();
            for (TransitRail rail : rails) {
                if (rail == null || rail.source() == null || rail.points() == null || rail.points().size() < 2) continue;
                double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
                boolean valid = true;
                for (TransitRail.Point point : rail.points()) {
                    if (point == null || !Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z())) {
                        valid = false;
                        break;
                    }
                    minX = Math.min(minX, point.x());
                    maxX = Math.max(maxX, point.x());
                    minZ = Math.min(minZ, point.z());
                    maxZ = Math.max(maxZ, point.z());
                }
                if (valid) built.add(new PreparedRail(rail, minX, maxX, minZ, maxZ));
            }
            List<PreparedRail> result = List.copyOf(built);
            PREPARED.put(new IdentityReference<>(rails, COLLECTED), result);
            return result;
        }
    }

    private static double projectX(double world, Viewport viewport, double bpp) {
        return viewport.x() + viewport.width() / 2 + (world - viewport.panX()) / bpp;
    }

    private static double projectZ(double world, Viewport viewport, double bpp) {
        return viewport.y() + viewport.height() / 2 + (world - viewport.panZ()) / bpp;
    }

    private static boolean finiteScreen(double coordinate) {
        return Double.isFinite(coordinate) && Math.abs(coordinate) <= MAX_SCREEN_COORDINATE;
    }

    /** Liang-Barsky clipping prevents large offscreen rail transforms from reaching the renderer. */
    private static Segment clip(double x1, double y1, double x2, double y2,
                                double left, double top, double right, double bottom,
                                TransitRail.Source source) {
        if (!finiteScreen(x1) || !finiteScreen(y1) || !finiteScreen(x2) || !finiteScreen(y2)) return null;
        double dx = x2 - x1, dy = y2 - y1;
        double t0 = 0.0, t1 = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - left, right - x1, y1 - top, bottom - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null;
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0.0) {
                if (ratio > t1) return null;
                t0 = Math.max(t0, ratio);
            } else {
                if (ratio < t0) return null;
                t1 = Math.min(t1, ratio);
            }
        }
        if (t0 > t1) return null;
        double startX = x1 + t0 * dx, startY = y1 + t0 * dy;
        double endX = x1 + t1 * dx, endY = y1 + t1 * dy;
        if (!finiteScreen(startX) || !finiteScreen(startY) || !finiteScreen(endX) || !finiteScreen(endY)
                || (startX == endX && startY == endY)) return null;
        return new Segment(startX, startY, endX, endY, source);
    }

    private record PreparedRail(TransitRail rail, double minX, double maxX, double minZ, double maxZ) { }

    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int hash;

        private IdentityReference(T referent, ReferenceQueue<T> queue) {
            super(referent, queue);
            hash = System.identityHashCode(referent);
        }

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityReference<?> reference)) return false;
            Object referent = get();
            return referent != null && referent == reference.get();
        }
    }
}
