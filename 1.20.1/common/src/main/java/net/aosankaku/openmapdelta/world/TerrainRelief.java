// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.Objects;

/** Immutable presentation view that adds restrained, height-derived light and shadow to cached terrain. */
public final class TerrainRelief {
    private static final double MIN_MULTIPLIER = 0.55;
    private static final double MAX_MULTIPLIER = 1.12;

    private final Tile center;
    private final Tile north;
    private final Tile east;
    private final Tile south;
    private final Tile west;
    private final long centerRevision;
    private final long northRevision;
    private final long eastRevision;
    private final long southRevision;
    private final long westRevision;

    public TerrainRelief(Tile center, Tile north, Tile east, Tile south, Tile west) {
        this.center = center;
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
        centerRevision = revisionOf(center);
        northRevision = revisionOf(north);
        eastRevision = revisionOf(east);
        southRevision = revisionOf(south);
        westRevision = revisionOf(west);
    }

    /** Returns the displayed pixel after deriving relief from known cardinal-neighbor heights. */
    public int getPixel(int localX, int localZ) {
        if (center == null) return 0;
        int color = center.getPixel(localX, localZ);
        if (color >>> 24 == 0 || !center.hasObservedPixel(localX, localZ)) return color;

        int height = center.getHeight(localX, localZ);
        if (height == Tile.UNKNOWN_HEIGHT) return color;

        double multiplier = 1.0;
        multiplier += edgeEffect(north, localX, localZ, 0, -1, 0.12, 0.035, height);
        multiplier += edgeEffect(east, localX, localZ, 1, 0, 0.06, 0.015, height);
        multiplier += edgeEffect(south, localX, localZ, 0, 1, 0.06, 0.015, height);
        multiplier += edgeEffect(west, localX, localZ, -1, 0, 0.12, 0.035, height);
        multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
        if (multiplier == 1.0) return color;

        int alpha = color & 0xFF000000;
        int red = scaleChannel(color >>> 16 & 255, multiplier);
        int green = scaleChannel(color >>> 8 & 255, multiplier);
        int blue = scaleChannel(color & 255, multiplier);
        return alpha | red << 16 | green << 8 | blue;
    }

    Tile centerTile() {
        return center;
    }

    private double edgeEffect(Tile outerNeighbor, int localX, int localZ,
                              int dx, int dz, double shadowWeight, double highlightWeight, int height) {
        int neighborX = localX + dx;
        int neighborZ = localZ + dz;
        Tile neighbor = center;
        if (neighborX < 0) {
            neighbor = west;
            neighborX = Tile.SIZE - 1;
        } else if (neighborX >= Tile.SIZE) {
            neighbor = east;
            neighborX = 0;
        } else if (neighborZ < 0) {
            neighbor = north;
            neighborZ = Tile.SIZE - 1;
        } else if (neighborZ >= Tile.SIZE) {
            neighbor = south;
            neighborZ = 0;
        }
        // The passed neighbor fixes the intended cardinal edge and prevents using an unrelated tile.
        if (neighbor != center && neighbor != outerNeighbor) return 0.0;
        if (neighbor == null || !neighbor.hasObservedPixel(neighborX, neighborZ)) return 0.0;
        if (neighbor.getPixel(neighborX, neighborZ) >>> 24 == 0) return 0.0;
        int neighborHeight = neighbor.getHeight(neighborX, neighborZ);
        if (neighborHeight == Tile.UNKNOWN_HEIGHT) return 0.0;

        long delta = (long) neighborHeight - (long) height;
        if (delta == 0) return 0.0;
        double strength = 1.0 + Math.min(1.0, (Math.abs(delta) - 1L) / 7.0);
        return delta > 0 ? -shadowWeight * strength : highlightWeight * strength;
    }

    private static int scaleChannel(int channel, double multiplier) {
        return Math.max(0, Math.min(255, (int) Math.round(channel * multiplier)));
    }

    private static long revisionOf(Tile tile) {
        return tile == null ? 0L : tile.getImageRevision();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TerrainRelief relief)) return false;
        return center == relief.center && north == relief.north && east == relief.east
                && south == relief.south && west == relief.west
                && centerRevision == relief.centerRevision && northRevision == relief.northRevision
                && eastRevision == relief.eastRevision && southRevision == relief.southRevision
                && westRevision == relief.westRevision;
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(center);
        result = 31 * result + System.identityHashCode(north);
        result = 31 * result + System.identityHashCode(east);
        result = 31 * result + System.identityHashCode(south);
        result = 31 * result + System.identityHashCode(west);
        result = 31 * result + Objects.hash(centerRevision, northRevision, eastRevision, southRevision, westRevision);
        return result;
    }
}
