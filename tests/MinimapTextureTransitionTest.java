// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileCache;
import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class MinimapTextureTransitionTest {
    private static final int SIZE = 4;
    private static final int A = 0xffb86a32;
    private static final int B = 0xff42a6d0;
    private static final int C = 0xff65c458;
    private static int assertions;

    private MinimapTextureTransitionTest() {}

    public static void main(String[] args) {
        testPhysicalTextureSizingBounds();
        testPhysicalResolutionKeepsWorldCoverageAndSamplesFiner();
        testPhysicalResolutionDoesNotInventSubBlockPixels();
        testPhysicalCompositionInvalidatesOnZoomChangeAtUnitSampling();
        testPhysicalCompositionReusesUnchangedUploadsAndTracksCacheChanges();
        testPhysicalUploadFailureRetries();
        testPhysicalTextureRegistrationsStayBoundedAcrossResizes();
        testShapeChangeRecomposesPhysicalMask();
        testPhysicalAndLegacyCompositionsShadeCachedHeightEdges();
        testCaveBandKeepsLastObservedPixels();
        testSurfaceToCaveKeepsObservedSurface();
        testObservedReplacementAndTransparentZero();
        testPanReprojectsRetainedWorldCoordinates();
        testStrideTwoOneBlockPanUsesNearestRetainedSample();
        testSameCacheObjectCanChangeBand();
        testSurfacePolicyDropsCavePixels();
        testLevelEpochStrideAndClearInvalidateRetention();
        System.out.println("MinimapTextureTransitionTest: " + assertions + " assertions passed");
    }

    private static void testPhysicalTextureSizingBounds() {
        check(MinimapSizing.textureSize(128, 4, 383.2) == 384,
                "physical texture sizing rounds up to an even pixel dimension");
        check(MinimapSizing.textureSize(128, 2, 500) == 256,
                "physical texture sizing does not exceed the world block span");
        check(MinimapSizing.textureSize(128, 16, 9000) == 2048,
                "physical texture sizing respects the existing texture cap");
        check(MinimapSizing.textureSize(4, 1, 8) == 4,
                "physical texture sizing does not invent sub-block terrain samples");
    }

    private static void testPhysicalResolutionKeepsWorldCoverageAndSamplesFiner() {
        Fixture f = new Fixture();
        RuntimeClientState.setZoomBlocksPerPixel(4);
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        // The player is at block (-1, 0); the unchanged 4x4 logical map covers
        // sixteen blocks in each direction, with a fractional 4/3-block raster step.
        fillWorldRange(f.cacheA, f.dimension, -9, -8, 16, 16);
        f.select(f.cacheA, 8);

        f.texture.compose(f.mc, SIZE, 12.0);

        NativeImage rendered = image();
        check(rendered.getWidth() == 12 && rendered.getHeight() == 12,
                "physical composition uses the requested even raster dimensions");
        check(rendered.pixelAt(0, 6) == colorForWorldCoordinate(-9, 0),
                "physical raster keeps the original negative-coordinate left edge");
        check(rendered.pixelAt(1, 6) == colorForWorldCoordinate(-8, 0),
                "fractional raster samples terrain between legacy logical pixels");
        check(rendered.pixelAt(11, 6) == colorForWorldCoordinate(5, 0),
                "physical raster keeps the original right-hand world coverage");
        check(rendered.pixelAt(6, 6) == colorForWorldCoordinate(-1, 0),
                "physical raster remains centered on the same floored player block");
    }

    private static void testPhysicalResolutionDoesNotInventSubBlockPixels() {
        Fixture f = new Fixture();
        RuntimeClientState.setZoomBlocksPerPixel(1);
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        fillWorldRange(f.cacheA, f.dimension, -3, -2, 4, 4);
        f.select(f.cacheA, 8);

        f.texture.compose(f.mc, SIZE, 8.0);

        NativeImage rendered = image();
        check(rendered.getWidth() == SIZE && rendered.getHeight() == SIZE,
                "physical composition caps raster density at one sample per world block");
        check(rendered.pixelAt(0, 2) == colorForWorldCoordinate(-3, 0),
                "capped raster preserves the original left world coordinate");
        check(rendered.pixelAt(3, 2) == colorForWorldCoordinate(0, 0),
                "capped raster preserves the original right world coordinate");
    }

    private static void testPhysicalCompositionInvalidatesOnZoomChangeAtUnitSampling() {
        Fixture f = new Fixture();
        RuntimeClientState.setZoomBlocksPerPixel(3);
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        fillWorldRange(f.cacheA, f.dimension, -6, -6, 12, 12);
        f.select(f.cacheA, 8);
        f.texture.compose(f.mc, SIZE, 32.0);
        check(image().getWidth() == 12, "initial zoom uses one physical texel per world block");

        RuntimeClientState.setZoomBlocksPerPixel(4);
        f.select(new TileCache(), 16);
        f.texture.compose(f.mc, SIZE, 32.0);

        check(image().getWidth() == 16, "zoom change updates the physical raster density");
        check(image().pixelAt(8, 8) == 0,
                "zoom change clears retained pixels even when both sample steps are one");
    }

    private static void testPhysicalCompositionReusesUnchangedUploadsAndTracksCacheChanges() {
        Fixture f = new Fixture();
        RuntimeClientState.setZoomBlocksPerPixel(1);
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        fillWorldRange(f.cacheA, f.dimension, -3, -2, 4, 4);
        f.select(f.cacheA, 8);
        f.texture.compose(f.mc, SIZE, 8.0);
        int afterFirstUpload = DynamicTexture.uploadCount();

        f.texture.compose(f.mc, SIZE, 8.0);
        check(DynamicTexture.uploadCount() == afterFirstUpload,
                "unchanged physical composition reuses its uploaded texture");

        put(f.cacheA, f.dimension, -1, 0, B);
        f.texture.compose(f.mc, SIZE, 8.0);
        check(DynamicTexture.uploadCount() == afterFirstUpload + 1,
                "a changed tile revision triggers a new physical upload");
        check(pixel(2, 2) == B, "a changed tile revision refreshes the sampled pixel");

        TileCache differentCache = new TileCache();
        fillWorldRange(differentCache, f.dimension, -3, -2, 4, 4, C);
        f.select(differentCache, 8);
        f.texture.compose(f.mc, SIZE, 8.0);
        check(pixel(2, 2) == C,
                "switching cache objects recomposes even with matching layer and revision");
    }

    private static void testShapeChangeRecomposesPhysicalMask() {
        Fixture f = new Fixture();
        RuntimeClientState.setZoomBlocksPerPixel(1);
        fillWorldRange(f.cacheA, f.dimension, -3, -2, 4, 4, A);
        f.select(f.cacheA, 8);
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.CIRCLE);
        f.texture.compose(f.mc, SIZE, 8.0);
        check(pixel(0, 0) == 0, "circular shape mask is applied at the physical raster size");
        int uploadsBeforeShapeChange = DynamicTexture.uploadCount();

        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        f.texture.compose(f.mc, SIZE, 8.0);

        check(DynamicTexture.uploadCount() == uploadsBeforeShapeChange + 1,
                "shape configuration invalidates physical composition caching");
        check(pixel(0, 0) == A, "square shape removes the previously cached circular mask");
    }

    private static void testPhysicalUploadFailureRetries() {
        Fixture f = new Fixture();
        RuntimeClientState.setZoomBlocksPerPixel(1);
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        fillWorldRange(f.cacheA, f.dimension, -3, -2, 4, 4, A);
        f.select(f.cacheA, 8);
        f.texture.compose(f.mc, SIZE, 8.0);
        int uploadsBeforeChange = DynamicTexture.uploadCount();

        put(f.cacheA, f.dimension, -1, 0, B);
        DynamicTexture.failNextUpload();
        f.texture.compose(f.mc, SIZE, 8.0);
        check(!f.texture.isTextureReady(), "failed physical upload leaves the texture marked for retry");
        check(DynamicTexture.uploadCount() == uploadsBeforeChange,
                "failed physical upload is not counted as a successful update");

        f.texture.compose(f.mc, SIZE, 8.0);
        check(f.texture.isTextureReady(), "the next physical composition retries the failed upload");
        check(DynamicTexture.uploadCount() == uploadsBeforeChange + 1,
                "retry uploads the latest physical image once");
        check(pixel(2, 2) == B, "retry publishes the current tile revision");
    }

    private static void testPhysicalTextureRegistrationsStayBoundedAcrossResizes() {
        Fixture f = new Fixture();
        OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
        f.select(f.cacheA, 8);
        RuntimeClientState.setZoomBlocksPerPixel(1);
        f.texture.compose(f.mc, SIZE, 32.0);
        check(f.mc.getTextureManager().activeTextureCount() == 1,
                "initial physical texture has one active registration");

        RuntimeClientState.setZoomBlocksPerPixel(2);
        f.texture.compose(f.mc, SIZE, 32.0);
        check(f.mc.getTextureManager().activeTextureCount() == 1,
                "resizing physical texture releases its prior registration");

        RuntimeClientState.setZoomBlocksPerPixel(3);
        f.texture.compose(f.mc, SIZE, 32.0);
        RuntimeClientState.setZoomBlocksPerPixel(4);
        f.texture.compose(f.mc, SIZE, 32.0);
        check(f.mc.getTextureManager().activeTextureCount() == 1,
                "repeated physical texture resizes keep registrations bounded");

        f.texture.clear();
        check(f.mc.getTextureManager().activeTextureCount() == 0,
                "clearing physical texture releases its registration");
    }

    private static void testCaveBandKeepsLastObservedPixels() {
        Fixture f = new Fixture();
        fill(f.cacheA, f.dimension, 0, 0, 1, A);
        f.select(f.cacheA, 8);
        render(f.texture, f.mc, 0, 0, 1);
        assertGrid(A, "initial cave pixels render");

        TileCache emptyBand = new TileCache();
        f.select(emptyBand, 16);
        render(f.texture, f.mc, 0, 0, 1);
        assertGrid(A, "cave Y-band transition retains the previous observed map");
    }

    private static void testSurfaceToCaveKeepsObservedSurface() {
        Fixture f = new Fixture();
        fill(f.surfaceCache, f.dimension, 0, 0, 1, A);
        f.select(f.surfaceCache, OpenMapDeltaClient.SURFACE_LAYER);
        render(f.texture, f.mc, 0, 0, 1);
        f.select(new TileCache(), 16);
        render(f.texture, f.mc, 0, 0, 1);
        assertGrid(A, "entering cover retains Surface pixels while cave band loads");
    }

    private static void testObservedReplacementAndTransparentZero() {
        Fixture f = new Fixture();
        fill(f.cacheA, f.dimension, 0, 0, 1, A);
        f.select(f.cacheA, 8);
        render(f.texture, f.mc, 0, 0, 1);
        TileCache nextBand = new TileCache();
        f.select(nextBand, 16);
        render(f.texture, f.mc, 0, 0, 1);
        put(nextBand, f.dimension, 0, 0, B);
        put(nextBand, f.dimension, -1, -1, 0);
        long beforeFallbackRender = contentRevision(nextBand);
        render(f.texture, f.mc, 0, 0, 1);

        check(pixel(2, 2) == B, "newly observed color replaces retained color immediately");
        check(pixel(1, 1) == 0, "newly observed transparent zero clears retained color");
        check(pixel(0, 0) == A, "unobserved pixel keeps its previous cave color");
        check(contentRevision(nextBand) == beforeFallbackRender,
                "render fallback does not write retained pixels into the tile cache");

        fill(nextBand, f.dimension, 0, 0, 1, C);
        render(f.texture, f.mc, 0, 0, 1);
        assertGrid(C, "completed new band replaces every retained pixel");
    }

    private static void testPanReprojectsRetainedWorldCoordinates() {
        Fixture f = new Fixture();
        fillByWorldX(f.cacheA, f.dimension, 0, 0, 1);
        f.select(f.cacheA, 8);
        render(f.texture, f.mc, 0, 0, 1);
        TileCache nextBand = new TileCache();
        f.select(nextBand, 16);
        render(f.texture, f.mc, 1, 0, 1);
        check(pixel(0, 2) == colorForWorldX(-1), "pan fallback is looked up by world coordinate");
        check(pixel(1, 2) == colorForWorldX(0), "pan shifts the retained map with world space");
        check(pixel(2, 2) == colorForWorldX(1), "pan does not leave old colors stuck on screen");
    }

    private static void testStrideTwoOneBlockPanUsesNearestRetainedSample() {
        Fixture f = new Fixture();
        fillByWorldCoordinate(f.cacheA, f.dimension, 0, 0, 2);
        f.select(f.cacheA, 8);
        render(f.texture, f.mc, 0, 0, 2);

        f.select(new TileCache(), 16);
        render(f.texture, f.mc, 1, 1, 2);

        // The new sample positions are -3,-1,1,3. Nearest old samples are
        // -2,0,2; the final row and column lie beyond the retained frame.
        for (int py = 0; py < 3; py++) {
            for (int px = 0; px < 3; px++) {
                int nearestWorldX = -2 + px * 2;
                int nearestWorldZ = -2 + py * 2;
                check(pixel(px, py) == colorForWorldCoordinate(nearestWorldX, nearestWorldZ),
                        "stride-two pan uses nearest retained world sample at " + px + "," + py);
            }
        }
        for (int i = 0; i < SIZE; i++) {
            check(pixel(SIZE - 1, i) == 0, "right edge outside retained bounds stays transparent");
            check(pixel(i, SIZE - 1) == 0, "bottom edge outside retained bounds stays transparent");
        }
    }

    private static void testSameCacheObjectCanChangeBand() {
        Fixture f = new Fixture();
        fill(f.cacheA, f.dimension, 0, 0, 1, A);
        f.select(f.cacheA, 8);
        render(f.texture, f.mc, 0, 0, 1);
        f.cacheA.clear();
        f.select(f.cacheA, 16);
        render(f.texture, f.mc, 0, 0, 1);
        assertGrid(A, "band transition retains pixels when the cache owner object is reused");
    }

    private static void testSurfacePolicyDropsCavePixels() {
        Fixture f = new Fixture();
        fill(f.cacheA, f.dimension, 0, 0, 1, A);
        f.select(f.cacheA, 8);
        render(f.texture, f.mc, 0, 0, 1);
        f.select(f.surfaceCache, OpenMapDeltaClient.SURFACE_LAYER);
        render(f.texture, f.mc, 0, 0, 1);
        assertGrid(0, "server surface policy never shows prior cave pixels");
    }

    private static void testPhysicalAndLegacyCompositionsShadeCachedHeightEdges() {
        Fixture f = new Fixture();
        f.mc.player = new LocalPlayer(63.75, 0.5);
        fillReliefLowEdge(f.cacheA, f.dimension);
        f.select(f.cacheA, 8);

        int uploadsBefore = DynamicTexture.uploadCount();
        f.texture.compose(f.mc, SIZE, 12.0);
        NativeImage physical = image();
        check(physical.getWidth() == SIZE, "physical minimap keeps its existing world-span density cap");
        check(DynamicTexture.uploadCount() == uploadsBefore + 1, "first physical minimap image uploads once");
        int originalLowEdge = physical.pixelAt(2, 2);
        check(originalLowEdge == A, "missing height neighbors leave the known flat edge unchanged");
        check(physical.pixelAt(3, 2) == 0, "missing terrain keeps the unexplored side transparent");
        int afterFirstPhysicalUpload = DynamicTexture.uploadCount();
        f.texture.compose(f.mc, SIZE, 12.0);
        check(DynamicTexture.uploadCount() == afterFirstPhysicalUpload, "unchanged physical relief skips an upload");
        check(image() == physical, "unchanged height relief keeps the physical minimap image");

        fillReliefHighEdge(f.cacheA, f.dimension);
        f.texture.compose(f.mc, SIZE, 12.0);
        physical = image();
        check(DynamicTexture.uploadCount() == afterFirstPhysicalUpload + 1, "arriving high terrain triggers one physical upload");
        check(luminance(physical.pixelAt(2, 2)) < luminance(A), "physical minimap shades the low side of a height step");
        check(luminance(physical.pixelAt(3, 2)) > luminance(A), "physical minimap lights the raised side of a height step");

        f.texture.compose(f.mc, SIZE, SIZE, 63.75, 0.5, false, 1);
        NativeImage legacy = image();
        check(luminance(legacy.pixelAt(2, 2)) < luminance(A), "legacy minimap shades the low side of a height step");
        check(luminance(legacy.pixelAt(3, 2)) > luminance(A), "legacy minimap lights the raised side of a height step");

        Tile high = f.cacheA.get(f.dimension, 128, 0);
        int oldHigh = legacy.pixelAt(3, 2);
        high.setPixel(0, 64, A, 18);
        int beforeHeightEdit = DynamicTexture.uploadCount();
        f.texture.compose(f.mc, SIZE, 12.0);
        physical = image();
        check(DynamicTexture.uploadCount() == beforeHeightEdit + 1, "physical image refreshes after a neighboring height-only edit");
        check(physical.pixelAt(3, 2) != oldHigh, "height-only edit changes the raised boundary shading");

        f.cacheA.remove(f.dimension, 128, 0);
        Tile replacement = f.cacheA.getOrCreate(f.dimension, 128, 0);
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) replacement.setPixel(x, z, A, 12);
        }
        int beforeReplacement = DynamicTexture.uploadCount();
        f.texture.compose(f.mc, SIZE, 12.0);
        check(DynamicTexture.uploadCount() == beforeReplacement + 1, "neighbor replacement refreshes cached physical relief");

        f.cacheA.remove(f.dimension, 128, 0);
        int beforeRemoval = DynamicTexture.uploadCount();
        f.texture.compose(f.mc, SIZE, 12.0);
        physical = image();
        check(DynamicTexture.uploadCount() == beforeRemoval + 1, "neighbor removal uploads the cleared relief edge");
        check(physical.pixelAt(2, 2) == originalLowEdge && physical.pixelAt(3, 2) == 0,
                "neighbor removal restores the low terrain color and transparent empty edge");
    }

    private static void fillReliefLowEdge(TileCache cache, Object dimension) {
        for (int z = -4; z <= 4; z++) {
            for (int x = 59; x <= 63; x++) {
                int alignedX = TileCache.alignTile(x);
                int alignedZ = TileCache.alignTile(z);
                Tile tile = cache.getOrCreate(dimension, alignedX, alignedZ);
                tile.setPixel(x - alignedX + 64, z - alignedZ + 64, A, 10);
            }
        }
    }

    private static void fillReliefHighEdge(TileCache cache, Object dimension) {
        for (int z = -4; z <= 4; z++) {
            for (int x = 64; x <= 68; x++) {
                int alignedX = TileCache.alignTile(x);
                int alignedZ = TileCache.alignTile(z);
                Tile tile = cache.getOrCreate(dimension, alignedX, alignedZ);
                tile.setPixel(x - alignedX + 64, z - alignedZ + 64, A, 11);
            }
        }
    }

    private static int luminance(int color) {
        return ((color >>> 16 & 255) * 3 + (color >>> 8 & 255) * 6 + (color & 255)) / 10;
    }

    private static void testLevelEpochStrideAndClearInvalidateRetention() {
        Fixture levelChange = new Fixture();
        fill(levelChange.cacheA, levelChange.dimension, 0, 0, 1, A);
        levelChange.select(levelChange.cacheA, 8);
        render(levelChange.texture, levelChange.mc, 0, 0, 1);
        levelChange.mc.level = new ClientLevel(levelChange.dimension);
        levelChange.select(new TileCache(), 16);
        render(levelChange.texture, levelChange.mc, 0, 0, 1);
        assertGrid(0, "a different level object invalidates prior pixels");

        Fixture epochChange = new Fixture();
        fill(epochChange.cacheA, epochChange.dimension, 0, 0, 1, A);
        epochChange.select(epochChange.cacheA, 8);
        render(epochChange.texture, epochChange.mc, 0, 0, 1);
        epochChange.surfaceCache.clear();
        epochChange.select(new TileCache(), 16);
        render(epochChange.texture, epochChange.mc, 0, 0, 1);
        assertGrid(0, "surface cache epoch change invalidates prior pixels");

        Fixture strideChange = new Fixture();
        fill(strideChange.cacheA, strideChange.dimension, 0, 0, 1, A);
        strideChange.select(strideChange.cacheA, 8);
        render(strideChange.texture, strideChange.mc, 0, 0, 1);
        strideChange.select(new TileCache(), 16);
        render(strideChange.texture, strideChange.mc, 0, 0, 2);
        assertGrid(0, "stride change invalidates prior pixels");

        Fixture clear = new Fixture();
        fill(clear.cacheA, clear.dimension, 0, 0, 1, A);
        clear.select(clear.cacheA, 8);
        render(clear.texture, clear.mc, 0, 0, 1);
        clear.texture.clear();
        clear.select(new TileCache(), 16);
        render(clear.texture, clear.mc, 0, 0, 1);
        assertGrid(0, "texture clear drops retained pixels");
    }

    private static void render(MinimapTexture texture, Minecraft mc, int centerX, int centerZ, int stride) {
        texture.compose(mc, SIZE, SIZE, centerX, centerZ, false, stride);
        check(texture.isTextureReady(), "texture uploaded after composition");
    }

    private static void fill(TileCache cache, Object dimension, int centerX, int centerZ, int stride, int color) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                put(cache, dimension, worldX(centerX, px, stride), worldZ(centerZ, py, stride), color);
            }
        }
    }

    private static void fillWorldRange(TileCache cache, Object dimension, int startX, int startZ,
                                       int width, int height) {
        fillWorldRange(cache, dimension, startX, startZ, width, height, 0);
    }

    private static void fillWorldRange(TileCache cache, Object dimension, int startX, int startZ,
                                       int width, int height, int fixedColor) {
        for (int z = startZ; z < startZ + height; z++) {
            for (int x = startX; x < startX + width; x++) {
                int color = fixedColor == 0 ? colorForWorldCoordinate(x, z) : fixedColor;
                put(cache, dimension, x, z, color);
            }
        }
    }

    private static void fillByWorldX(TileCache cache, Object dimension, int centerX, int centerZ, int stride) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                int x = worldX(centerX, px, stride);
                put(cache, dimension, x, worldZ(centerZ, py, stride), colorForWorldX(x));
            }
        }
    }

    private static void fillByWorldCoordinate(TileCache cache, Object dimension, int centerX, int centerZ, int stride) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                int x = worldX(centerX, px, stride);
                int z = worldZ(centerZ, py, stride);
                put(cache, dimension, x, z, colorForWorldCoordinate(x, z));
            }
        }
    }

    private static void put(TileCache cache, Object dimension, int worldX, int worldZ, int color) {
        int alignedX = TileCache.alignTile(worldX);
        int alignedZ = TileCache.alignTile(worldZ);
        Tile tile = cache.getOrCreate(dimension, alignedX, alignedZ);
        tile.setPixel(worldX - alignedX + 64, worldZ - alignedZ + 64, color);
    }

    private static int worldX(int centerX, int px, int stride) { return centerX + (px - SIZE / 2) * stride; }
    private static int worldZ(int centerZ, int py, int stride) { return centerZ + (py - SIZE / 2) * stride; }
    private static int colorForWorldX(int worldX) { return 0xff000000 | ((worldX + 16) << 16) | 0x00543210; }
    private static int colorForWorldCoordinate(int worldX, int worldZ) {
        return 0xff00005a | (((worldX + 128) & 0xff) << 16) | (((worldZ + 128) & 0xff) << 8);
    }

    private static long contentRevision(TileCache cache) {
        return cache.tiles().stream().mapToLong(Tile::getContentRevision).sum();
    }

    private static NativeImage image() {
        NativeImage image = net.minecraft.client.renderer.texture.DynamicTexture.lastUploaded().image();
        check(image != null, "composed image was uploaded");
        return image;
    }

    private static int pixel(int x, int y) { return image().pixelAt(x, y); }

    private static void assertGrid(int color, String message) {
        NativeImage image = image();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (image.pixelAt(x, y) != color) {
                    throw new AssertionError(message + ": expected 0x" + Integer.toHexString(color)
                            + " at (" + x + "," + y + ") but got 0x" + Integer.toHexString(image.pixelAt(x, y)));
                }
                assertions++;
            }
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final Object dimension = new Object();
        final TileCache surfaceCache = new TileCache();
        final TileCache cacheA = new TileCache();
        final Minecraft mc = new Minecraft();
        final MinimapTexture texture = new MinimapTexture(surfaceCache);

        Fixture() {
            RuntimeClientState.setZoomBlocksPerPixel(1);
            OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.CIRCLE);
            OpenMapDeltaClient.select(surfaceCache, surfaceCache, OpenMapDeltaClient.SURFACE_LAYER);
            mc.level = new ClientLevel(dimension);
            mc.player = new LocalPlayer(-0.25, 0.75);
            Minecraft.setInstance(mc);
        }

        void select(TileCache cache, int layer) {
            OpenMapDeltaClient.select(surfaceCache, cache, layer);
        }
    }
}
