// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.Arrays;

public class Tile {
    public static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;
    public static final int SIZE = 128;
    public static final int PIXEL_COUNT = SIZE * SIZE;
    private static final int MASK_WORD_COUNT = PIXEL_COUNT / Long.SIZE;

    private final int[] pixels = new int[PIXEL_COUNT];
    private int[] grassPixels; // Zero means that the original map color is retained.
    private boolean grassTintEnabled;
    private final int[] heights = unknownHeights();
    private final long[] observed = new long[MASK_WORD_COUNT];
    private boolean dirty;
    private long contentRevision;
    private long imageRevision;
    private long savedContentRevision;
    private long lastUpdatedTick;

    public int getPixel(int localX, int localZ) {
        if (!inBounds(localX, localZ)) return 0;
        int index = index(localX, localZ);
        return grassTintEnabled && grassPixels != null && grassPixels[index] != 0 ? grassPixels[index] : pixels[index];
    }

    public int getHeight(int localX, int localZ) {
        return inBounds(localX, localZ) ? heights[index(localX, localZ)] : UNKNOWN_HEIGHT;
    }

    /** A sampled transparent pixel is different from a pixel awaiting a scan or disk load. */
    public boolean hasObservedPixel(int localX, int localZ) {
        return inBounds(localX, localZ) && isObserved(index(localX, localZ));
    }

    private static int[] unknownHeights() {
        int[] values = new int[PIXEL_COUNT];
        Arrays.fill(values, UNKNOWN_HEIGHT);
        return values;
    }

    /** Records a sampled pixel, including transparent zero pixels. */
    public boolean setPixel(int localX, int localZ, int argb) {
        return setPixel(localX, localZ, argb, UNKNOWN_HEIGHT);
    }

    public boolean setPixel(int localX, int localZ, int argb, int height) {
        return setPixel(localX, localZ, argb, height, 0);
    }

    public boolean setPixel(int localX, int localZ, int argb, int height, int grassColor) {
        if (!inBounds(localX, localZ)) return false;
        int index = index(localX, localZ);
        if (grassColor == argb) grassColor = 0;
        int previousGrass = grassPixels == null ? 0 : grassPixels[index];
        boolean changed = pixels[index] != argb || heights[index] != height
                || previousGrass != grassColor || !isObserved(index);
        if (grassColor != 0 && grassPixels == null) grassPixels = new int[PIXEL_COUNT];
        if (grassPixels != null) grassPixels[index] = grassColor;
        pixels[index] = argb;
        heights[index] = height;
        markObserved(index);
        if (changed) {
            dirty = true;
            contentRevision++;
            imageRevision++;
        }
        return changed;
    }

    /** Applies persisted pixels only where this session has not sampled a value. */
    public boolean mergeLoaded(Snapshot snapshot) {
        boolean changed = false;
        for (int word = 0; word < observed.length; word++) {
            long incoming = snapshot.observedWord(word) & ~observed[word];
            while (incoming != 0) {
                int bit = Long.numberOfTrailingZeros(incoming);
                int index = word * Long.SIZE + bit;
                pixels[index] = snapshot.pixelAt(index);
                heights[index] = snapshot.heightAt(index);
                int grass = snapshot.grassPixelAt(index);
                if (grass != 0 && grassPixels == null) grassPixels = new int[PIXEL_COUNT];
                if (grassPixels != null) grassPixels[index] = grass;
                observed[word] |= 1L << bit;
                incoming &= incoming - 1;
                changed = true;
            }
        }
        if (changed) imageRevision++;
        return changed;
    }

    public int[] getPixels() {
        int[] displayed = pixels.clone();
        if (grassTintEnabled && grassPixels != null) {
            for (int i = 0; i < displayed.length; i++) if (grassPixels[i] != 0) displayed[i] = grassPixels[i];
        }
        return displayed;
    }
    public boolean setGrassTintEnabled(boolean enabled) {
        if (grassTintEnabled == enabled) return false;
        grassTintEnabled = enabled;
        imageRevision++;
        dirty = true;
        return true;
    }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }
    public boolean hasUnsavedContent() { return contentRevision > savedContentRevision; }
    public long getImageRevision() { return imageRevision; }
    public long getContentRevision() { return contentRevision; }
    public long getSavedContentRevision() { return savedContentRevision; }
    public void markSavedContent(long revision) {
        if (revision > savedContentRevision && revision <= contentRevision) savedContentRevision = revision;
    }
    public long getLastUpdatedTick() { return lastUpdatedTick; }
    public void setLastUpdatedTick(long tick) { lastUpdatedTick = tick; }

    public Snapshot snapshot() { return new Snapshot(pixels, observed, heights, grassPixels); }

    public boolean hasObservedPixels() {
        for (long word : observed) if (word != 0) return true;
        return false;
    }

    private boolean isObserved(int index) {
        return (observed[index / Long.SIZE] & (1L << (index % Long.SIZE))) != 0;
    }

    private void markObserved(int index) {
        observed[index / Long.SIZE] |= 1L << (index % Long.SIZE);
    }

    private static int index(int localX, int localZ) {
        return (127 - localZ) * SIZE + (127 - localX);
    }

    private static boolean inBounds(int localX, int localZ) {
        return localX >= 0 && localZ >= 0 && localX < SIZE && localZ < SIZE;
    }

    /** Immutable copy of a tile's pixels and sampled-pixel mask. */
    public static final class Snapshot {
        private final int[] pixels;
        private final long[] observed;
        private final int[] heights;
        private final int[] grassPixels;

        public Snapshot(int[] pixels, long[] observed) {
            this(pixels, observed, unknownHeights());
        }

        public Snapshot(int[] pixels, long[] observed, int[] heights) {
            this(pixels, observed, heights, null);
        }

        public Snapshot(int[] pixels, long[] observed, int[] heights, int[] grassPixels) {
            if (grassPixels != null && grassPixels.length != PIXEL_COUNT) throw new IllegalArgumentException("Invalid grass colors");
            if (pixels.length != PIXEL_COUNT || observed.length != MASK_WORD_COUNT || heights.length != PIXEL_COUNT) {
                throw new IllegalArgumentException("Invalid tile snapshot dimensions");
            }
            this.pixels = pixels.clone();
            this.observed = observed.clone();
            this.heights = heights.clone();
            this.grassPixels = grassPixels == null ? null : grassPixels.clone();
        }

        public int pixelAt(int index) { return pixels[index]; }
        public int grassPixelAt(int index) { return grassPixels == null ? 0 : grassPixels[index]; }
        public int[] copyGrassPixels() { return grassPixels == null ? new int[PIXEL_COUNT] : grassPixels.clone(); }
        public int heightAt(int index) { return heights[index]; }
        public int[] copyHeights() { return heights.clone(); }
        public long observedWord(int word) { return observed[word]; }
        public int[] copyPixels() { return pixels.clone(); }
        public long[] copyObserved() { return observed.clone(); }
        public boolean hasObservedPixels() {
            for (long word : observed) if (word != 0) return true;
            return false;
        }

        public static Snapshot mergeObserved(Snapshot stored, Snapshot sampled) {
            int[] mergedPixels = stored == null ? new int[PIXEL_COUNT] : stored.copyPixels();
            int[] mergedGrass = stored == null ? new int[PIXEL_COUNT] : stored.copyGrassPixels();
            int[] mergedHeights = stored == null ? unknownHeights() : stored.copyHeights();
            long[] mergedMask = stored == null ? new long[MASK_WORD_COUNT] : stored.copyObserved();
            int[] sampledPixels = sampled.copyPixels();
            long[] sampledMask = sampled.copyObserved();
            for (int word = 0; word < mergedMask.length; word++) {
                long bits = sampledMask[word];
                while (bits != 0) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    mergedPixels[word * Long.SIZE + bit] = sampledPixels[word * Long.SIZE + bit];
                    mergedHeights[word * Long.SIZE + bit] = sampled.heightAt(word * Long.SIZE + bit);
                    mergedGrass[word * Long.SIZE + bit] = sampled.grassPixelAt(word * Long.SIZE + bit);
                    bits &= bits - 1;
                }
                mergedMask[word] |= sampledMask[word];
            }
            return new Snapshot(mergedPixels, mergedMask, mergedHeights, mergedGrass);
        }
    }
}
