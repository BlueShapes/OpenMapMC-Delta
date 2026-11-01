// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.render.FullscreenMapZoom;
import net.aosankaku.openmapdelta.render.FullscreenMapProjection;
import net.aosankaku.openmapdelta.render.FullscreenMapViewport;
import net.aosankaku.openmapdelta.render.FullscreenChunkGrid;

/** Near-player ground remains reachable while the visible self head is still selectable. */
public final class FullscreenLocalPlayerSizingTest {
    private static int assertions;

    public static void main(String[] args) {
        normalHeadIsSmallerWithoutChangingRemotePlayers();
        proximityShrinksInStableSteps();
        oneBlockHeadKeepsSelectionAndFreesAdjacentGround();
        detailZoomSharesTheTerrainAndSelectionTransform();
        System.out.println("Fullscreen self head tests passed (" + assertions + " assertions).");
    }

    private static void normalHeadIsSmallerWithoutChangingRemotePlayers() {
        for (double bpp : new double[]{0.25, 0.5, 1}) {
            check(FullscreenLocalPlayerSizing.normalSize(bpp, 16) == 12,
                    "default self head is 25% smaller at detail zoom");
            check(PlayerMarkerSizing.fullscreenSize(bpp, true, 16) == 16,
                    "remote head keeps the existing cap and size");
        }
        for (double bpp : new double[]{0.25, 0.5, 1, 2, 16}) {
            for (int cap : new int[]{4, 16, 64}) {
                int size = FullscreenLocalPlayerSizing.normalSize(bpp, cap);
                check(size <= cap && size >= 1, "self head respects the existing cap");
                check(size >= Math.ceil(1 / bpp), "normal self head is at least one world block wide");
            }
        }
    }

    private static void proximityShrinksInStableSteps() {
        for (double bpp : new double[]{0.25, 0.5, 1, 2, 16}) {
            int normal = FullscreenLocalPlayerSizing.normalSize(bpp, 16);
            check(FullscreenLocalPlayerSizing.size(normal, bpp, 1000) == normal,
                    "distant pointer keeps the ordinary self size");
            int previous = normal;
            java.util.Set<Integer> sizes = new java.util.HashSet<>();
            for (double distance = normal * 3; distance >= 0; distance -= 0.125) {
                int size = FullscreenLocalPlayerSizing.size(normal, bpp, distance);
                check(size <= previous, "approaching the center never enlarges the head");
                check(size <= normal && size >= Math.max(1, Math.ceil(1 / bpp)), "every stage stays in its bounds");
                check(size == FullscreenLocalPlayerSizing.size(normal, bpp, distance),
                        "size depends on distance, never the previous hovered size");
                sizes.add(size);
                previous = size;
            }
            check(sizes.size() >= 3, "proximity visits intermediate sizes");
            check(previous == Math.max(1, Math.ceil(1 / bpp)), "center reaches the one-block minimum");
        }
        check(FullscreenLocalPlayerSizing.size(12, 0.25, Double.NaN) == 12,
                "unknown cursor distance leaves the head readable");
    }

    private static void oneBlockHeadKeepsSelectionAndFreesAdjacentGround() {
        for (double bpp : new double[]{0.25, 0.5, 1}) {
            int normal = FullscreenLocalPlayerSizing.normalSize(bpp, 16);
            int minimum = FullscreenLocalPlayerSizing.size(normal, bpp, 0);
            check(minimum * bpp == 1, "minimum drawn face spans exactly one block");
            check(FullscreenLocalPlayerSizing.contains(100, 80, 100, 80, minimum, false),
                    "the center of the smallest visible head remains selectable");
            for (int sign : new int[]{-1, 1}) {
                double adjacentX = 100 + sign / bpp;
                double adjacentY = 80 + sign / bpp;
                int nearSize = FullscreenLocalPlayerSizing.size(normal, bpp, Math.abs(adjacentX - 100));
                check(!FullscreenLocalPlayerSizing.contains(adjacentX, 80, 100, 80, nearSize, false),
                        "the next block beside self is free for a ground context menu");
                check(!FullscreenLocalPlayerSizing.contains(100, adjacentY, 100, 80, nearSize, false),
                        "the next block above or below self is free for a ground context menu");
            }
            check(FullscreenLocalPlayerSizing.contains(100 + minimum * 0.70, 80, 100, 80, minimum, true),
                    "a visible rotated corner remains selectable");
            check(!FullscreenLocalPlayerSizing.contains(100 + normal, 80, 100, 80, minimum, false),
                    "the original head footprint leaves no invisible selection area");
        }
    }

    private static void detailZoomSharesTheTerrainAndSelectionTransform() {
        double relative = 1;
        for (double expected : new double[]{0.5, 0.25, 0.25}) {
            relative = FullscreenMapZoom.adjust(1, relative, 1);
            near(FullscreenMapZoom.blocksPerPixel(1, relative), expected, "detail zoom has bounded half steps");
        }
        near(FullscreenMapZoom.blocksPerPixel(1, FullscreenMapZoom.adjust(1, relative, -1)), 0.5,
                "zoom out returns to 2x before 1x");
        FullscreenMapViewport viewport = FullscreenMapViewport.forView(731, 511, 0.25, -17.25, 33.75);
        near(viewport.getSampleStride(), 1, "4x uses real block data without fractional resampling");
        near(viewport.renderScale(0.25), 4, "each real block draws four GUI pixels");
        near(FullscreenChunkGrid.lineSpacing(0.25), 64, "chunk grid uses the same 4x transform");
        for (int world : new int[]{-128, -18, -17, 0, 128}) {
            int pixel = FullscreenMapProjection.pixel(7, 731, -17.25, world, 0.25);
            check(FullscreenMapProjection.world(7, 731, -17.25, pixel, 0.25) == world,
                    "4x right click selects the same positive or negative world block as the marker");
            for (double currentBpp : new double[]{0.25, 0.5, 1}) {
                double terrainX = viewport.drawX(7, -17.25, currentBpp, 731)
                        + (world - viewport.getOriginX()) / viewport.getSampleStride() * viewport.renderScale(currentBpp);
                near(terrainX, 7 + 731 / 2 + (world + 17.25) / currentBpp,
                        "cached 4x terrain stays aligned with markers before a zoom-out recompose");
            }
        }
        check(viewport.containsView(-17.25, 33.75), "4x view stays within the composed terrain");
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
