// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Exercises the real fullscreen texture class against small in-memory Minecraft API fixtures. */
public final class FullscreenMapTextureTest {
    private static final int GUI_SCALE = 3;
    private static final double BPP = 4;
    private static final int PANEL_WIDTH = 192;
    private static final int PANEL_HEIGHT = 64;
    private static int assertions;

    private FullscreenMapTextureTest() {}

    public static void main(String[] args) {
        initialAndIncrementalCopiesMatchFullComposition();
        failedPartialUploadRetriesWithFullImageCopy();
        replacementTextureAfterSmallUpdateGetsAFullCopy();
        System.out.println("FullscreenMapTextureTest: " + assertions + " assertions passed");
    }

    private static void initialAndIncrementalCopiesMatchFullComposition() {
        Fixture fixture = new Fixture();
        FullscreenMapViewport view = fixture.viewport(PANEL_WIDTH, PANEL_HEIGHT);
        check(fixture.texture.compose(fixture.mc, fixture.level, view), "initial full composition succeeds");
        NativeImage image = uploadedImage();
        long fullSize = (long) view.getTextureWidth() * view.getTextureHeight();
        check(image.writeCount() == fullSize, "initial composition copies every pixel into the image");

        FullscreenTerrainRaster reference = new FullscreenTerrainRaster();
        check(reference.update(view, fixture.cache.getTiles()), "reference raster initializes");
        assertImageMatches(image, reference.getPixels(), view, "initial full copy matches raster output");
        int writesBeforeUnchanged = Math.toIntExact(image.writeCount());
        int uploadsBeforeUnchanged = DynamicTexture.uploadCount();
        check(fixture.texture.compose(fixture.mc, fixture.level, view), "unchanged composition succeeds");
        check(image.writeCount() == writesBeforeUnchanged,
                "unchanged composition writes no NativeImage pixels");
        check(DynamicTexture.uploadCount() == uploadsBeforeUnchanged,
                "unchanged composition skips the GPU upload");

        Tile changed = fixture.cache.get(fixture.dimension, 128, 0);
        check(changed != null && changed.setPixel(17, 23, 0xFFFA1327), "fixture changes one observed source pixel");
        check(reference.update(view, fixture.cache.getTiles()), "reference raster sees the changed tile");
        int expectedDirtyPixels = dirtyPixelCount(reference);
        long writesBeforeChange = image.writeCount();
        int uploadsBeforeChange = DynamicTexture.uploadCount();
        check(fixture.texture.compose(fixture.mc, fixture.level, view), "small changed-tile composition succeeds");
        long changedWrites = image.writeCount() - writesBeforeChange;
        check(changedWrites == expectedDirtyPixels,
                "incremental NativeImage copy is restricted to the raster dirty rectangle");
        check(changedWrites < fullSize, "one changed tile copies fewer pixels than a full texture");
        check(DynamicTexture.uploadCount() == uploadsBeforeChange + 1,
                "changed terrain still uploads the updated GPU texture");
        assertImageMatches(image, reference.getPixels(), view, "incremental image matches recomposed raster");
        assertMatchesFreshFullComposition(fixture, view, image, "changed image equals a fresh full compose");

        check(fixture.cache.remove(fixture.dimension, 128, 0), "fixture removes a visible tile");
        check(reference.update(view, fixture.cache.getTiles()), "reference raster sees removed terrain");
        int expectedRemovalPixels = dirtyPixelCount(reference);
        long writesBeforeRemoval = image.writeCount();
        check(fixture.texture.compose(fixture.mc, fixture.level, view), "removed-tile composition succeeds");
        check(image.writeCount() - writesBeforeRemoval == expectedRemovalPixels,
                "removed tile also copies only its affected texture rectangle");
        assertImageMatches(image, reference.getPixels(), view, "removed tile clears all affected pixels");
        int removedX = (int) Math.floor((128 - view.getOriginX()) / view.getSampleStride());
        int removedZ = (int) Math.floor((0 - view.getOriginZ()) / view.getSampleStride());
        check(image.pixelAt(removedX, removedZ) == 0, "removed source terrain clears its former texture pixels");
        assertMatchesFreshFullComposition(fixture, view, image, "removed-tile image equals a fresh full compose");
    }

    private static void failedPartialUploadRetriesWithFullImageCopy() {
        Fixture fixture = new Fixture();
        FullscreenMapViewport view = fixture.viewport(PANEL_WIDTH, PANEL_HEIGHT);
        check(fixture.texture.compose(fixture.mc, fixture.level, view), "retry fixture initially composes");
        NativeImage image = uploadedImage();
        FullscreenTerrainRaster reference = new FullscreenTerrainRaster();
        reference.update(view, fixture.cache.getTiles());

        fixture.cache.get(fixture.dimension, 128, 0).setPixel(17, 23, 0xFF0084E1);
        reference.update(view, fixture.cache.getTiles());
        long fullSize = (long) view.getTextureWidth() * view.getTextureHeight();
        long beforeFailureCopy = image.writeCount();
        int uploadsBeforeFailure = DynamicTexture.uploadCount();
        DynamicTexture.failNextUpload();
        check(!fixture.texture.compose(fixture.mc, fixture.level, view),
                "simulated upload failure is reported to the caller");
        check(!fixture.texture.isTextureReady(), "failed partial upload marks the GPU texture unready");
        check(image.writeCount() - beforeFailureCopy < fullSize,
                "first failed attempt copies only the changed rectangle");
        check(DynamicTexture.uploadCount() == uploadsBeforeFailure,
                "failed upload is not counted as a successful GPU upload");

        long beforeRetryCopy = image.writeCount();
        check(fixture.texture.compose(fixture.mc, fixture.level, view), "next call retries and succeeds");
        check(fixture.texture.isTextureReady(), "successful retry restores ready state");
        check(image.writeCount() - beforeRetryCopy == fullSize,
                "retry after failure fully refreshes the image instead of reusing stale dirty bounds");
        check(DynamicTexture.uploadCount() == uploadsBeforeFailure + 1,
                "successful retry publishes exactly one GPU upload");
        assertImageMatches(image, reference.getPixels(), view, "retried image contains every current raster pixel");
        assertMatchesFreshFullComposition(fixture, view, image, "retried image equals a fresh full compose");
    }

    private static void replacementTextureAfterSmallUpdateGetsAFullCopy() {
        Fixture fixture = new Fixture();
        FullscreenMapViewport originalView = fixture.viewport(PANEL_WIDTH, PANEL_HEIGHT);
        check(fixture.texture.compose(fixture.mc, fixture.level, originalView), "resize fixture initially composes");
        NativeImage originalImage = uploadedImage();
        DynamicTexture originalTexture = DynamicTexture.lastUploaded();

        fixture.cache.get(fixture.dimension, 128, 0).setPixel(17, 23, 0xFFDA8040);
        long beforeSmallUpdate = originalImage.writeCount();
        check(fixture.texture.compose(fixture.mc, fixture.level, originalView), "resize fixture applies a small update");
        check(originalImage.writeCount() - beforeSmallUpdate
                        < (long) originalView.getTextureWidth() * originalView.getTextureHeight(),
                "pre-resize update uses a small dirty copy");

        FullscreenMapViewport resizedView = fixture.viewport(128, PANEL_HEIGHT);
        check(fixture.texture.compose(fixture.mc, fixture.level, resizedView), "resized texture composes");
        NativeImage replacementImage = uploadedImage();
        long resizedFullSize = (long) resizedView.getTextureWidth() * resizedView.getTextureHeight();
        check(replacementImage != originalImage, "resize registers a new CPU image");
        check(replacementImage.writeCount() == resizedFullSize,
                "new image receives a full copy even after the prior composition was incremental");
        check(originalTexture.isClosed(), "resize releases the previous dynamic texture");
        assertMatchesFreshFullComposition(fixture, resizedView, replacementImage,
                "replacement image equals a fresh full compose");
    }

    private static int dirtyPixelCount(FullscreenTerrainRaster raster) {
        return (raster.getDirtyMaxX() - raster.getDirtyMinX())
                * (raster.getDirtyMaxZ() - raster.getDirtyMinZ());
    }

    private static NativeImage uploadedImage() {
        DynamicTexture texture = DynamicTexture.lastUploaded();
        check(texture != null, "a GPU texture upload was recorded");
        return texture.image();
    }

    private static void assertImageMatches(NativeImage image, int[] expected,
                                           FullscreenMapViewport view, String message) {
        check(expected.length == view.getTextureWidth() * view.getTextureHeight(),
                "reference raster has the expected dimensions");
        int[] actual = image.copyPixels();
        for (int index = 0; index < expected.length; index++) {
            if (actual[index] != expected[index]) {
                throw new AssertionError(message + " at pixel " + index + ": expected 0x"
                        + Integer.toHexString(expected[index]) + " but got 0x" + Integer.toHexString(actual[index]));
            }
            assertions++;
        }
    }

    private static void assertMatchesFreshFullComposition(Fixture fixture, FullscreenMapViewport view,
                                                           NativeImage actual, String message) {
        Minecraft freshMc = new Minecraft();
        freshMc.level = new ClientLevel(fixture.dimension);
        FullscreenMapTexture freshTexture = new FullscreenMapTexture();
        check(freshTexture.compose(freshMc, freshMc.level, view), "fresh full texture compose succeeds");
        NativeImage freshImage = uploadedImage();
        check(freshImage.writeCount() == (long) view.getTextureWidth() * view.getTextureHeight(),
                "fresh reference performs a full image copy");
        int[] expected = freshImage.copyPixels();
        assertImageMatches(actual, expected, view, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final Object dimension = new Object();
        final TileCache cache = new TileCache();
        final Minecraft mc = new Minecraft();
        final ClientLevel level = new ClientLevel(dimension);
        final FullscreenMapTexture texture = new FullscreenMapTexture();

        Fixture() {
            mc.level = level;
            Minecraft.setInstance(mc);
            OpenMapDeltaClient.reset();
            for (int x = -384; x <= 384; x += Tile.SIZE) {
                for (int z = -128; z <= 128; z += Tile.SIZE) fillTile(x, z);
            }
            selectCache();
        }

        FullscreenMapViewport viewport(int width, int height) {
            return FullscreenMapViewport.forView(width, height, BPP, 0, 0, GUI_SCALE);
        }

        private void fillTile(int alignedX, int alignedZ) {
            Tile tile = cache.getOrCreate(dimension, alignedX, alignedZ);
            int color = 0xFF000000 | ((alignedX + 512) & 255) << 16
                    | ((alignedZ + 512) & 255) << 8 | ((alignedX ^ alignedZ) & 255);
            for (int z = 0; z < Tile.SIZE; z++) {
                for (int x = 0; x < Tile.SIZE; x++) tile.setPixel(x, z, color);
            }
        }

        private void selectCache() {
            OpenMapDeltaClient.select(cache, cache, OpenMapDeltaClient.SURFACE_LAYER);
        }
    }
}
