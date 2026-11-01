// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.function.IntBinaryOperator;

/** Summed-area colors permit exact, constant-time rectangle averages for explored terrain. */
public final class TileColorAverage {
    private static final int SIDE = Tile.SIZE + 1;
    private final int[][] sums = new int[5][SIDE * SIDE];
    private final Tile tile;
    private final TerrainRelief relief;
    private final long imageRevision;

    public TileColorAverage(Tile tile) {
        this(tile, null, tile::getPixel);
    }

    /** Builds a shaded summary using the supplied immutable dependency context. */
    public TileColorAverage(TerrainRelief relief) {
        this(relief.centerTile(), relief, relief::getPixel);
    }

    private TileColorAverage(Tile tile, TerrainRelief relief, IntBinaryOperator pixelSource) {
        this.tile = tile;
        this.relief = relief;
        imageRevision = tile.getImageRevision();
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) {
                int color = pixelSource.applyAsInt(x, z);
                int at = (z + 1) * SIDE + x + 1;
                int left = at - 1, above = at - SIDE, corner = above - 1;
                int alpha = color >>> 24;
                for (int channel = 0; channel < 5; channel++) {
                    int value = alpha == 0 ? 0 : channel == 0 ? 1 : (color >>> ((channel - 1) * 8)) & 255;
                    sums[channel][at] = value + sums[channel][left] + sums[channel][above] - sums[channel][corner];
                }
            }
        }
    }

    public boolean matches(Tile tile) { return relief == null && this.tile == tile && imageRevision == tile.getImageRevision(); }

    public boolean matches(TerrainRelief relief) { return this.relief != null && this.relief.equals(relief); }

    public void accumulate(int minX, int minZ, int maxX, int maxZ, long[] total) {
        accumulate(minX, minZ, maxX, maxZ, total, 0);
    }

    public void accumulate(int minX, int minZ, int maxX, int maxZ, long[] total, int offset) {
        if (minX < 0 || minZ < 0 || maxX > Tile.SIZE || maxZ > Tile.SIZE || minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Rectangle outside tile");
        }
        int corner = minZ * SIDE + minX;
        int right = minZ * SIDE + maxX;
        int bottom = maxZ * SIDE + minX;
        int end = maxZ * SIDE + maxX;
        for (int channel = 0; channel < 5; channel++) {
            total[offset + channel] += sums[channel][end] - sums[channel][right] - sums[channel][bottom] + sums[channel][corner];
        }
    }

    /** Adds an exact, area-weighted pixel rectangle whose edges may cross source-pixel boundaries. */
    public void accumulate(double minX, double minZ, double maxX, double maxZ, double[] total) {
        accumulate(minX, minZ, maxX, maxZ, total, 0);
    }

    public void accumulate(double minX, double minZ, double maxX, double maxZ, double[] total, int offset) {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX < 0.0 || minZ < 0.0 || maxX > Tile.SIZE || maxZ > Tile.SIZE
                || minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Rectangle outside tile");
        }
        if (minX == maxX || minZ == maxZ) return;
        for (int channel = 0; channel < 5; channel++) {
            total[offset + channel] += prefix(channel, maxX, maxZ) - prefix(channel, minX, maxZ)
                    - prefix(channel, maxX, minZ) + prefix(channel, minX, minZ);
        }
    }

    /** Integral of the piecewise-constant source pixels up to a fractional tile coordinate. */
    private double prefix(int channel, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        int x1 = Math.min(Tile.SIZE, x0 + 1), z1 = Math.min(Tile.SIZE, z0 + 1);
        double fx = x0 == Tile.SIZE ? 0.0 : x - x0;
        double fz = z0 == Tile.SIZE ? 0.0 : z - z0;
        double topLeft = sums[channel][z0 * SIDE + x0];
        double topRight = sums[channel][z0 * SIDE + x1];
        double bottomLeft = sums[channel][z1 * SIDE + x0];
        double bottomRight = sums[channel][z1 * SIDE + x1];
        return topLeft + fx * (topRight - topLeft) + fz * (bottomLeft - topLeft)
                + fx * fz * (bottomRight - topRight - bottomLeft + topLeft);
    }

    public static int color(long[] total) {
        return color(total, 0);
    }

    public static int color(long[] total, int offset) {
        if (total[offset] == 0) return 0;
        return (int) (total[offset + 4] / total[offset]) << 24 | (int) (total[offset + 3] / total[offset]) << 16
                | (int) (total[offset + 2] / total[offset]) << 8 | (int) (total[offset + 1] / total[offset]);
    }

    public static int color(double[] total) {
        return color(total, 0);
    }

    public static int color(double[] total, int offset) {
        if (total[offset] <= 0.0) return 0;
        return channel(total[offset + 4] / total[offset]) << 24
                | channel(total[offset + 3] / total[offset]) << 16
                | channel(total[offset + 2] / total[offset]) << 8
                | channel(total[offset + 1] / total[offset]);
    }

    private static int channel(double value) {
        double nearest = Math.rint(value);
        if (Math.abs(value - nearest) <= 1e-9) value = nearest;
        return (int) Math.max(0.0, Math.min(255.0, Math.floor(value)));
    }
}
