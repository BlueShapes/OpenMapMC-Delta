// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Minimizes the sum of 3D distances to station positions, then rounds to block coordinates. */
public final class TransitStationCenter {
    private static final int MAX_ITERATIONS = 128;
    private static final double TOLERANCE = 1e-6;
    private static final double COINCIDENT = 1e-8;

    private TransitStationCenter() {}

    public static TransitStation.Position center(Collection<TransitStation.Position> platforms) {
        List<TransitStation.Position> points = new ArrayList<>(platforms);
        if (points.isEmpty()) throw new IllegalArgumentException("A station needs at least one platform");
        points.sort(TransitStation.Position::compareTo);
        if (collinear(points)) {
            // Any point between the middle two minimizes the sum; choose their midpoint consistently.
            TransitStation.Position left = points.get((points.size() - 1) / 2);
            TransitStation.Position right = points.get(points.size() / 2);
            return block(((double) left.x() + right.x()) / 2,
                    ((double) left.y() + right.y()) / 2, ((double) left.z() + right.z()) / 2);
        }
        double x = 0, y = 0, z = 0;
        for (var point : points) { x += point.x(); y += point.y(); z += point.z(); }
        x /= points.size(); y /= points.size(); z /= points.size();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double rx = 0, ry = 0, rz = 0, hxx = 0, hyy = 0, hzz = 0, hxy = 0, hxz = 0, hyz = 0;
            int coincident = 0;
            for (var point : points) {
                double dx = point.x() - x, dy = point.y() - y, dz = point.z() - z;
                double distance = norm(dx, dy, dz);
                if (distance <= COINCIDENT) { coincident++; continue; }
                double ux = dx / distance, uy = dy / distance, uz = dz / distance;
                rx += ux; ry += uy; rz += uz;
                // Equivalent to (I - uu^T)/distance, without subtracting almost-equal values on the diagonal.
                hxx += (uy * uy + uz * uz) / distance;
                hyy += (ux * ux + uz * uz) / distance;
                hzz += (ux * ux + uy * uy) / distance;
                hxy -= ux * uy / distance; hxz -= ux * uz / distance; hyz -= uy * uz / distance;
            }
            double residual = norm(rx, ry, rz);
            // At an input point, its multiplicity contributes a ball to the subgradient.
            if (residual == 0 || (coincident > 0 && residual <= coincident)) return block(x, y, z);
            double[] direction = coincident == 0 ? newton(hxx, hyy, hzz, hxy, hxz, hyz, rx, ry, rz) : null;
            if (direction == null || direction[0] * rx + direction[1] * ry + direction[2] * rz <= 0) {
                direction = new double[] {rx, ry, rz};
            }
            double length = norm(direction[0], direction[1], direction[2]);
            double ux = direction[0] / length, uy = direction[1] / length, uz = direction[2] / length;
            double step = lineMinimum(points, x, y, z, ux, uy, uz);
            x += step * ux; y += step * uy; z += step * uz;
            if (step <= TOLERANCE) {
                // Line-search accuracy is coarser than coincidence detection. A tiny Newton step near an
                // input point does not prove optimality; snap and check its full subgradient next time.
                TransitStation.Position nearby = null;
                for (var point : points) {
                    if (norm(point.x() - x, point.y() - y, point.z() - z) <= TOLERANCE) {
                        nearby = point;
                        break;
                    }
                }
                if (nearby == null) break;
                x = nearby.x(); y = nearby.y(); z = nearby.z();
            }
        }
        return block(x, y, z);
    }

    /** Solve the positive-definite Hessian using Cholesky; fall back if rounding makes it singular. */
    private static double[] newton(double xx, double yy, double zz, double xy, double xz, double yz,
                                   double rx, double ry, double rz) {
        if (!(xx > 0)) return null;
        double a = Math.sqrt(xx), b = xy / a, c = xz / a;
        double pivot = yy - b * b;
        if (!(pivot > 0)) return null;
        double d = Math.sqrt(pivot), e = (yz - b * c) / d;
        pivot = zz - c * c - e * e;
        if (!(pivot > 0)) return null;
        double f = Math.sqrt(pivot);
        double v0 = rx / a, v1 = (ry - b * v0) / d, v2 = (rz - c * v0 - e * v1) / f;
        double z = v2 / f, y = (v1 - e * z) / d, x = (v0 - b * y - c * z) / a;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new double[] {x, y, z};
    }

    private static double lineMinimum(List<TransitStation.Position> points, double x, double y, double z,
                                      double ux, double uy, double uz) {
        double low = 0, high = 0;
        for (var point : points) {
            high = Math.max(high, (point.x() - x) * ux + (point.y() - y) * uy + (point.z() - z) * uz);
        }
        // Beyond every point's projection, the directional derivative is nonnegative.
        for (int iteration = 0; iteration < 80 && high - low > TOLERANCE; iteration++) {
            double middle = (low + high) / 2;
            double derivative = 0;
            int coincident = 0;
            for (var point : points) {
                double dx = x + middle * ux - point.x(), dy = y + middle * uy - point.y(),
                        dz = z + middle * uz - point.z();
                double distance = norm(dx, dy, dz);
                if (distance <= COINCIDENT) coincident++;
                else derivative += (dx * ux + dy * uy + dz * uz) / distance;
            }
            if (derivative - coincident <= 0 && derivative + coincident >= 0) return middle;
            if (derivative + coincident < 0) low = middle;
            else high = middle;
        }
        return (low + high) / 2;
    }

    private static boolean collinear(List<TransitStation.Position> points) {
        var first = points.get(0);
        var last = points.get(points.size() - 1);
        double dx = (double) last.x() - first.x(), dy = (double) last.y() - first.y(),
                dz = (double) last.z() - first.z();
        for (var point : points) {
            double px = (double) point.x() - first.x(), py = (double) point.y() - first.y(),
                    pz = (double) point.z() - first.z();
            if (dy * pz - dz * py != 0 || dz * px - dx * pz != 0 || dx * py - dy * px != 0) return false;
        }
        return true;
    }

    private static double norm(double x, double y, double z) { return Math.sqrt(x * x + y * y + z * z); }
    private static TransitStation.Position block(double x, double y, double z) {
        return new TransitStation.Position((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
    }
}
