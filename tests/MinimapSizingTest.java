// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.config.MinimapInfoSlot;
import net.aosankaku.openmapdelta.config.MinimapInfoType;
import net.aosankaku.openmapdelta.platform.services.IPlatformHelper;

import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone sizing/config checks; no Minecraft client or test framework is needed. */
public final class MinimapSizingTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        displaySizes();
        geometry();
        circleTerrainMeetsBorder();
        compassMargins();
        minimapTextMinimum();
        config();
        minimapPlayerIconConfig();
        independentMinimapPlayerIcons();
        fullscreenPlayerIconConfig();
        waypointConfig();
        createStationGroupingRadiusConfig();
        waypointLabelConfig();
        minimapWaypointPresentationConfig();
        layerAndNavigationConfig();
        fullscreenAndGenerationConfig();
        fullscreenWaypointLabelScaleConfig();
        minimapInformationConfig();
        minimapInformationLayout();
        zoom();
        System.out.println("Minimap sizing tests passed (" + assertions + " assertions).");
    }

    private static void independentMinimapPlayerIcons() throws Exception {
        var head = OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD;
        var pointer = OpenMapDeltaConfig.MinimapPlayerIcon.VANILLA_POINTER;
        var triangle = OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE;
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        check(OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.getDefault() == head,
                "other players default to heads independently of the self selector");

        OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(head);
        OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.set(pointer);
        Files.writeString(path, "{\"enabled\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == head
                        && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == head,
                "configs predating any icon selector keep the initial shared head appearance");

        for (var icon : OpenMapDeltaConfig.MinimapPlayerIcon.values()) {
            for (boolean nested : new boolean[]{false, true}) {
                OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(head);
                OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.set(pointer);
                String legacy = "{\"minimapPlayerIcon\":\"" + icon.name().toLowerCase(java.util.Locale.ROOT) + "\"}";
                Files.writeString(path, nested ? "{\"map\":" + legacy + "}" : legacy);
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == icon
                                && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == icon,
                        "legacy shared preference migrates to self and others: " + icon + ", nested=" + nested);
            }
        }

        OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.set(triangle);
        for (var self : OpenMapDeltaConfig.MinimapPlayerIcon.values()) {
            for (var other : OpenMapDeltaConfig.MinimapPlayerIcon.values()) {
                OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(self);
                OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.set(other);
                OpenMapDeltaConfig.save();
                OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(head);
                OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.set(head);
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == self
                                && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == other,
                        "independent icon choices survive save/reload: self=" + self + ", others=" + other);
                check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == triangle,
                        "minimap icon changes preserve fullscreen selection");
            }
        }

        Files.writeString(path, """
                {"minimapPlayerIcon":"triangle","minimapOtherPlayerIcon":"vanilla_pointer",
                 "map":{"minimapPlayerIcon":"player_head","minimapOtherPlayerIcon":"triangle"}}
                """);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == head
                        && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == triangle,
                "nested icon selectors take precedence consistently over flat selectors");
        Files.writeString(path, """
                {"minimapOtherPlayerIcon":"vanilla_pointer","map":{"minimapPlayerIcon":"triangle"}}
                """);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == triangle
                        && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == pointer,
                "explicit other icon is retained when the self icon comes from legacy nested settings");

        for (String invalid : new String[]{"null", "{}", "[]", "1", "true", "\"unknown\""}) {
            for (boolean nested : new boolean[]{false, true}) {
                OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.set(triangle);
                String settings = "{\"minimapPlayerIcon\":\"vanilla_pointer\",\"minimapOtherPlayerIcon\":" + invalid + "}";
                Files.writeString(path, nested ? "{\"map\":" + settings + "}" : settings);
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == pointer
                                && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == head,
                        "invalid explicit other selector uses its own default: " + invalid + ", nested=" + nested);
            }
            Files.writeString(path, "{\"minimapPlayerIcon\":" + invalid
                    + ",\"minimapOtherPlayerIcon\":\"triangle\"}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == head
                            && OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON.get() == triangle,
                    "invalid self selector does not overwrite the explicit other selector: " + invalid);
        }
    }

    private static void fullscreenWaypointLabelScaleConfig() throws Exception {
        check(OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.getDefault() == 0.75,
                "fullscreen waypoint text defaults to 25 percent smaller");
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        Files.writeString(path, "{\"fullscreenWaypointLabelMinZoom\":0.3}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.get() == 0.75,
                "existing configs without a text size adopt the smaller default");
        for (double scale : new double[]{0.25, 0.65, 0.75, 1.0, 2.0}) {
            OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.set(scale);
            OpenMapDeltaConfig.save();
            OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.set(0.5);
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.get() == scale,
                    "fullscreen text size survives save and reload: " + scale);
            check(OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM.get() == 0.3,
                    "text size is independent of label visibility zoom");
        }
        for (String invalid : new String[]{"0", "0.249", "2.01", "1e309", "null", "[]", "{}", "\"large\""}) {
            Files.writeString(path, "{\"fullscreenWaypointLabelScale\":" + invalid + ",\"showCompass\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.get() == 0.75 && !OpenMapDeltaConfig.SHOW_COMPASS.get(),
                    "invalid text size restores default and preserves other settings: " + invalid);
        }
        for (double invalid : new double[]{0, 2.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            try {
                OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE.set(invalid);
                throw new AssertionError("Invalid fullscreen text scale accepted: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(true, "invalid text scale is rejected at runtime");
            }
        }
    }

    private static void fullscreenAndGenerationConfig() throws Exception {
        check(OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.getDefault() == 16, "player icons default to a sixteen-pixel cap");
        check(OpenMapDeltaConfig.BIOME_GRASS_TINT.getDefault() && OpenMapDeltaConfig.ASYNC_MAP_GENERATION.getDefault(), "new terrain options default to enabled");
        check(OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.getDefault()
                        && OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.getDefault(),
                "MTR and Create fullscreen rail overlays default to enabled");
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        Files.writeString(path, "{\"playerIconMaxSize\":4,\"fullscreenDefaultZoom\":18,\"biomeGrassTint\":false,\"asyncMapGeneration\":false,\"fullscreenShowMtrRails\":false,\"fullscreenShowCreateRails\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.get() == 4 && OpenMapDeltaConfig.FULLSCREEN_DEFAULT_ZOOM.get() == 18, "small icon cap and two additional zoom-out steps load");
        check(!OpenMapDeltaConfig.BIOME_GRASS_TINT.get() && !OpenMapDeltaConfig.ASYNC_MAP_GENERATION.get(), "terrain settings can be disabled");
        OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.set(64);
        OpenMapDeltaConfig.save(); OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.get() == 64 && !OpenMapDeltaConfig.BIOME_GRASS_TINT.get(), "save/reload retains player cap and terrain options");
        check(!OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get()
                        && OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get(),
                "MTR and Create visibility toggles persist independently");
        for (String invalid : new String[]{"3", "65", "null", "[]", "{}"}) {
            Files.writeString(path, "{\"playerIconMaxSize\":" + invalid + ",\"biomeGrassTint\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.get() == 16 && !OpenMapDeltaConfig.BIOME_GRASS_TINT.get(), "invalid icon cap falls back without losing other options: " + invalid);
        }
        Files.writeString(path, "{\"fullscreenDefaultZoom\":19}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.FULLSCREEN_DEFAULT_ZOOM.get() == 1, "zoom beyond eighteen is rejected");
    }

    private static void minimapInformationLayout() {
        for (int screenWidth : new int[]{320, 640, 1920}) {
            for (int screenHeight : new int[]{240, 360, 1080}) {
                for (double scale : new double[]{0.25, 1.0, 2.0}) {
                    int size = MinimapSizing.displaySize(screenWidth, screenHeight, scale);
                    int frameWidth = MinimapInfoLayout.width(screenWidth, size);
                    int cornerWidth = MinimapInfoLayout.cornerTextWidth(frameWidth);
                    int frameHeight = MinimapInfoLayout.height(size);
                    for (int mapX : new int[]{8, screenWidth - size - 8}) {
                        int left = MinimapInfoLayout.left(screenWidth, mapX, size);
                        check(left >= 3 && left + frameWidth <= screenWidth - 3, "information frame stays on screen");
                        check(left + 5 + cornerWidth + 2 < left + frameWidth - cornerWidth - 7,
                                "left/right corner label backgrounds never overlap");
                        check(6 + 9 + 1 < frameHeight - 12 - 5 - 1,
                                "upper/lower corner label backgrounds never overlap");
                    }
                }
            }
        }
        check(MinimapInfoLayout.width(320, 20) == 120 && MinimapInfoLayout.height(20) == 48,
                "minimum information frame does not force larger terrain map");
        for (int screenWidth : new int[]{320, 641, 1920}) {
            for (int size : new int[]{20, 81, 160}) {
                for (int mapX : new int[]{8, (screenWidth - size) / 2, screenWidth - size - 8}) {
                    int available = MinimapInfoLayout.centeredTextWidth(screenWidth, mapX, size);
                    for (float textWidth : new float[]{7.2f, available / 2.0f, available}) {
                        float left = MinimapInfoLayout.centeredTextLeft(mapX, size, textWidth);
                        check(Math.abs(left + textWidth / 2 - (mapX + size / 2.0f)) < 0.0001f,
                                "top/bottom text center matches map center at every screen position");
                        check(left - 2 >= 3 && left + textWidth + 2 <= screenWidth - 3,
                                "centered text and background stay on screen without shifting");
                    }
                }
            }
        }
    }

    private static void minimapInformationConfig() throws Exception {
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE.getDefault() == 0.6, "smaller default player icon");
        check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_SCALE.getDefault() == 0.65, "smaller default waypoint icon and name");
        check(OpenMapDeltaConfig.MINIMAP_LABEL_LIMIT.getDefault() == 5, "five ordinary names by default");
        check(OpenMapDeltaConfig.BORDER_STYLE.getDefault() == OpenMapDeltaConfig.BorderStyle.SOLID, "solid border by default");
        check(OpenMapDeltaConfig.BORDER_COLOR.getDefault() == 0xFF808080, "opaque gray border by default");
        check(OpenMapDeltaConfig.INFO_TOP.getDefault() == MinimapInfoType.BIOME_NAME, "biome above map");
        check(OpenMapDeltaConfig.INFO_BOTTOM.getDefault() == MinimapInfoType.COORDINATES, "full coordinates below map");
        check(OpenMapDeltaConfig.INFO_BOTTOM_RIGHT.getDefault() == MinimapInfoType.DAYTIME_24, "24 hour clock lower right");
        check(OpenMapDeltaConfig.INFO_BOTTOM_LEFT.getDefault() == MinimapInfoType.WORLD_DAYS, "day count lower left");
        check(OpenMapDeltaConfig.INFO_TOP_LEFT.getDefault() == MinimapInfoType.NONE
                && OpenMapDeltaConfig.INFO_TOP_RIGHT.getDefault() == MinimapInfoType.NONE, "upper corners empty");
        for (MinimapInfoSlot slot : MinimapInfoSlot.values()) {
            for (MinimapInfoType type : MinimapInfoType.values()) {
                if (!slot.accepts(type)) {
                    try {
                        OpenMapDeltaConfig.infoSlot(slot).set(type);
                        throw new AssertionError("wide entry accepted in a corner");
                    } catch (IllegalArgumentException expected) { assertions++; }
                } else {
                    OpenMapDeltaConfig.infoSlot(slot).set(type);
                    check(OpenMapDeltaConfig.infoSlot(slot).get() == type, "allowed slot choice persists");
                }
            }
            OpenMapDeltaConfig.infoSlot(slot).set(OpenMapDeltaConfig.infoSlot(slot).getDefault());
        }
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        Files.writeString(path, "{\"minimapLabelLimit\":-1,\"infoTop\":\"DIMENSION_ID\",\"infoTopLeft\":\"COORDINATES\",\"infoTickSeconds\":1,\"infoSpeedSeconds\":10}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_LABEL_LIMIT.get() == -1, "unlimited label setting loads");
        check(OpenMapDeltaConfig.INFO_TOP.get() == MinimapInfoType.DIMENSION_ID, "wide top choice loads");
        check(OpenMapDeltaConfig.INFO_TOP_LEFT.get() == MinimapInfoType.NONE, "invalid wide corner choice falls back");
        check(OpenMapDeltaConfig.INFO_TICK_SECONDS.get() == 1.0 && OpenMapDeltaConfig.INFO_SPEED_SECONDS.get() == 10, "custom averaging/time parameters load");
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.INFO_TOP.set(MinimapInfoType.NONE);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.INFO_TOP.get() == MinimapInfoType.DIMENSION_ID, "save/reload preserves choice");
        Files.writeString(path, "{\"minimapLabelLimit\":3.5,\"infoTickSeconds\":1e309,\"minimapPlayerScale\":0,\"borderStyle\":\"unknown\",\"infoBottom\":\"unknown\",\"showCompass\":false}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_LABEL_LIMIT.get() == 5, "fractional limit rejected");
        check(OpenMapDeltaConfig.INFO_TICK_SECONDS.get() == 0.05, "nonfinite tick conversion rejected");
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE.get() == 0.6, "zero player scale rejected");
        check(OpenMapDeltaConfig.BORDER_STYLE.get() == OpenMapDeltaConfig.BorderStyle.SOLID, "unknown border style falls back");
        check(OpenMapDeltaConfig.INFO_BOTTOM.get() == MinimapInfoType.COORDINATES, "unknown info choice falls back");
        check(!OpenMapDeltaConfig.SHOW_COMPASS.get(), "invalid new settings do not prevent later settings from loading");
    }

    private static void displaySizes() {
        size(1920, 1080, 1.0, 360);
        size(1080, 1920, 1.0, 360);
        size(3440, 1440, 1.0, 480);
        size(640, 343, 1.0, 114);
        size(640, 343, 0.5, 57);
        size(1920, 1080, 0.25, 90);
        size(1920, 1080, 0.75, 270);
        size(1920, 1080, 1.25, 450);
        size(1920, 1080, 1.5, 540);
        size(1920, 1080, 2.0, 720);
        for (int guiScale : new int[]{1, 2, 3, 4, 6}) {
            int physicalSize = MinimapSizing.displaySize(1920 / guiScale, 1080 / guiScale, 1.0) * guiScale;
            check(physicalSize == 360, "same physical size at GUI scale " + guiScale);
        }
        size(0, 1080, 1.0, 0);
        size(1920, 0, 1.0, 0);
        size(-1, 100, 1.0, 0);
        size(100, -1, 1.0, 0);
        size(1, 1, 1.0, 0);
        size(2, 2, 1.0, 0);
        size(3, 3, 1.0, 1);
        size(3, 3, 0.25, 0);
        for (int shorter = 0; shorter <= 24; shorter++) {
            check(MinimapSizing.displaySize(shorter, 100, 1.0) <= shorter / 3.0,
                    "strict one-third bound even on tiny viewport " + shorter);
        }
        for (double invalid : new double[]{0.0, 0.24, 2.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            size(1920, 1080, invalid, 360);
        }
    }

    private static void geometry() {
        check(MinimapSizing.TEXTURE_SIZE == 128, "native texture and sampled world extent preserved");
        check(MinimapSizing.renderExtent(0, true, true) == 128, "borderless circle extent");
        check(MinimapSizing.renderExtent(2, true, false) == 134, "circle includes raster border pixel");
        check(MinimapSizing.renderExtent(8, true, true) == 146, "maximum circle border");
        check(MinimapSizing.renderExtent(2, false, false) == 132, "north-up square border");
        check(MinimapSizing.renderExtent(8, false, false) == 144, "maximum square border");
        check(MinimapSizing.renderExtent(8, false, true) == 182, "heading-up square covers diagonal");
        for (boolean circular : new boolean[]{false, true}) {
            for (boolean headingUp : new boolean[]{false, true}) {
                for (int thickness : new int[]{0, 2, 8}) {
                    int extent = MinimapSizing.renderExtent(thickness, circular, headingUp);
                    int inset = (extent - 128) / 2;
                    check(inset >= thickness, "border remains within layout bounds");
                    if (circular && thickness > 0) {
                        check(inset >= thickness + 1, "circular border extra pixel fits");
                    }
                    if (!circular && headingUp) {
                        double cornerRadius = 64 * Math.sqrt(2);
                        check(inset + 64 - cornerRadius >= 0
                                        && inset + 64 + cornerRadius <= extent,
                                "rotated square corners fit at maximum diagonal");
                    }
                    int target = MinimapSizing.displaySize(640, 343, 1.0);
                    float renderScale = target / (float) extent;
                    check(extent * (double) renderScale <= 343 / 3.0,
                            "scaled outer extent stays below one third");
                }
            }
        }
        check(MinimapSizing.margin(1920, 1080, 360) == 8, "normal corner margin");
        check(MinimapSizing.margin(3, 3, 1) == 2, "tiny corner margin");
        check(MinimapSizing.margin(0, 0, 0) == 0, "empty viewport margin");
        for (int shorter : new int[]{1, 2, 3, 9, 24, 1080}) {
            for (double scale : new double[]{0.25, 1.0, 2.0}) {
                int size = MinimapSizing.displaySize(shorter, shorter, scale);
                int margin = MinimapSizing.margin(shorter, shorter, size);
                check(margin >= 0 && size + margin <= shorter,
                        "map fits every corner without negative origins");
            }
        }
    }

    private static void circleTerrainMeetsBorder() {
        OpenMapDeltaConfig.MinimapShape previous = OpenMapDeltaConfig.SHAPE.get();
        try {
            OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.CIRCLE);
            MinimapShape.ensureMask(128);
            int color = 0x80ABCDEF;
            // These texels sit inside the solid border's inner edge, including diagonal edges.
            for (int[] point : new int[][]{{63, 0}, {64, 0}, {0, 63}, {0, 64},
                    {63, 127}, {64, 127}, {127, 63}, {127, 64},
                    {16, 21}, {111, 21}, {16, 106}, {111, 106}}) {
                check(MinimapShape.applyMask(color, point[0], point[1]) == color,
                        "terrain reaches the border without a transparent edge texel");
            }
            for (int[] point : new int[][]{{0, 0}, {127, 0}, {0, 127}, {127, 127}}) {
                check(MinimapShape.applyMask(color, point[0], point[1]) == 0,
                        "circle still clips texture corners outside the border");
            }
            MinimapShape.ensureMask(81);
            check(MinimapShape.applyMask(color, 34, 0) == color, "odd-size circle reaches its border");
            MinimapShape.ensureMask(20);
            check(MinimapShape.applyMask(color, 9, 0) == color, "small circle reaches its border");
            OpenMapDeltaConfig.SHAPE.set(OpenMapDeltaConfig.MinimapShape.SQUARE);
            check(MinimapShape.applyMask(color, 0, 0) == color, "square terrain remains unmasked");
        } finally {
            OpenMapDeltaConfig.SHAPE.set(previous);
        }
    }

    private static void compassMargins() {
        for (int[] viewport : new int[][]{{1920, 1080}, {640, 343}, {1080, 1920}, {3, 3}, {9, 6}}) {
            for (int thickness : new int[]{0, 2, 8}) {
                for (double scale : new double[]{0.25, 1.0, 2.0}) {
                    int size = MinimapSizing.displaySize(viewport[0], viewport[1], scale);
                    if (size == 0) continue;
                    int extent = MinimapSizing.renderExtent(thickness, true, false);
                    int inset = (extent - 128) / 2;
                    float renderScale = size / (float) extent;
                    double overhang = Math.max(0, 10 - inset) * renderScale;
                    int margin = MinimapSizing.margin(viewport[0], viewport[1], size, overhang);
                    check(margin + (inset - 10) * (double) renderScale >= 0,
                            "north compass glyph remains on screen at top-right");
                    check(viewport[0] - size - margin >= 0, "right corner map remains on screen");
                    check(margin + size <= viewport[1], "top corner map remains on screen");
                }
            }
        }
    }

    private static void minimapTextMinimum() {
        check(MinimapSizing.MIN_TEXT_PIXEL_HEIGHT == 16, "minimap text has a sixteen-pixel physical minimum");
        int[][] physicalWindows = {{854, 480}, {640, 360}, {320, 240}};
        int[] fontLineHeights = {8, 9, 12, 16};
        int sampledWorldSpan = MinimapSizing.TEXTURE_SIZE * RuntimeClientState.getZoomBlocksPerPixel();
        for (int[] window : physicalWindows) {
            for (int guiScale : new int[]{1, 2, 3, 4}) {
                int screenWidth = window[0] / guiScale;
                int screenHeight = window[1] / guiScale;
                for (double mapScale : new double[]{0.25, 0.5, 1.0, 2.0}) {
                    int mapSize = MinimapSizing.displaySize(screenWidth, screenHeight, mapScale);
                    for (boolean circular : new boolean[]{false, true}) {
                        for (boolean headingUp : new boolean[]{false, true}) {
                            for (int border : new int[]{0, 2, 8}) {
                                int extent = MinimapSizing.renderExtent(border, circular, headingUp);
                                int inset = (extent - MinimapSizing.TEXTURE_SIZE) / 2;
                                double renderScale = mapSize / (double) extent;
                                for (int fontHeight : fontLineHeights) {
                                    double infoScale = MinimapSizing.textScale(0.6, 1.0, guiScale, fontHeight);
                                    double waypointScale = MinimapSizing.textScale(0.65, renderScale, guiScale, fontHeight);
                                    double compassScale = MinimapSizing.textScale(1.0, renderScale, guiScale, fontHeight);
                                    assertPhysicalTextHeight(infoScale, 1.0, guiScale, fontHeight, "information");
                                    assertPhysicalTextHeight(waypointScale, renderScale, guiScale, fontHeight, "waypoint");
                                    assertPhysicalTextHeight(compassScale, renderScale, guiScale, fontHeight, "compass");

                                    double requestedLarge = MinimapSizing.textScale(2.0, renderScale, guiScale, fontHeight);
                                    assertPhysicalTextHeight(requestedLarge, renderScale, guiScale, fontHeight,
                                            "larger requested waypoint");
                                    if (2.0 * renderScale * guiScale * fontHeight
                                            >= MinimapSizing.MIN_TEXT_PIXEL_HEIGHT) {
                                        check(requestedLarge == 2.0, "larger requested waypoint scale is preserved");
                                    }
                                    check(MinimapSizing.displaySize(screenWidth, screenHeight, mapScale) == mapSize
                                                    && MinimapSizing.TEXTURE_SIZE == 128
                                                    && MinimapSizing.renderExtent(border, circular, headingUp) == extent
                                                    && MinimapSizing.TEXTURE_SIZE * RuntimeClientState.getZoomBlocksPerPixel()
                                                            == sampledWorldSpan,
                                            "text floor leaves minimap dimensions and sampled texture span unchanged");

                                    int infoLineHeight = MinimapInfoLayout.lineHeight(fontHeight, guiScale);
                                    check(infoLineHeight >= 12
                                                    && infoLineHeight >= Math.ceil(fontHeight * infoScale),
                                            "dynamic information line height reserves the drawn text");
                                    int frameHeight = MinimapInfoLayout.height(mapSize, infoLineHeight);
                                    check(frameHeight >= Math.max(48, mapSize)
                                                    && frameHeight >= 2 * infoLineHeight + 18,
                                            "dynamic information frame provides two rows of spacing");
                                    int upperBottom = 6 + infoLineHeight;
                                    int lowerTop = frameHeight - infoLineHeight - 5;
                                    check(lowerTop > upperBottom,
                                            "corner information rows stay separated at dynamic text height");

                                    double topOverhang = Math.max(0,
                                            (6 - inset) * renderScale + 4 * compassScale * renderScale);
                                    double bottomOverhang = Math.max(0,
                                            (6 - inset) * renderScale + (fontHeight - 4) * compassScale * renderScale);
                                    int topReserve = MinimapInfoLayout.topReserve(infoLineHeight,
                                            (int) Math.ceil(topOverhang));
                                    int bottomReserve = MinimapInfoLayout.bottomReserve(infoLineHeight,
                                            (int) Math.ceil(bottomOverhang));
                                    check(topReserve >= infoLineHeight + 3 + Math.ceil(topOverhang)
                                                    && bottomReserve >= infoLineHeight + 4 + Math.ceil(bottomOverhang),
                                            "dynamic HUD reserves account for information and compass overhang");
                                    int infoTop = topReserve - (int) Math.ceil(topOverhang) - infoLineHeight - 3;
                                    int infoBottom = topReserve + frameHeight + (int) Math.ceil(bottomOverhang) + 4 + infoLineHeight;
                                    int reservedScreenHeight = topReserve + frameHeight + bottomReserve;
                                    check(infoTop >= 0 && infoBottom <= reservedScreenHeight + 0.000001,
                                            "reserved HUD frame keeps centered information within screen bounds");

                                    double compassMargin = Math.max(topOverhang, bottomOverhang);
                                    int margin = MinimapSizing.margin(screenWidth, screenHeight, mapSize, compassMargin);
                                    check(margin + 0.000001 >= compassMargin
                                                    || Math.min(screenWidth, screenHeight) - mapSize < Math.ceil(compassMargin),
                                            "largest compass overhang is included in minimap edge margin when space allows");
                                    int bottomAnchorY = screenHeight - mapSize - margin;
                                    check(bottomAnchorY + mapSize + bottomOverhang <= screenHeight + 0.000001
                                                    || Math.min(screenWidth, screenHeight) - mapSize < Math.ceil(bottomOverhang),
                                            "south compass label stays onscreen at a bottom anchor without bottom info");
                                }
                            }
                        }
                    }
                }
            }
        }
        clusterBadgeMarginsAtSmallestMap();
    }

    private static void assertPhysicalTextHeight(double localScale, double parentScale,
                                                 double guiScale, int fontHeight, String kind) {
        double physicalHeight = fontHeight * localScale * parentScale * guiScale;
        check(physicalHeight + 0.000001 >= MinimapSizing.MIN_TEXT_PIXEL_HEIGHT,
                kind + " text meets the physical minimum");
    }

    private static void clusterBadgeMarginsAtSmallestMap() {
        int screenWidth = 854;
        int screenHeight = 480;
        int mapSize = MinimapSizing.displaySize(screenWidth, screenHeight, 0.25);
        int extent = MinimapSizing.renderExtent(2, true, false);
        int inset = (extent - MinimapSizing.TEXTURE_SIZE) / 2;
        double renderScale = mapSize / (double) extent;
        double labelScale = MinimapSizing.textScale(0.65, renderScale, 1, 9);
        double edgeOffset = 2;

        for (int count : new int[]{10, 100, 1000}) {
            int digits = Integer.toString(count).length();
            int conservativeTextWidth = 6 * digits;
            int badgeRadius = (int) Math.ceil(Math.max(10,
                    Math.hypot(conservativeTextWidth / 2.0, 9 / 2.0) + 3));
            double badgeOverhang = Math.max(0,
                    edgeOffset + (badgeRadius + 1) * labelScale - inset) * renderScale;
            // Compass is disabled in this case; cluster geometry alone must keep the badge onscreen.
            int margin = MinimapSizing.margin(screenWidth, screenHeight, mapSize, badgeOverhang);
            int bottomAnchorY = screenHeight - mapSize - margin;
            check(margin + 0.000001 >= badgeOverhang,
                    "large cluster badge reserve covers count " + count + " with compass disabled");
            check(bottomAnchorY + mapSize + badgeOverhang <= screenHeight + 0.000001,
                    "large cluster badge fits a bottom map anchor: " + count);
        }
    }

    private static void config() throws Exception {
        check(OpenMapDeltaConfig.MINIMAP_SCALE.getDefault() == 0.9, "default minimap is ten percent smaller");
        for (double invalid : new double[]{0.0, 0.24, 2.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double previous = OpenMapDeltaConfig.MINIMAP_SCALE.get();
            try {
                OpenMapDeltaConfig.MINIMAP_SCALE.set(invalid);
                throw new AssertionError("Accepted invalid scale: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(OpenMapDeltaConfig.MINIMAP_SCALE.get() == previous, "invalid setter preserves current scale");
            }
        }
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        Files.createDirectories(path.getParent());
        OpenMapDeltaConfig.MINIMAP_SCALE.set(0.9);
        Files.deleteIfExists(path);
        OpenMapDeltaConfig.load();
        check(Files.readString(path).contains("\"minimapScale\": 0.9"), "first run writes the smaller relative scale");
        Files.writeString(path, "{\"minimapSize\":512}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_SCALE.get() == 0.9, "legacy pixel setting uses the smaller baseline");
        Files.writeString(path, "{\"minimapScale\":0.5}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_SCALE.get() == 0.5, "fractional config loads");
        OpenMapDeltaConfig.MINIMAP_SCALE.set(1.25);
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.MINIMAP_SCALE.set(1.0);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_SCALE.get() == 1.25, "save/reload preserves scale");
        for (String invalid : new String[]{"0.1", "0", "-1", "2.01", "1e309", "-1e309",
                "NaN", "Infinity", "\"NaN\"", "null", "[]", "{}"}) {
            OpenMapDeltaConfig.MINIMAP_SCALE.set(1.25);
            OpenMapDeltaConfig.ENABLED.set(true);
            Files.writeString(path, "{\"minimapScale\":" + invalid + ",\"enabled\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_SCALE.get() == 0.9, "invalid JSON scale falls back: " + invalid);
            check(!OpenMapDeltaConfig.ENABLED.get(), "other settings still load: " + invalid);
            OpenMapDeltaConfig.save();
        }
        for (double valid : new double[]{0.25, 0.5, 0.9, 1.0, 1.25, 2.0}) {
            Files.writeString(path, "{\"minimapScale\":" + valid + "}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_SCALE.get() == valid, "valid configured scale " + valid);
        }
    }

    private static void minimapPlayerIconConfig() throws Exception {
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.getDefault() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD,
                "minimap defaults to upright player faces");
        check(!OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.getDefault(),
                "minimap player head defaults to fixed upright orientation");

        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.getDefault());
        OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(false);
        Files.writeString(path, "{\"enabled\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD
                        && !OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get(),
                "older configs without player icon keys retain both defaults");

        Files.writeString(path, "{\"minimapPlayerIcon\":\"TRIANGLE\",\"minimapPlayerScale\":1.5,\"minimapRotatePlayerHead\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE
                        && OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE.get() == 1.5
                        && OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get(),
                "existing triangle preference, shared size and head rotation are preserved");

        for (OpenMapDeltaConfig.MinimapPlayerIcon icon : OpenMapDeltaConfig.MinimapPlayerIcon.values()) {
            for (boolean rotate : new boolean[]{false, true}) {
                OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(icon);
                OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(rotate);
                OpenMapDeltaConfig.save();
                OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(icon == OpenMapDeltaConfig.MinimapPlayerIcon.VANILLA_POINTER
                        ? OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE : OpenMapDeltaConfig.MinimapPlayerIcon.VANILLA_POINTER);
                OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(!rotate);
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == icon
                                && OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get() == rotate
                                && OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE.get() == 1.5,
                        "icon choice, shared size and head rotation survive save/reload: " + icon + ", " + rotate);
            }
        }

        Files.writeString(path, "{\"minimapPlayerIcon\":\"vanilla_pointer\"}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.VANILLA_POINTER,
                "lowercase vanilla pointer enum values load");

        Files.writeString(path, "{\"minimapPlayerIcon\":\"player_head\",\"minimapRotatePlayerHead\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD
                        && OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get(),
                "lowercase enum values load with the strict boolean setting");

        for (String invalid : new String[]{"null", "{}", "[]", "1", "true", "\"unknown\""}) {
            OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD);
            OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(false);
            OpenMapDeltaConfig.SHOW_DEATH.set(true);
            Files.writeString(path, "{\"minimapPlayerIcon\":" + invalid
                    + ",\"minimapRotatePlayerHead\":true,\"showDeath\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD,
                    "malformed player icon restores its default independently: " + invalid);
            check(OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get() && !OpenMapDeltaConfig.SHOW_DEATH.get(),
                    "valid rotation and unrelated settings load after malformed icon: " + invalid);
        }

        for (String invalid : new String[]{"null", "{}", "[]", "1", "0", "\"true\"", "\"invalid\""}) {
            OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE);
            OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(true);
            OpenMapDeltaConfig.SHOW_DEATH.set(true);
            Files.writeString(path, "{\"minimapPlayerIcon\":\"player_head\",\"minimapRotatePlayerHead\":"
                    + invalid + ",\"showDeath\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.getDefault()
                            == OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get(),
                    "malformed head rotation restores its default independently: " + invalid);
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD
                            && !OpenMapDeltaConfig.SHOW_DEATH.get(),
                    "valid icon and unrelated settings load after malformed rotation: " + invalid);
        }

        float[] yaws = {0.0f, 90.0f, -90.0f, 180.0f, 45.0f};
        float[] northUpRotations = {180.0f, 270.0f, 90.0f, 360.0f, 225.0f};
        for (int i = 0; i < yaws.length; i++) {
            float yaw = yaws[i];
            float expected = northUpRotations[i];
            check(MinimapPlayerIconMath.rotationDegrees(false, false, false, yaw) == expected,
                    "directional pointer and triangle north-up rotation at yaw " + yaw);
            check(MinimapPlayerIconMath.rotationDegrees(true, true, false, yaw) == expected,
                    "rotating head follows triangle north-up rotation at yaw " + yaw);
            check(MinimapPlayerIconMath.rotationDegrees(false, false, true, yaw) == 0.0f
                            && MinimapPlayerIconMath.rotationDegrees(true, true, true, yaw) == 0.0f,
                    "directional icons point up in heading-up mode at yaw " + yaw);
            check(MinimapPlayerIconMath.rotationDegrees(true, false, false, yaw) == 0.0f
                            && MinimapPlayerIconMath.rotationDegrees(true, false, true, yaw) == 0.0f,
                    "fixed head stays upright in every map mode at yaw " + yaw);
        }
        check(MinimapPlayerIconMath.rotationDegrees(false, false, true, 90, -90) == 180,
                "remote directional icon is relative to the local view in heading-up mode");
        check(MinimapPlayerIconMath.rotationDegrees(true, true, true, 45, 90) == -45,
                "remote rotating face follows its own heading relative to the map");
        check(MinimapPlayerIconMath.rotationDegrees(true, false, true, 45, 90) == 0
                        && MinimapPlayerIconMath.rotationDegrees(true, false, false, 45, 90) == 0,
                "remote fixed face stays upright regardless of local view or own heading");
        check(MinimapPlayerIconMath.rotationDegrees(true, true, false, -90, 90) == 90,
                "fullscreen/north-up face rotation is independent of local view");
    }

    private static void fullscreenPlayerIconConfig() throws Exception {
        check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.getDefault() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD,
                "fullscreen defaults to player faces");
        check(!OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.getDefault(),
                "fullscreen faces default to upright orientation");
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        for (String legacy : new String[]{"true", "false", "null", "\"false\""}) {
            Files.writeString(path, "{\"showPlayerHead\":" + legacy + "}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == (legacy.equals("false")
                            ? OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE : OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD),
                    "legacy fullscreen head preference imports only a real boolean: " + legacy);
        }
        Files.writeString(path, "{\"map\":{\"showPlayerHead\":false}}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE,
                "legacy nested head toggle is imported");
        Files.writeString(path, "{}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD,
                "old config without head or icon preference uses faces");

        for (OpenMapDeltaConfig.MinimapPlayerIcon icon : OpenMapDeltaConfig.MinimapPlayerIcon.values()) {
            for (boolean miniRotate : new boolean[]{false, true}) {
                for (boolean fullRotate : new boolean[]{false, true}) {
                    OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.set(OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD);
                    OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.set(icon);
                    OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(miniRotate);
                    OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.set(fullRotate);
                    OpenMapDeltaConfig.save();
                    OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.set(!miniRotate);
                    OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.set(!fullRotate);
                    OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.set(OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE);
                    OpenMapDeltaConfig.load();
                    check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == icon
                                    && OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD
                                    && OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get() == miniRotate
                                    && OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get() == fullRotate,
                            "map icon/rotation choices persist independently: " + icon + ", " + miniRotate + ", " + fullRotate);
                }
            }
        }
        Files.writeString(path, "{\"showPlayerHead\":false,\"fullscreenPlayerIcon\":\"player_head\",\"fullscreenRotatePlayerHead\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD
                        && OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get(),
                "explicit fullscreen selector takes precedence over legacy toggle");

        for (String invalid : new String[]{"null", "{}", "[]", "1", "true", "\"unknown\""}) {
            OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.set(OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE);
            Files.writeString(path, "{\"fullscreenPlayerIcon\":" + invalid
                    + ",\"minimapPlayerIcon\":\"vanilla_pointer\",\"fullscreenRotatePlayerHead\":true}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.PLAYER_HEAD
                            && OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.VANILLA_POINTER
                            && OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get(),
                    "invalid fullscreen selector restores only its own default: " + invalid);
        }
        for (String invalid : new String[]{"null", "{}", "[]", "1", "0", "\"true\""}) {
            OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.set(true);
            Files.writeString(path, "{\"fullscreenPlayerIcon\":\"vanilla_pointer\",\"fullscreenRotatePlayerHead\":"
                    + invalid + ",\"minimapRotatePlayerHead\":true}");
            OpenMapDeltaConfig.load();
            check(!OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD.get()
                            && OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD.get()
                            && OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.VANILLA_POINTER,
                    "invalid fullscreen rotation does not change minimap rotation: " + invalid);
        }
    }

    private static void waypointConfig() throws Exception {
        check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.getDefault() == 5.0, "default waypoint hide radius is five blocks");
        for (double invalid : new double[]{-0.01, 128.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double previous = OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get();
            try {
                OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.set(invalid);
                throw new AssertionError("Accepted invalid waypoint hide distance: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == previous,
                        "invalid setter preserves current waypoint hide distance");
            }
        }
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.set(5.0);
        Files.writeString(path, "{\"enabled\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == 5.0, "older config retains five-block default");
        OpenMapDeltaConfig.save();
        check(Files.readString(path).contains("\"waypointHideDistance\": 5.0"), "save writes waypoint hide distance");
        OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.set(8.0);
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.set(5.0);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == 8.0, "save/reload preserves selected hide distance");
        for (double valid : new double[]{0.0, 0.5, 5.0, 7.5, 128.0}) {
            Files.writeString(path, "{\"waypointHideDistance\":" + valid + "}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == valid, "valid waypoint hide distance: " + valid);
        }
        String[] invalidValues = {"-1", "128.01", "1e309", "-1e309", "NaN", "Infinity",
                "\"NaN\"", "\"5\"", "null", "[]", "{}", "true"};
        for (String invalid : invalidValues) {
            OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.set(8.0);
            OpenMapDeltaConfig.SHOW_DEATH.set(true);
            Files.writeString(path, "{\"waypointHideDistance\":" + invalid + ",\"showDeath\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == 5.0, "invalid distance falls back: " + invalid);
            check(!OpenMapDeltaConfig.SHOW_DEATH.get(), "later settings still load after invalid distance: " + invalid);
        }
        if (!OpenMapDeltaConfig.MAP_FULLSCREEN_ENABLED.getKey().startsWith("map.")) {
            Files.writeString(path, "{\"map\":{\"waypointHideDistance\":3.0}}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == 3.0, "legacy nested waypoint hide distance loads");
            for (String invalid : invalidValues) {
                OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.set(8.0);
                OpenMapDeltaConfig.SHOW_DEATH.set(true);
                Files.writeString(path, "{\"map\":{\"waypointHideDistance\":" + invalid + ",\"showDeath\":false}}");
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE.get() == 5.0, "invalid nested distance falls back: " + invalid);
                check(!OpenMapDeltaConfig.SHOW_DEATH.get(), "nested settings still load after invalid distance: " + invalid);
            }
        }
    }

    private static void createStationGroupingRadiusConfig() throws Exception {
        var radius = OpenMapDeltaConfig.CREATE_STATION_GROUPING_RADIUS;
        check(radius.getDefault() == 50, "Create station grouping defaults to 50 blocks");
        check(radius.getKey().equals("createStationGroupingRadius"), "Create grouping radius uses its documented JSON key");
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        boolean previousShowDeath = OpenMapDeltaConfig.SHOW_DEATH.get();

        Files.writeString(path, "{\"enabled\":true}");
        // Simulate startup: missing keys retain the value initialized from the default.
        radius.set(radius.getDefault());
        OpenMapDeltaConfig.load();
        check(radius.get() == 50, "older configs without a Create grouping radius use the default");

        radius.set(75);
        OpenMapDeltaConfig.save();
        check(Files.readString(path).contains("\"createStationGroupingRadius\": 75"),
                "save writes the custom Create grouping radius");
        radius.set(50);
        OpenMapDeltaConfig.load();
        check(radius.get() == 75, "save/reload preserves a custom Create grouping radius");

        for (int valid : new int[]{0, 1_000_000}) {
            radius.set(valid);
            OpenMapDeltaConfig.save();
            radius.set(50);
            OpenMapDeltaConfig.load();
            check(radius.get() == valid, "valid Create grouping radius survives save/reload: " + valid);
        }

        String[] invalidValues = {"-1", "1000001", "1.5", "1e309", "-1e309", "NaN", "Infinity",
                "\"NaN\"", "\"25\"", "null", "[]", "{}", "true"};
        for (String invalid : invalidValues) {
            radius.set(75);
            OpenMapDeltaConfig.SHOW_DEATH.set(true);
            Files.writeString(path, "{\"createStationGroupingRadius\":" + invalid + ",\"showDeath\":false}");
            OpenMapDeltaConfig.load();
            check(radius.get() == 50, "invalid Create grouping radius falls back to default: " + invalid);
            check(!OpenMapDeltaConfig.SHOW_DEATH.get(), "later config settings still load after invalid radius: " + invalid);
        }

        radius.set(50);
        OpenMapDeltaConfig.SHOW_DEATH.set(previousShowDeath);
    }

    private static void minimapWaypointPresentationConfig() throws Exception {
        check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.getDefault() == 150.0,
                "default minimap name cutoff is 150 blocks");
        check(OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.getDefault() == 2.0,
                "default minimap edge icon offset is outward");
        for (double invalid : new double[]{-0.01, 1000000.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double previous = OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get();
            try {
                OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.set(invalid);
                throw new AssertionError("Invalid minimap name distance accepted");
            } catch (IllegalArgumentException expected) {
                check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get() == previous,
                        "invalid name distance preserves current setting");
            }
        }
        for (double invalid : new double[]{-16.01, 8.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double previous = OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get();
            try {
                OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.set(invalid);
                throw new AssertionError("Invalid minimap edge offset accepted");
            } catch (IllegalArgumentException expected) {
                check(OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get() == previous,
                        "invalid edge offset preserves current setting");
            }
        }
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.set(150.0);
        OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.set(2.0);
        Files.writeString(path, "{\"enabled\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get() == 150.0
                        && OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get() == 2.0,
                "older configs acquire the new minimap defaults without a reset");
        OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.set(0.0);
        OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.set(-4.0);
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.set(150.0);
        OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.set(2.0);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get() == 0.0
                        && OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get() == -4.0,
                "unlimited names and the previous edge position survive saving and loading");
        Files.writeString(path, "{\"minimapWaypointNameDistance\":-1,\"minimapMarkerEdgeOffset\":9}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get() == 150.0
                        && OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get() == 2.0,
                "invalid stored values fall back to defaults independently");
        Files.writeString(path, "{\"minimapWaypointNameDistance\":{},\"minimapMarkerEdgeOffset\":\"outside\"}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE.get() == 150.0
                        && OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get() == 2.0,
                "malformed types do not prevent loading the config");
    }

    private static void waypointLabelConfig() throws Exception {
        check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.getDefault() == 2.5, "default waypoint label fade start is 2.5 blocks");
        for (double invalid : new double[]{-0.01, 128.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double previous = OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get();
            try {
                OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.set(invalid);
                throw new AssertionError("Accepted invalid waypoint label fade distance: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == previous,
                        "invalid setter preserves current waypoint label fade distance");
            }
        }
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.set(2.5);
        Files.writeString(path, "{\"enabled\":true}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == 2.5, "older config retains 2.5-block default");
        OpenMapDeltaConfig.save();
        check(Files.readString(path).contains("\"waypointLabelFadeDistance\": 2.5"), "save writes waypoint label fade distance");
        OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.set(8.0);
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.set(2.5);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == 8.0, "save/reload preserves selected label fade distance");
        for (double valid : new double[]{0.0, 0.5, 2.5, 7.5, 128.0}) {
            Files.writeString(path, "{\"waypointLabelFadeDistance\":" + valid + "}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == valid, "valid waypoint label fade distance: " + valid);
        }
        String[] invalidValues = {"-1", "128.01", "1e309", "-1e309", "NaN", "Infinity",
                "\"NaN\"", "\"5\"", "null", "[]", "{}", "true"};
        for (String invalid : invalidValues) {
            OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.set(8.0);
            OpenMapDeltaConfig.SHOW_DEATH.set(true);
            Files.writeString(path, "{\"waypointLabelFadeDistance\":" + invalid + ",\"showDeath\":false}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == 2.5, "invalid distance falls back: " + invalid);
            check(!OpenMapDeltaConfig.SHOW_DEATH.get(), "later settings still load after invalid distance: " + invalid);
        }
        if (!OpenMapDeltaConfig.MAP_FULLSCREEN_ENABLED.getKey().startsWith("map.")) {
            Files.writeString(path, "{\"map\":{\"waypointLabelFadeDistance\":3.0}}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == 3.0, "legacy nested waypoint label fade distance loads");
            for (String invalid : invalidValues) {
                OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.set(8.0);
                OpenMapDeltaConfig.SHOW_DEATH.set(true);
                Files.writeString(path, "{\"map\":{\"waypointLabelFadeDistance\":" + invalid + ",\"showDeath\":false}}");
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE.get() == 2.5, "invalid nested distance falls back: " + invalid);
                check(!OpenMapDeltaConfig.SHOW_DEATH.get(), "nested settings still load after invalid distance: " + invalid);
            }
        }
    }

    private static void layerAndNavigationConfig() throws Exception {
        Path path = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        check(OpenMapDeltaConfig.CAVE_LAYER_STEP.getDefault() == 8, "eight-block cave bands by default");
        check(OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.getDefault() == 3, "three simultaneous destinations by default");
        check(OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.getDefault() == 5.0, "temporary-waypoint arrival radius by default");
        Files.writeString(path, "{\"caveLayerStep\":16,\"navigationMaxTargets\":6,\"navigationArrivalDistance\":7.5}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.CAVE_LAYER_STEP.get() == 16, "configurable cave band width");
        check(OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.get() == 6, "configurable navigation limit");
        check(OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.get() == 7.5, "configurable arrival radius");
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.CAVE_LAYER_STEP.set(8);
        OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.set(3);
        OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.set(5.0);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.CAVE_LAYER_STEP.get() == 16 && OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.get() == 6
                && OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.get() == 7.5, "new settings survive save/reload");
        for (String invalid : new String[]{"-1", "129", "1e309", "-1e309", "NaN", "Infinity",
                "\"8\"", "null", "[]", "{}", "true"}) {
            for (boolean nested : new boolean[]{false, true}) {
                if (nested && OpenMapDeltaConfig.MAP_FULLSCREEN_ENABLED.getKey().startsWith("map.")) continue;
                OpenMapDeltaConfig.CAVE_LAYER_STEP.set(16);
                OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.set(6);
                OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.set(7.5);
                OpenMapDeltaConfig.SHOW_DEATH.set(true);
                String settings = "\"caveLayerStep\":" + invalid + ",\"navigationMaxTargets\":" + invalid
                        + ",\"navigationArrivalDistance\":" + invalid + ",\"showDeath\":false";
                Files.writeString(path, nested ? "{\"map\":{" + settings + "}}" : "{" + settings + "}");
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.CAVE_LAYER_STEP.get() == 8, "invalid cave width falls back: " + invalid);
                check(OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.get() == 3, "invalid limit falls back: " + invalid);
                check(OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.get() == 5.0, "invalid arrival falls back: " + invalid);
                check(!OpenMapDeltaConfig.SHOW_DEATH.get(), "other settings load after invalid navigation settings");
            }
        }
        for (String invalidInteger : new String[]{"0", "1.5", "2147483648"}) {
            Files.writeString(path, "{\"caveLayerStep\":" + invalidInteger + ",\"navigationMaxTargets\":" + invalidInteger + "}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.CAVE_LAYER_STEP.get() == 8 && OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.get() == 3,
                    "integer settings reject zero, fractions and overflow");
        }
        Files.writeString(path, "{\"caveLayerStep\":1,\"navigationMaxTargets\":16,\"navigationArrivalDistance\":0}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.CAVE_LAYER_STEP.get() == 1 && OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.get() == 16
                && OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.get() == 0.0, "valid limits and disabled arrival");
    }

    private static void zoom() {
        OpenMapDeltaConfig.ZOOM_BLOCKS_PER_PIXEL.set(2);
        RuntimeClientState.resetZoom();
        for (double scale : new double[]{0.25, 0.5, 1.0, 2.0}) {
            OpenMapDeltaConfig.MINIMAP_SCALE.set(scale);
            MinimapSizing.displaySize(1920, 1080, scale);
            check(RuntimeClientState.getZoomBlocksPerPixel() == 2, "HUD size preserves runtime zoom");
            check(MinimapSizing.TEXTURE_SIZE * RuntimeClientState.getZoomBlocksPerPixel() == 256,
                    "HUD size preserves sampled world span");
        }
        RuntimeClientState.zoomIn();
        check(RuntimeClientState.getZoomBlocksPerPixel() == 1, "zoom-in halves blocks per texture pixel");
        RuntimeClientState.zoomIn();
        check(RuntimeClientState.getZoomBlocksPerPixel() == 1, "zoom-in minimum unchanged");
        RuntimeClientState.zoomOut();
        check(RuntimeClientState.getZoomBlocksPerPixel() == 2, "zoom-out increment unchanged");
        OpenMapDeltaConfig.ZOOM_BLOCKS_PER_PIXEL.set(16);
        RuntimeClientState.resetZoom();
        RuntimeClientState.zoomOut();
        check(RuntimeClientState.getZoomBlocksPerPixel() == 16, "zoom-out maximum unchanged");
    }

    private static void size(int width, int height, double scale, int expected) {
        check(MinimapSizing.displaySize(width, height, scale) == expected,
                width + "x" + height + " at size " + scale);
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        assertions++;
    }

    public static class TestPlatform implements IPlatformHelper {
        public String getPlatformName() { return "MinimapSizingTest"; }
        public boolean isModLoaded(String modId) { return false; }
        public boolean isDevelopmentEnvironment() { return true; }
        public Path getConfigDir() { return Path.of(System.getProperty("minimap.test.configDir")); }
    }
}
