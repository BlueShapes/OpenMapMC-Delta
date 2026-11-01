// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.List;
import java.util.Objects;

/** Immutable, client-synchronized rail path used by the fullscreen map overlay. */
public record TransitRail(Source source, Object dimension, List<Point> points) {
    public enum Source { CREATE, MTR }

    public record Point(double x, double y, double z) {
        public boolean isFinite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    public TransitRail {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dimension, "dimension");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.size() < 2 || points.stream().anyMatch(point -> point == null || !point.isFinite())) {
            throw new IllegalArgumentException("A rail path needs at least two finite points");
        }
    }
}
