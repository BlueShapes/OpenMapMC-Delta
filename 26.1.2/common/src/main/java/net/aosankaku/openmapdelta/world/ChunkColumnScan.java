// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

/** Worker stage: only detached block palettes, never a live world or renderer. */
public final class ChunkColumnScan {
    private ChunkColumnScan() {}
    public static final int NO_SPACE = Integer.MIN_VALUE;

    public interface Columns {
        int minY();
        int maxYExclusive();
        int surface(int x, int z);
        boolean air(int x, int y, int z);
        boolean fluid(int x, int y, int z);
        default boolean hasCeiling() { return false; }
        default int ceilingYExclusive() { return maxYExclusive(); }
    }

    public record Prepared(int[] tops, int[] spaces, int[] floors, int[] depths) {}

    public static Prepared prepare(Columns columns, int layer) throws InterruptedException {
        int[] tops = new int[256], spaces = new int[256], floors = new int[256], depths = new int[256];
        java.util.Arrays.fill(spaces, NO_SPACE);
        for (int z = 0; z < 16; z++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            for (int x = 0; x < 16; x++) {
                int i = x + z * 16;
                int min = columns.minY(), surface = columns.surface(x, z);
                int top = surface;
                if (layer == Integer.MIN_VALUE && columns.hasCeiling()) {
                    int columnX = x, columnZ = z;
                    top = ceilingSurface(min, columns.ceilingYExclusive() - 1,
                            y -> columns.air(columnX, y, columnZ));
                } else {
                    while (top > min && columns.air(x, top, z)) top--;
                }
                tops[i] = top;
                if (layer == Integer.MIN_VALUE) {
                    int below = top - 1;
                    if (top > min && columns.fluid(x, top, z)) {
                        do { depths[i]++; } while (--below > min && columns.fluid(x, below + 1, z));
                    }
                    continue;
                }
                int selected = Math.max(min, Math.min(columns.maxYExclusive() - 1, layer));
                if (selected > surface) continue;
                int space = NO_SPACE;
                for (int distance = 0; distance <= 8 && space == NO_SPACE; distance++) {
                    for (int direction = -1; direction <= 1; direction += 2) {
                        int y = selected + distance * direction;
                        if (y > min && y < surface && (columns.air(x, y, z) || columns.fluid(x, y, z))) {
                            space = y; break;
                        }
                    }
                }
                spaces[i] = space;
                if (space == NO_SPACE) { floors[i] = selected; continue; }
                int floor = space;
                do {
                    floor--;
                    if (columns.fluid(x, floor, z)) depths[i]++;
                } while (floor > min && (columns.air(x, floor, z) || columns.fluid(x, floor, z)));
                floors[i] = floor;
            }
        }
        return new Prepared(tops, spaces, floors, depths);
    }

    /** Ignore the roof and tiny pockets; keep the first exposed solid or fluid beneath it. */
    public static int ceilingSurface(int min, int top, java.util.function.IntPredicate air) {
        int initialAir = 0;
        while (top > min && air.test(top)) { top--; initialAir++; }
        if (initialAir >= 2) return air.test(top) ? NO_SPACE : top;
        while (top > min) {
            if (air.test(top) && air.test(top - 1)) {
                do { top--; } while (top > min && air.test(top));
                return air.test(top) ? NO_SPACE : top;
            }
            top--;
        }
        return NO_SPACE;
    }
}
