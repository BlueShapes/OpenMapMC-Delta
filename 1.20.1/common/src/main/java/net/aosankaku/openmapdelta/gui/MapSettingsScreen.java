// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.config.ConfigValue;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.render.FullscreenMapZoom;
import net.aosankaku.openmapdelta.render.WaypointClusterOrder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapSettingsScreen extends Screen {
    private final Screen parent;
    private SettingsList settingsList;
    private double savedScrollAmount;
    private final List<SettingWidget> settingWidgets = new ArrayList<>();
    private EditBox borderHexBox;
    private Button doneButton;

    public MapSettingsScreen(Screen parent) {
        super(Component.translatable("screen.odysseymap.settings"));
        this.parent = parent;
    }

    private interface SettingOption {
        String label();
        String getValueText();
        default String messageText() {
            String value = getValueText();
            return value == null || value.isEmpty() ? label() : label() + ": " + value;
        }
        default Component message() { return Component.literal(messageText()); }
        default Component tooltip() { return message(); }
        default void onClick() {}
        default boolean active() { return true; }

        default AbstractWidget createWidget(int x, int y, int width, int height) {
            Button button = Button.builder(message(), b -> {
                onClick();
                b.setMessage(message());
                b.setTooltip(Tooltip.create(tooltip()));
                b.active = active();
            }).bounds(x, y, width, height).build();
            button.setTooltip(Tooltip.create(tooltip()));
            return button;
        }
    }

    private static class SettingWidget {
        final AbstractWidget widget;
        final SettingOption option;
        String lastMessageText;

        SettingWidget(AbstractWidget widget, SettingOption option) {
            this.widget = widget;
            this.option = option;
            this.lastMessageText = option.messageText();
        }
    }

    @Override
    protected void init() {
        if (settingsList != null) savedScrollAmount = settingsList.savedScrollAmount();
        settingWidgets.clear();

        int centerX = width / 2;
        int listTop = 32;
        int listHeight = Math.max(24, height - 70);

        if (borderHexBox == null) {
            borderHexBox = new EditBox(font, 0, 0, 220, 18,
                    Component.translatable("gui.odysseymap.setting.border_color"));
            borderHexBox.setValue(String.format("#%08X", OpenMapDeltaConfig.BORDER_COLOR.get()));
            borderHexBox.setMaxLength(9);
            borderHexBox.setResponder(text -> {
                String hex = text.trim();
                if (hex.startsWith("#")) hex = hex.substring(1);
                try {
                    long value = Long.parseLong(hex, 16);
                    if (hex.length() != 6 && hex.length() != 8) return;
                    OpenMapDeltaConfig.BORDER_COLOR.set((int) value | (hex.length() == 6 ? 0xFF000000 : 0));
                    OpenMapDeltaConfig.save();
                } catch (NumberFormatException ignored) {}
            });
        }

        settingsList = new SettingsList(minecraft, font, width, height, listTop, height - 38);

        // Define the existing options for each setting category
        int[] minimapCaveYBounds = currentMinimapCaveYBounds();
        List<SettingOption> tab0Options = List.of(
            new BooleanOption(Component.translatable("gui.odysseymap.setting.enabled").getString(), OpenMapDeltaConfig.ENABLED),
            doubleSlider(Component.translatable("gui.odysseymap.setting.size").getString(), OpenMapDeltaConfig.MINIMAP_SCALE, 0.25, 2.0, 0.01, false, MapSettingsScreen::formatDouble),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.position").getString(), OpenMapDeltaConfig.POSITION, OpenMapDeltaConfig.ScreenPosition.values()),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.shape").getString(), OpenMapDeltaConfig.SHAPE, OpenMapDeltaConfig.MinimapShape.values()),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.minimap_layer_mode").getString(), OpenMapDeltaConfig.MINIMAP_LAYER_MODE, net.aosankaku.openmapdelta.config.MinimapLayerMode.values()),
            new MinimapCaveYSliderOption(minimapCaveYBounds[0], minimapCaveYBounds[1]),
            integerSlider(Component.translatable("gui.odysseymap.setting.zoom").getString(), OpenMapDeltaConfig.ZOOM_BLOCKS_PER_PIXEL, 1, 16, 1, false, Integer::toString, () -> {
                RuntimeClientState.resetZoom();
                if (OpenMapDeltaClient.getTileCache() != null) {
                    OpenMapDeltaClient.getTileCache().markAllDirty();
                }
            }),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.rotation").getString(), OpenMapDeltaConfig.ROTATION_MODE, OpenMapDeltaConfig.RotationMode.values()),
            doubleSlider(Component.translatable("gui.odysseymap.setting.transparency").getString(), OpenMapDeltaConfig.TRANSPARENCY, 0.1, 1.0, 0.01, false, MapSettingsScreen::formatDouble),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.border_style").getString(), OpenMapDeltaConfig.BORDER_STYLE, OpenMapDeltaConfig.BorderStyle.values()),
            doubleSlider(Component.translatable("gui.odysseymap.setting.player_scale").getString(), OpenMapDeltaConfig.MINIMAP_PLAYER_SCALE, 0.25, 2.0, 0.01, false, MapSettingsScreen::formatDouble),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.minimap_player_icon").getString(), OpenMapDeltaConfig.MINIMAP_PLAYER_ICON, OpenMapDeltaConfig.MinimapPlayerIcon.values()),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.minimap_other_player_icon").getString(), OpenMapDeltaConfig.MINIMAP_OTHER_PLAYER_ICON, OpenMapDeltaConfig.MinimapPlayerIcon.values()),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.minimap_player_label_inside").getString(), OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE, OpenMapDeltaConfig.MinimapPlayerLabelMode.values()) {
                @Override public Component tooltip() {
                    return message().copy().append("\n")
                            .append(Component.translatable("gui.odysseymap.setting.minimap_player_label_inside.hint"));
                }
            },
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.minimap_player_label_outside").getString(), OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE, OpenMapDeltaConfig.MinimapPlayerLabelMode.values()) {
                @Override public Component tooltip() {
                    return message().copy().append("\n")
                            .append(Component.translatable("gui.odysseymap.setting.minimap_player_label_outside.hint"));
                }
            },
            new BooleanOption(Component.translatable("gui.odysseymap.setting.minimap_rotate_player_head").getString(), OpenMapDeltaConfig.MINIMAP_ROTATE_PLAYER_HEAD),
            doubleSlider(Component.translatable("gui.odysseymap.setting.waypoint_scale").getString(), OpenMapDeltaConfig.MINIMAP_WAYPOINT_SCALE, 0.25, 2.0, 0.01, false, MapSettingsScreen::formatDouble),
            new SettingOption() {
                public String label() { return Component.translatable("screen.odysseymap.minimap_info").getString(); }
                public String getValueText() { return Component.translatable("gui.odysseymap.info.configure").getString(); }
                public void onClick() { if (minecraft != null) minecraft.setScreen(new MinimapInfoScreen(MapSettingsScreen.this)); }
            },
            new BooleanOption(Component.translatable("gui.odysseymap.setting.hide_in_screens").getString(), OpenMapDeltaConfig.HIDE_WHEN_SCREEN_OPEN)
        );

        List<SettingOption> tab1Options = List.of(
            new BooleanOption(Component.translatable("gui.odysseymap.setting.players").getString(), OpenMapDeltaConfig.SHOW_PLAYERS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.waypoints").getString(), OpenMapDeltaConfig.SHOW_WAYPOINTS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.shared_waypoints").getString(), OpenMapDeltaConfig.SHOW_SHARED_WAYPOINTS),
            doubleSlider(Component.translatable("gui.odysseymap.waypoint_hide_distance").getString(), OpenMapDeltaConfig.WAYPOINT_HIDE_DISTANCE, 0.0, 128.0, 0.5, true, MapSettingsScreen::formatDouble),
            doubleSlider(Component.translatable("gui.odysseymap.waypoint_label_fade_distance").getString(), OpenMapDeltaConfig.WAYPOINT_LABEL_FADE_DISTANCE, 0.0, 128.0, 0.5, true, MapSettingsScreen::formatDouble),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.cluster_label_order").getString(), OpenMapDeltaConfig.WORLD_WAYPOINT_CLUSTER_LABEL_ORDER, WaypointClusterOrder.Order.values()),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.beds").getString(), OpenMapDeltaConfig.SHOW_BEDS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.record_non_spawn_sleep").getString(), OpenMapDeltaConfig.RECORD_NON_SPAWN_SLEEP),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.portals").getString(), OpenMapDeltaConfig.SHOW_PORTALS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.structures").getString(), OpenMapDeltaConfig.SHOW_STRUCTURES),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.entities").getString(), OpenMapDeltaConfig.SHOW_ENTITIES),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.distance").getString(), OpenMapDeltaConfig.SHOW_MARKER_DISTANCE),
            integerSlider(Component.translatable("gui.odysseymap.setting.minimap_label_limit").getString(), OpenMapDeltaConfig.MINIMAP_LABEL_LIMIT, -1, 500, 1, false, value -> value == -1 ? Component.translatable("gui.odysseymap.icons_all").getString() : Integer.toString(value), null),
            doubleSlider(Component.translatable("gui.odysseymap.setting.minimap_name_distance").getString(), OpenMapDeltaConfig.MINIMAP_WAYPOINT_NAME_DISTANCE, 0.0, 1_000_000.0, 1.0, true, value -> value == 0.0 ? Component.translatable("gui.odysseymap.icons_all").getString() : formatDouble(value) + "m"),
            doubleSlider(Component.translatable("gui.odysseymap.setting.minimap_edge_offset").getString(), OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET, -16.0, 8.0, 0.25, false, value -> (value > 0.0 ? "+" : "") + formatDouble(value)),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.clamp").getString(), OpenMapDeltaConfig.STICK_MARKERS_TO_BORDER)
        );

        List<SettingOption> tab2Options = List.of(
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.fullscreen_player_icon").getString(), OpenMapDeltaConfig.FULLSCREEN_PLAYER_ICON, OpenMapDeltaConfig.MinimapPlayerIcon.values()),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.fullscreen_rotate_player_head").getString(), OpenMapDeltaConfig.FULLSCREEN_ROTATE_PLAYER_HEAD),
            integerSlider(Component.translatable("gui.odysseymap.setting.player_max_size").getString(), OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE, 4, 64, 1, false, Integer::toString, null),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.fullscreen").getString(), OpenMapDeltaConfig.MAP_FULLSCREEN_ENABLED),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.coordinates").getString(), OpenMapDeltaConfig.MAP_SHOW_COORDINATES),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.waypoints").getString(), OpenMapDeltaConfig.MAP_SHOW_WAYPOINTS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.player_marker").getString(), OpenMapDeltaConfig.MAP_SHOW_PLAYER_MARKER),
            integerSlider(Component.translatable("gui.odysseymap.setting.default_zoom").getString(), OpenMapDeltaConfig.FULLSCREEN_DEFAULT_ZOOM, 1, 18, 1, false, Integer::toString, null),
            new LabelZoomSliderOption(),
            doubleSlider(Component.translatable("gui.odysseymap.setting.fullscreen_label_scale").getString(), OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_SCALE, 0.25, 2.0, 0.01, false, MapSettingsScreen::formatDouble),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.zoom_buttons").getString(), OpenMapDeltaConfig.FULLSCREEN_SHOW_ZOOM_BUTTONS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.chunk_borders").getString(), OpenMapDeltaConfig.FULLSCREEN_SHOW_GRID),
            doubleSlider(Component.translatable("gui.odysseymap.setting.chunk_width").getString(), OpenMapDeltaConfig.FULLSCREEN_GRID_THICKNESS, 0.25, 4.0, 0.05, false, MapSettingsScreen::formatDouble),
            integerSlider(Component.translatable("gui.odysseymap.setting.max_waypoints").getString(), OpenMapDeltaConfig.MAP_MAX_WAYPOINTS_RENDERED, 10, 500, 1, false, Integer::toString, null),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.safe_render").getString(), OpenMapDeltaConfig.MAP_SAFE_RENDER_MODE),
            integerSlider(Component.translatable("gui.odysseymap.setting.cave_layer_step").getString(), OpenMapDeltaConfig.CAVE_LAYER_STEP, 1, 64, 1, false, Integer::toString, () -> OpenMapDeltaClient.setSelectedMapLayer(OpenMapDeltaClient.getSelectedMapLayer())),
            integerSlider(Component.translatable("gui.odysseymap.setting.navigation_max_targets").getString(), OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS, 1, 16, 1, false, Integer::toString, null),
            doubleSlider(Component.translatable("gui.odysseymap.setting.navigation_arrival_distance").getString(), OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE, 0.0, 128.0, 0.5, true, MapSettingsScreen::formatDouble)
        );

        List<SettingOption> tab3Options = List.of(
            new BooleanOption(Component.translatable("gui.odysseymap.setting.biome_tint").getString(), OpenMapDeltaConfig.BIOME_GRASS_TINT),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.async_generation").getString(), OpenMapDeltaConfig.ASYNC_MAP_GENERATION),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.compass").getString(), OpenMapDeltaConfig.SHOW_COMPASS),
            new BooleanOption(Component.translatable("gui.odysseymap.setting.coordinates").getString(), OpenMapDeltaConfig.SHOW_COORDINATES),
            new EnumOption<>(Component.translatable("gui.odysseymap.setting.performance").getString(), OpenMapDeltaConfig.PERFORMANCE_MODE, OpenMapDeltaConfig.PerformanceMode.values()),
            integerSlider(Component.translatable("gui.odysseymap.setting.columns").getString(), OpenMapDeltaConfig.COLUMNS_PER_TICK, 32, 4096, 1, true, Integer::toString, null),
            integerSlider(Component.translatable("gui.odysseymap.setting.scan_interval").getString(), OpenMapDeltaConfig.SCAN_INTERVAL_FRAMES, 1, 20, 1, false, Integer::toString, null),
            integerSlider(Component.translatable("gui.odysseymap.setting.border_thickness").getString(), OpenMapDeltaConfig.BORDER_THICKNESS, 0, 8, 1, false, Integer::toString, null)
        );

        List<SettingOption> tab4Options = List.of(
            new SettingOption() {
                public String label() { return Component.translatable("gui.odysseymap.setting.server_config").getString(); }
                public String getValueText() { return Component.translatable("gui.odysseymap.info.configure").getString(); }
                public Component tooltip() { return Component.translatable("gui.odysseymap.setting.server_config.hint"); }
                public boolean active() { return ServerConfigClient.canManage(); }
                public void onClick() {
                    if (minecraft != null && ServerConfigClient.canManage()) {
                        minecraft.setScreen(new ServerSettingsScreen(MapSettingsScreen.this));
                    }
                }
            },
            new BooleanOption(Component.translatable("gui.odysseymap.setting.map_mod_enabled").getString(), OpenMapDeltaConfig.MAP_MOD_ENABLED),
            new SettingOption() {
                public String label() { return Component.translatable("gui.odysseymap.setting.map_mod_selection").getString(); }
                public String getValueText() { return Component.translatable("gui.odysseymap.info.configure").getString(); }
                public void onClick() {
                    OpenMapDeltaConfig.save();
                    minecraft.setScreen(new MapModSelectionScreen(MapSettingsScreen.this));
                }
            },
            new BooleanOption(Component.translatable("gui.odysseymap.setting.external_players").getString(), OpenMapDeltaConfig.EXTERNAL_PLAYERS_ENABLED),
            new SettingOption() {
                public String label() { return Component.translatable("screen.odysseymap.bluemap").getString(); }
                public String getValueText() { return Component.translatable("gui.odysseymap.info.configure").getString(); }
                public void onClick() {
                    if (minecraft != null) minecraft.setScreen(new BlueMapSettingsScreen(MapSettingsScreen.this));
                }
            },
            new BooleanOption(Component.translatable("gui.odysseymap.create_stations").getString(), OpenMapDeltaConfig.CREATE_STATION_WAYPOINTS),
            integerSlider(Component.translatable("gui.odysseymap.create_station_grouping_radius").getString(), OpenMapDeltaConfig.CREATE_STATION_GROUPING_RADIUS, 0, 1_000_000, 1, true, value -> value == 0 ? Component.translatable("gui.odysseymap.value.off").getString() : Integer.toString(value), null),
            new BooleanOption(Component.translatable("gui.odysseymap.mtr_stations").getString(), OpenMapDeltaConfig.MTR_STATION_WAYPOINTS)
        );

        List<SettingOption> tab5Options = List.of(
            new DeathBooleanOption("gui.odysseymap.setting.death", OpenMapDeltaConfig.SHOW_DEATH),
            new DeathBooleanOption("gui.odysseymap.setting.smart_deathpoints", OpenMapDeltaConfig.SMART_DEATHPOINTS),
            new DeathBooleanOption("gui.odysseymap.setting.keep_inventory_deathpoints", OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS),
            new IntegerSliderOption(Component.translatable("gui.odysseymap.setting.death_black_retention").getString(),
                    OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS, 0, 86400, 1, true,
                    value -> value == 0 ? Component.translatable("gui.odysseymap.value.forever").getString() : Integer.toString(value),
                    null, Component.translatable("gui.odysseymap.setting.death_black_retention.hint")),
            new DoubleSliderOption(Component.translatable("gui.odysseymap.setting.death_beacon_min_opacity").getString(),
                    OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY, 0, 1, 0.01, false,
                    value -> String.format(Locale.ROOT, "%.0f%%", value * 100.0),
                    Component.translatable("gui.odysseymap.setting.death_beacon_min_opacity.hint")),
            new DoubleSliderOption(Component.translatable("gui.odysseymap.setting.death_beacon_max_opacity").getString(),
                    OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY, 0, 1, 0.01, false,
                    value -> String.format(Locale.ROOT, "%.0f%%", value * 100.0),
                    Component.translatable("gui.odysseymap.setting.death_beacon_max_opacity.hint")),
            new SettingOption() {
                public String label() { return Component.translatable("gui.odysseymap.setting.restore_deathpoint").getString(); }
                public String getValueText() { return ""; }
                public boolean active() { return OpenMapDeltaConfig.SHOW_DEATH.get() && net.aosankaku.openmapdelta.marker.MarkerManager.get().hasCachedDeathPoint(); }
                public Component tooltip() { return Component.translatable("gui.odysseymap.setting.restore_deathpoint.hint"); }
                public void onClick() {
                    if (net.aosankaku.openmapdelta.marker.MarkerManager.get().restoreCachedDeathPoint()
                            && minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.translatable("gui.odysseymap.info.deathpoint_restored"), false);
                    }
                }
            },
            new SettingOption() {
                public String label() { return Component.translatable("gui.odysseymap.setting.cheap_death_items").getString(); }
                public String getValueText() { return OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.get(); }
                public AbstractWidget createWidget(int x, int y, int width, int height) {
                    EditBox input = new EditBox(font, x, y, width, height, Component.translatable("gui.odysseymap.setting.cheap_death_items"));
                    input.setMaxLength(8192);
                    input.setValue(OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.get());
                    input.setTooltip(Tooltip.create(Component.translatable("gui.odysseymap.setting.cheap_death_items.hint")));
                    input.setResponder(value -> { OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.set(value); OpenMapDeltaConfig.save(); });
                    return input;
                }
            }
        );
        List<List<SettingOption>> allTabsOptions = List.of(tab0Options, tab1Options, tab2Options, tab3Options, tab4Options, tab5Options);

        String[] groupKeys = {
            "gui.odysseymap.tab.minimap",
            "gui.odysseymap.tab.markers",
            "gui.odysseymap.tab.fullscreen",
            "gui.odysseymap.tab.performance",
            "gui.odysseymap.tab.integrations",
            "gui.odysseymap.tab.deathpoints"
        };
        int initialWidth = Math.max(1, settingsList.getRowWidth() - 8);
        for (int tab = 0; tab < allTabsOptions.size(); tab++) {
            settingsList.addHeading(Component.translatable(groupKeys[tab]));
            for (SettingOption option : allTabsOptions.get(tab)) {
                AbstractWidget widget = option.createWidget(settingsList.getRowLeft(), 0, initialWidth, 20);
                widget.active = option.active();
                if (widget instanceof EditBox) settingsList.addHeading(Component.literal(option.label()));
                settingsList.addWidget(widget, 0, false);
                settingWidgets.add(new SettingWidget(widget, option));
            }
            if (tab == 3) {
                settingsList.addHeading(Component.translatable("gui.odysseymap.setting.border_color"));
                settingsList.addWidget(borderHexBox, 220, true);
            }
        }
        settingsList.setScrollAmount(savedScrollAmount);
        addRenderableOnly(settingsList);
        addWidget(settingsList);

        int footerWidth = Math.min(150, Math.max(1, (width - 24) / 2));
        int footerGap = 8;
        int footerLeft = centerX - (footerWidth * 2 + footerGap) / 2;
        int footerTop = Math.max(0, height - 28);
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.import_waypoints"), b -> {
                    if (minecraft != null) minecraft.setScreen(new WaypointImportScreen(this));
                }).bounds(footerLeft, footerTop, footerWidth, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(footerLeft + footerWidth + footerGap, footerTop, footerWidth, 20).build());
        if (savedScrollAmount > 0 && minecraft != null && minecraft.getLastInputType().isKeyboard()) setInitialFocus(doneButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        for (SettingWidget widget : settingWidgets) {
            widget.widget.active = widget.option.active();
            if (widget.widget instanceof Button button) {
                String messageText = widget.option.messageText();
                if (!messageText.equals(widget.lastMessageText)) {
                    button.setMessage(widget.option.message());
                    button.setTooltip(Tooltip.create(widget.option.tooltip()));
                    widget.lastMessageText = messageText;
                }
            }
        }
    }

    private static class DeathBooleanOption implements SettingOption {
        private final String key;
        private final ConfigValue<Boolean> value;
        DeathBooleanOption(String key, ConfigValue<Boolean> value) { this.key = key; this.value = value; }
        public String label() { return Component.translatable(key).getString(); }
        public String getValueText() { return Component.translatable(value.get() ? "gui.odysseymap.value.on" : "gui.odysseymap.value.off").getString(); }
        public Component tooltip() { return Component.translatable(key + ".hint"); }
        public boolean active() {
            return value == OpenMapDeltaConfig.SHOW_DEATH || (value == OpenMapDeltaConfig.SMART_DEATHPOINTS
                    ? OpenMapDeltaConfig.SHOW_DEATH.get() : OpenMapDeltaConfig.effectiveSmartDeathpoints());
        }
        public void onClick() {
            if (value == OpenMapDeltaConfig.SHOW_DEATH) OpenMapDeltaConfig.setDeathpointsEnabled(!value.get());
            else if (value == OpenMapDeltaConfig.SMART_DEATHPOINTS) OpenMapDeltaConfig.setSmartDeathpointsEnabled(!value.get());
            else value.set(!value.get());
            OpenMapDeltaConfig.save();
        }
    }

    // Helper implementations of SettingOption
    private static class LabelZoomSliderOption implements SettingOption {
        public String label() { return Component.translatable("gui.odysseymap.setting.fullscreen_label_min_zoom").getString(); }
        public String getValueText() { return String.format(Locale.ROOT, "%.2f×", OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM.get()); }
        public AbstractWidget createWidget(int x, int y, int width, int height) {
            return new LabelZoomSlider(x, y, width, height);
        }
    }

    private static class LabelZoomSlider extends AbstractSliderButton {
        LabelZoomSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                    FullscreenMapZoom.waypointLabelZoomToSlider(OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM.get()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            LabelZoomSliderOption option = new LabelZoomSliderOption();
            setMessage(Component.literal(option.label() + ": " + option.getValueText()));
            setTooltip(Tooltip.create(getMessage().copy().append("\n")
                    .append(Component.translatable("gui.odysseymap.setting.fullscreen_label_min_zoom_hint"))));
        }

        @Override
        protected void applyValue() {
            double minimum = FullscreenMapZoom.waypointLabelZoomFromSlider(value);
            if (Double.compare(minimum, OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM.get()) != 0) {
                OpenMapDeltaConfig.FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM.set(minimum);
                OpenMapDeltaConfig.save();
            }
        }
    }

    private static class BooleanOption implements SettingOption {
        private final String label;
        private final ConfigValue<Boolean> configValue;

        BooleanOption(String label, ConfigValue<Boolean> configValue) {
            this.label = label;
            this.configValue = configValue;
        }

        @Override
        public String label() { return label; }

        @Override
        public String getValueText() {
            return Component.translatable(configValue.get() ? "gui.odysseymap.value.on" : "gui.odysseymap.value.off").getString();
        }

        @Override
        public void onClick() {
            configValue.set(!configValue.get());
            OpenMapDeltaConfig.save();
        }
    }

    private static class EnumOption<E extends Enum<E>> implements SettingOption {
        private final String label;
        private final ConfigValue<E> configValue;
        private final E[] values;

        EnumOption(String label, ConfigValue<E> configValue, E[] values) {
            this.label = label;
            this.configValue = configValue;
            this.values = values;
        }

        @Override
        public String label() { return label; }

        @Override
        public String getValueText() {
            return Component.translatable("gui.odysseymap.value." + configValue.get().name().toLowerCase(Locale.ROOT)).getString();
        }

        @Override
        public void onClick() {
            E current = configValue.get();
            int nextIdx = (current.ordinal() + 1) % values.length;
            configValue.set(values[nextIdx]);
            OpenMapDeltaConfig.save();
        }
    }

    private static SettingOption integerSlider(String label, ConfigValue<Integer> configValue,
                                               int minimum, int maximum, int step, boolean logarithmic,
                                               java.util.function.IntFunction<String> formatter, Runnable onChange) {
        return new IntegerSliderOption(label, configValue, minimum, maximum, step, logarithmic, formatter, onChange);
    }

    private static SettingOption doubleSlider(String label, ConfigValue<Double> configValue,
                                              double minimum, double maximum, double step, boolean logarithmic,
                                              java.util.function.DoubleFunction<String> formatter) {
        return new DoubleSliderOption(label, configValue, minimum, maximum, step, logarithmic, formatter);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private int[] currentMinimapCaveYBounds() {
        if (minecraft != null && minecraft.level != null) {
            int minimum = Math.max(-4096, minecraft.level.getMinBuildHeight());
            int maximum = Math.min(4096, minecraft.level.getMaxBuildHeight() - 1);
            if (minimum < maximum) return new int[]{minimum, maximum};
        }
        return new int[]{-64, 319};
    }

    private static class MinimapCaveYSliderOption implements SettingOption {
        private final int minimum;
        private final int maximum;
        private final String label = Component.translatable("gui.odysseymap.setting.minimap_cave_y").getString();

        MinimapCaveYSliderOption(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public String label() { return label; }
        public String getValueText() { return Integer.toString(clamp(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get())); }

        private int clamp(int value) { return Math.max(minimum, Math.min(maximum, value)); }

        @Override
        public AbstractWidget createWidget(int x, int y, int width, int height) {
            return new NumericSettingSlider(x, y, width, height, label,
                    new SettingSliderRange(minimum, maximum, 1, false),
                    () -> clamp(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get()),
                    value -> {
                        int next = (int) Math.round(value);
                        if (OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == next) return;
                        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(next);
                        OpenMapDeltaConfig.save();
                    },
                    value -> Integer.toString((int) Math.round(value)),
                    Component.translatable("gui.odysseymap.setting.minimap_cave_y.hint"));
        }
    }

    private static class IntegerSliderOption implements SettingOption {
        private final String label;
        private final ConfigValue<Integer> configValue;
        private final SettingSliderRange range;
        private final java.util.function.IntFunction<String> formatter;
        private final Runnable onChange;
        private final Component tooltipHint;

        IntegerSliderOption(String label, ConfigValue<Integer> configValue, int minimum, int maximum, int step,
                            boolean logarithmic, java.util.function.IntFunction<String> formatter, Runnable onChange) {
            this(label, configValue, minimum, maximum, step, logarithmic, formatter, onChange, Component.empty());
        }

        IntegerSliderOption(String label, ConfigValue<Integer> configValue, int minimum, int maximum, int step,
                            boolean logarithmic, java.util.function.IntFunction<String> formatter, Runnable onChange,
                            Component tooltipHint) {
            this.label = label;
            this.configValue = configValue;
            this.range = new SettingSliderRange(minimum, maximum, step, logarithmic);
            this.formatter = formatter;
            this.onChange = onChange;
            this.tooltipHint = tooltipHint;
        }

        public String label() { return label; }
        public String getValueText() { return formatter.apply(configValue.get()); }

        @Override
        public AbstractWidget createWidget(int x, int y, int width, int height) {
            return new NumericSettingSlider(x, y, width, height, label, range,
                    () -> configValue.get(), value -> {
                        int next = (int) Math.round(value);
                        if (configValue.get() == next) return;
                        configValue.set(next);
                        OpenMapDeltaConfig.save();
                        if (onChange != null) onChange.run();
                    }, value -> formatter.apply((int) Math.round(value)), tooltipHint);
        }
    }

    private static class DoubleSliderOption implements SettingOption {
        private final String label;
        private final ConfigValue<Double> configValue;
        private final SettingSliderRange range;
        private final java.util.function.DoubleFunction<String> formatter;
        private final Component tooltipHint;

        DoubleSliderOption(String label, ConfigValue<Double> configValue, double minimum, double maximum,
                           double step, boolean logarithmic, java.util.function.DoubleFunction<String> formatter) {
            this(label, configValue, minimum, maximum, step, logarithmic, formatter, Component.empty());
        }

        DoubleSliderOption(String label, ConfigValue<Double> configValue, double minimum, double maximum,
                           double step, boolean logarithmic, java.util.function.DoubleFunction<String> formatter,
                           Component tooltipHint) {
            this.label = label;
            this.configValue = configValue;
            this.range = new SettingSliderRange(minimum, maximum, step, logarithmic);
            this.formatter = formatter;
            this.tooltipHint = tooltipHint;
        }

        public String label() { return label; }
        public String getValueText() { return formatter.apply(configValue.get()); }

        @Override
        public AbstractWidget createWidget(int x, int y, int width, int height) {
            return new NumericSettingSlider(x, y, width, height, label, range,
                    () -> configValue.get(), value -> {
                        if (Double.compare(configValue.get(), value) == 0) return;
                        configValue.set(value);
                        OpenMapDeltaConfig.save();
                    }, formatter, tooltipHint);
        }
    }
}
