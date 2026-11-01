// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

/** Height-derived presentation colors leave the cached tile and storage snapshots untouched. */
public final class TerrainReliefTest {
    private static final int BASE = 0xFF788C9A;
    private static int assertions;

    private TerrainReliefTest() {}

    public static void main(String[] args) {
        flatGroundKeepsItsOriginalColor();
        heightEdgesShowLightAndShadow();
        unknownAndTransparentPixelsDoNotCreateEdges();
        edgeNeighborsJoinAcrossTileBoundaries();
        dependencyEqualityTracksNeighborsAndHeights();
        shadingDoesNotRewriteRawPixelsOrSnapshots();
        extremeHeightDifferencesDoNotOverflow();
        System.out.println("Terrain relief tests passed (" + assertions + " assertions).");
    }

    private static void flatGroundKeepsItsOriginalColor() {
        Tile tile = solid(10, BASE);
        TerrainRelief relief = view(tile, null, null, null, null);
        for (int z : new int[]{0, 64, 127}) {
            for (int x : new int[]{0, 64, 127}) {
                check(relief.getPixel(x, z) == BASE, "flat terrain keeps the sampled map color");
            }
        }
    }

    private static void heightEdgesShowLightAndShadow() {
        Tile tile = solid(10, BASE);
        tile.setPixel(64, 64, BASE, 11);
        TerrainRelief relief = view(tile, null, null, null, null);

        int highEdge = relief.getPixel(64, 64);
        int lowShadow = relief.getPixel(64, 65);
        check(luminance(highEdge) > luminance(BASE), "the raised side catches a restrained northwest light");
        check(luminance(lowShadow) < luminance(BASE), "the lower side beside a raised block receives shadow");
        check((highEdge >>> 24) == (BASE >>> 24) && (lowShadow >>> 24) == (BASE >>> 24),
                "relief preserves the sampled alpha");

        tile.setPixel(65, 64, BASE, 18);
        int deepShadow = relief.getPixel(65, 65);
        check(luminance(deepShadow) < luminance(lowShadow), "a larger height difference casts a stronger shadow");
        check(luminance(deepShadow) >= (BASE & 255) * 0.55, "deep cliffs keep a visible color floor");
    }

    private static void unknownAndTransparentPixelsDoNotCreateEdges() {
        Tile tile = solid(Tile.UNKNOWN_HEIGHT, BASE);
        tile.setPixel(64, 64, 0x00788C9A, Tile.UNKNOWN_HEIGHT);
        tile.setPixel(63, 64, BASE, 30);
        TerrainRelief relief = view(tile, null, null, null, null);
        check(relief.getPixel(64, 64) == 0x00788C9A, "transparent pixels stay transparent at a height boundary");
        check(relief.getPixel(64, 65) == BASE, "unknown center heights remain unshaded");
        check(relief.getPixel(62, 64) == BASE, "a known height beside an unknown height does not infer a cliff");

        Tile noCenter = new Tile();
        TerrainRelief noCenterRelief = view(noCenter, null, null, null, null);
        check(noCenterRelief.getPixel(64, 64) == 0, "unobserved center pixels remain empty");
    }

    private static void edgeNeighborsJoinAcrossTileBoundaries() {
        Tile west = solid(4, BASE);
        Tile east = solid(5, BASE);
        TerrainRelief westEdge = view(west, null, east, null, null);
        TerrainRelief eastEdge = view(east, null, null, null, west);
        Tile interior = solid(4, BASE);
        fillXRange(interior, 65, 127, 5);
        TerrainRelief interiorView = view(interior, null, null, null, null);

        int worldWestPixel = westEdge.getPixel(127, 64);
        int worldEastPixel = eastEdge.getPixel(0, 64);
        check(worldWestPixel == interiorView.getPixel(64, 64),
                "a negative-coordinate west tile gets the same shadow as an interior lower block");
        check(worldEastPixel == interiorView.getPixel(65, 64),
                "an east tile gets the same highlight as an interior raised block");

        Tile middleNorth = solid(4, BASE);
        Tile north = solid(5, BASE);
        Tile northInterior = solid(4, BASE);
        fillZRange(northInterior, 0, 63, 5);
        check(view(middleNorth, north, null, null, null).getPixel(64, 0)
                        == view(northInterior, null, null, null, null).getPixel(64, 64),
                "a north tile joins the same low-side shadow as an interior height step");

        Tile middleSouth = solid(5, BASE);
        Tile south = solid(4, BASE);
        Tile southInterior = solid(5, BASE);
        fillZRange(southInterior, 65, 127, 4);
        check(view(middleSouth, null, null, south, null).getPixel(64, 127)
                        == view(southInterior, null, null, null, null).getPixel(64, 64),
                "a south tile joins the same high-side light as an interior height step");
    }

    private static void dependencyEqualityTracksNeighborsAndHeights() {
        Tile center = solid(10, BASE);
        Tile north = solid(10, BASE);
        TerrainRelief original = view(center, north, null, null, null);
        check(original.equals(view(center, north, null, null, null)), "equal tile identities and revisions reuse a relief view");
        check(!original.equals(view(center, new Tile(), null, null, null)), "neighbor replacement invalidates its dependent relief view");
        north.setPixel(10, 10, BASE, 9);
        check(!original.equals(view(center, north, null, null, null)), "neighbor height-only edits invalidate the relief view");
        check(!original.equals(view(new Tile(), north, null, null, null)), "center replacement invalidates the relief view");
        check(!original.equals(view(center, null, null, null, null)), "neighbor removal invalidates the relief view");
    }

    private static void shadingDoesNotRewriteRawPixelsOrSnapshots() {
        Tile tile = solid(10, BASE);
        int grass = 0xff55aa44;
        tile.setPixel(64, 64, BASE, 11, grass);
        tile.setGrassTintEnabled(true);
        Tile.Snapshot before = tile.snapshot();
        long contentRevision = tile.getContentRevision();
        int rawIndex = (127 - 64) * Tile.SIZE + 127 - 64;
        int tintBefore = tile.getPixel(64, 64);
        TerrainRelief relief = view(tile, null, null, null, null);
        int displayed = relief.getPixel(64, 64);

        check(displayed != tintBefore, "relief shades the visible grass-tinted terrain color");
        check(tile.snapshot().pixelAt(rawIndex) == before.pixelAt(rawIndex), "relief leaves raw cached map color unchanged");
        check(tile.snapshot().grassPixelAt(rawIndex) == before.grassPixelAt(rawIndex), "relief leaves grass color data unchanged");
        check(tile.getContentRevision() == contentRevision, "presentation shading does not edit saved content");
    }

    private static void extremeHeightDifferencesDoNotOverflow() {
        Tile low = solid(Integer.MIN_VALUE + 1, BASE);
        Tile high = solid(Integer.MAX_VALUE, BASE);
        int pixel = view(low, null, high, null, null).getPixel(127, 64);
        check((pixel >>> 24) == 255, "extreme height deltas preserve alpha and avoid overflow");
        check(luminance(pixel) >= (BASE & 255) * 0.55, "extreme height deltas stay within bounded shading");
    }

    private static Tile solid(int height, int color) {
        Tile tile = new Tile();
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) tile.setPixel(x, z, color, height);
        }
        return tile;
    }

    private static void fillXRange(Tile tile, int minX, int maxX, int height) {
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = minX; x <= maxX; x++) tile.setPixel(x, z, BASE, height);
        }
    }

    private static void fillZRange(Tile tile, int minZ, int maxZ, int height) {
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = 0; x < Tile.SIZE; x++) tile.setPixel(x, z, BASE, height);
        }
    }

    private static TerrainRelief view(Tile center, Tile north, Tile east, Tile south, Tile west) {
        return new TerrainRelief(center, north, east, south, west);
    }

    private static int luminance(int color) {
        return ((color >>> 16 & 255) * 3 + (color >>> 8 & 255) * 6 + (color & 255)) / 10;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
