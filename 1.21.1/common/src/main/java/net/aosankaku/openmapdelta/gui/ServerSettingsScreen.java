// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.aosankaku.openmapdelta.server.ServerConfigValues;
import net.aosankaku.openmapdelta.web.WebMapUrls;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Small, live editor for the explicitly exposed server-owned settings. */
public final class ServerSettingsScreen extends Screen {
    private final Screen parent;
    private EditBox webMapUrlBox;
    private EditBox maxWaypointsBox;
    private NumericSettingSlider maxWaypointsSlider;
    private Button caveMapButton;
    private Button webMapButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private boolean draftInitialized;
    private boolean updatingFields;
    private boolean caveMapDraft;
    private boolean webMapDraft;
    private boolean caveMapDirty;
    private boolean webMapDirty;
    private boolean webMapUrlDirty;
    private boolean maxWaypointsDirty;
    private int webMapDebounce;
    private int maxWaypointsDebounce;
    private String webMapUrlDraft = "";
    private String maxWaypointsDraft = "";
    private String localErrorKey;
    private long observedStateVersion = -1L;
    private long observedSessionVersion = -1L;

    public ServerSettingsScreen(Screen parent) {
        super(Component.translatable("screen.odysseymap.server_config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(420, Math.max(1, width - 16));
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(4, (height - 242) / 2);

        int right = panelLeft + panelWidth - 10;
        caveMapButton = addRenderableWidget(Button.builder(toggleText("allow_cave_map", false), button -> toggleCaveMap())
                .bounds(right - 112, panelTop + 32, 112, 20).build());
        caveMapButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.odysseymap.server_config.allow_cave_map.hint")));
        webMapButton = addRenderableWidget(Button.builder(toggleText("web_map_enabled", false), button -> toggleWebMap())
                .bounds(right - 112, panelTop + 56, 112, 20).build());
        webMapButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.odysseymap.server_config.web_map_enabled.hint")));

        webMapUrlBox = new EditBox(font, panelLeft + 10, panelTop + 100, panelWidth - 20, 18,
                Component.translatable("gui.odysseymap.server_config.web_map_url"));
        webMapUrlBox.setMaxLength(ServerConfigValues.MAX_URL_LENGTH);
        webMapUrlBox.setTooltip(Tooltip.create(Component.translatable(
                "gui.odysseymap.server_config.web_map_url.hint")));
        webMapUrlBox.setResponder(this::onWebMapUrlChanged);
        addRenderableWidget(webMapUrlBox);

        maxWaypointsBox = new EditBox(font, right - 80, panelTop + 120, 80, 18,
                Component.translatable("gui.odysseymap.server_config.max_shared_waypoints"));
        maxWaypointsBox.setMaxLength(4);
        maxWaypointsBox.setTooltip(Tooltip.create(Component.translatable(
                "gui.odysseymap.server_config.max_shared_waypoints.hint")));
        maxWaypointsBox.setResponder(this::onMaxWaypointsChanged);
        addRenderableWidget(maxWaypointsBox);

        maxWaypointsSlider = new NumericSettingSlider(panelLeft + 10, panelTop + 140, panelWidth - 20, 18,
                "", new SettingSliderRange(0, ServerConfigValues.MAX_SHARED_WAYPOINTS, 1, false),
                this::maxSliderValue, this::onMaxSliderChanged, value -> Integer.toString((int) Math.round(value)),
                Component.translatable("gui.odysseymap.server_config.max_shared_waypoints.hint"));
        addRenderableWidget(maxWaypointsSlider);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(panelLeft + 10, panelTop + 212, panelWidth - 20, 20).build());

        if (draftInitialized) {
            setBoxValue(webMapUrlBox, webMapUrlDraft);
            setBoxValue(maxWaypointsBox, maxWaypointsDraft);
        }
        syncFromServer();
        updateWidgetState();
    }

    private void toggleCaveMap() {
        ServerConfigSnapshot state = ServerConfigClient.snapshot();
        if (state == null || !ServerConfigClient.canManage()) return;
        caveMapDraft = !caveMapDraft;
        caveMapDirty = caveMapDraft != state.values().allowCaveMap();
        sendToggleDraft();
        updateWidgetState();
    }

    private void toggleWebMap() {
        ServerConfigSnapshot state = ServerConfigClient.snapshot();
        if (state == null || !ServerConfigClient.canManage()) return;
        webMapDraft = !webMapDraft;
        webMapDirty = webMapDraft != state.values().webMapEnabled();
        sendToggleDraft();
        updateWidgetState();
    }

    private void sendToggleDraft() {
        ServerConfigSnapshot state = ServerConfigClient.snapshot();
        if (state == null || !ServerConfigClient.canManage()) return;
        ServerConfigValues base = ServerConfigClient.baseValues();
        if (base == null) return;
        boolean validUrl = validUrlDraft(webMapDraft);
        boolean validCount = validMaxDraft();
        String url = validUrlDraft(false) ? webMapUrlDraft.trim() : base.webMapUrl();
        int maximum = validCount ? Integer.parseInt(maxWaypointsDraft.trim()) : base.maxSharedWaypoints();
        boolean publish = webMapDraft && (validUrl && !url.isEmpty() || base.webMapEnabled());
        String validationError = !validUrl ? "gui.odysseymap.server_config.status.invalid_url"
                : !validCount ? "gui.odysseymap.server_config.status.invalid_count" : null;
        submitValues(caveMapDraft, publish, url, maximum);
        if (validationError != null) {
            ServerConfigClient.beginDraft();
            localErrorKey = validationError;
        }
    }

    private void onWebMapUrlChanged(String value) {
        if (updatingFields) return;
        webMapUrlDraft = value;
        webMapUrlDirty = true;
        webMapDebounce = 10;
        localErrorKey = null;
        ServerConfigClient.beginDraft();
    }

    private void onMaxWaypointsChanged(String value) {
        if (updatingFields) return;
        maxWaypointsDraft = value;
        maxWaypointsDirty = true;
        maxWaypointsDebounce = 10;
        localErrorKey = null;
        ServerConfigClient.beginDraft();
        if (maxWaypointsSlider != null) maxWaypointsSlider.syncToGetter();
    }

    private double maxSliderValue() {
        if (validMaxDraft()) return Integer.parseInt(maxWaypointsDraft.trim());
        ServerConfigSnapshot state = ServerConfigClient.snapshot();
        return state != null && state.values() != null ? state.values().maxSharedWaypoints() : 0;
    }

    private void onMaxSliderChanged(double value) {
        maxWaypointsDraft = Integer.toString((int) Math.round(value));
        maxWaypointsDirty = true;
        maxWaypointsDebounce = 10;
        localErrorKey = null;
        ServerConfigClient.beginDraft();
        setBoxValue(maxWaypointsBox, maxWaypointsDraft);
    }

    private void flushTextDraft() {
        ServerConfigSnapshot state = ServerConfigClient.snapshot();
        if (state == null || !ServerConfigClient.canManage()) return;
        boolean validUrl = validUrlDraft(webMapDraft);
        boolean validCount = validMaxDraft();
        String validationError = !validUrl ? "gui.odysseymap.server_config.status.invalid_url"
                : !validCount ? "gui.odysseymap.server_config.status.invalid_count" : null;
        localErrorKey = validationError;

        if (validUrl && validCount) {
            submitValues(caveMapDraft, webMapDraft, webMapUrlDraft.trim(), Integer.parseInt(maxWaypointsDraft.trim()));
            return;
        }

        // Replace any older queued text draft with a valid state that preserves the latest toggle intent.
        ServerConfigValues base = ServerConfigClient.baseValues();
        if (base == null) return;
        String url = validUrlDraft(false) ? webMapUrlDraft.trim() : base.webMapUrl();
        int maximum = validCount ? Integer.parseInt(maxWaypointsDraft.trim()) : base.maxSharedWaypoints();
        boolean publish = webMapDraft && (validUrl && !url.isEmpty() || base.webMapEnabled());
        submitValues(caveMapDraft, publish, url, maximum);
        if (validationError != null) ServerConfigClient.beginDraft();
        localErrorKey = validationError;
    }

    private void submitValues(boolean allowCaveMap, boolean publishWebMap, String url, int maximum) {
        try {
            ServerConfigClient.submit(new ServerConfigValues(allowCaveMap, publishWebMap, url, maximum));
            localErrorKey = null;
        } catch (IllegalArgumentException invalid) {
            ServerConfigClient.clearQueuedEdit();
            localErrorKey = "gui.odysseymap.server_config.status.invalid_url";
        }
    }

    private boolean validUrlDraft(boolean enabled) {
        if (webMapUrlDraft == null || webMapUrlDraft.length() > ServerConfigValues.MAX_URL_LENGTH
                || webMapUrlDraft.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ServerConfigValues.MAX_URL_BYTES) return false;
        String trimmed = webMapUrlDraft.trim();
        if (trimmed.isEmpty()) return !enabled;
        return WebMapUrls.shared(true, trimmed).isPresent();
    }

    private boolean validMaxDraft() {
        if (maxWaypointsDraft == null || maxWaypointsDraft.isBlank()) return false;
        try {
            int value = Integer.parseInt(maxWaypointsDraft.trim());
            return value >= 0 && value <= ServerConfigValues.MAX_SHARED_WAYPOINTS;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private void syncFromServer() {
        long session = ServerConfigClient.sessionVersion();
        if (observedSessionVersion != session) {
            clearDraft();
            observedSessionVersion = session;
        }
        ServerConfigSnapshot state = ServerConfigClient.snapshot();
        if (state == null) {
            if (draftInitialized && observedStateVersion != ServerConfigClient.stateVersion()) clearDraft();
            observedStateVersion = ServerConfigClient.stateVersion();
            return;
        }
        if (!state.administrator() || state.values() == null) {
            clearDraft();
            observedStateVersion = ServerConfigClient.stateVersion();
            return;
        }

        boolean newState = observedStateVersion != ServerConfigClient.stateVersion();
        String status = ServerConfigClient.statusTranslationKey();
        boolean conflict = status.equals("gui.odysseymap.server_config.status.conflict");
        boolean terminalDiscard = isTerminalDiscardStatus(status);
        if (!draftInitialized || conflict || (newState && terminalDiscard)) {
            copyServerValues(state.values());
            if (terminalDiscard) localErrorKey = null;
        } else if (newState && !ServerConfigClient.hasPendingWrite()) {
            ServerConfigValues values = state.values();
            if (!caveMapDirty || caveMapDraft == values.allowCaveMap()) {
                caveMapDraft = values.allowCaveMap();
                caveMapDirty = false;
            }
            if (!webMapDirty || webMapDraft == values.webMapEnabled()) {
                webMapDraft = values.webMapEnabled();
                webMapDirty = false;
            }
            if (!webMapUrlDirty || (webMapUrlBox != null && webMapUrlBox.getValue().trim().equals(values.webMapUrl()))) {
                webMapUrlDraft = values.webMapUrl();
                webMapUrlDirty = false;
                webMapDebounce = 0;
                setBoxValue(webMapUrlBox, webMapUrlDraft);
            }
            if (!maxWaypointsDirty || (maxWaypointsBox != null
                    && maxWaypointsBox.getValue().equals(Integer.toString(values.maxSharedWaypoints())))) {
                maxWaypointsDraft = Integer.toString(values.maxSharedWaypoints());
                maxWaypointsDirty = false;
                maxWaypointsDebounce = 0;
                setBoxValue(maxWaypointsBox, maxWaypointsDraft);
                if (maxWaypointsSlider != null) maxWaypointsSlider.syncToGetter();
            }
        }
        observedStateVersion = ServerConfigClient.stateVersion();
    }

    private boolean isTerminalDiscardStatus(String status) {
        return status.equals("gui.odysseymap.server_config.status.timeout")
                || status.equals("gui.odysseymap.server_config.status.send_error")
                || status.equals("gui.odysseymap.server_config.status.permission")
                || status.equals("gui.odysseymap.server_config.status.invalid")
                || status.equals("gui.odysseymap.server_config.status.storage_error");
    }

    private void copyServerValues(ServerConfigValues values) {
        caveMapDraft = values.allowCaveMap();
        webMapDraft = values.webMapEnabled();
        webMapUrlDraft = values.webMapUrl();
        maxWaypointsDraft = Integer.toString(values.maxSharedWaypoints());
        caveMapDirty = webMapDirty = webMapUrlDirty = maxWaypointsDirty = false;
        webMapDebounce = maxWaypointsDebounce = 0;
        localErrorKey = null;
        draftInitialized = true;
        setBoxValue(webMapUrlBox, webMapUrlDraft);
        setBoxValue(maxWaypointsBox, maxWaypointsDraft);
        if (maxWaypointsSlider != null) maxWaypointsSlider.syncToGetter();
    }

    private void clearDraft() {
        caveMapDraft = false;
        webMapDraft = false;
        webMapUrlDraft = "";
        maxWaypointsDraft = "";
        caveMapDirty = webMapDirty = webMapUrlDirty = maxWaypointsDirty = false;
        webMapDebounce = maxWaypointsDebounce = 0;
        localErrorKey = null;
        draftInitialized = false;
        setBoxValue(webMapUrlBox, "");
        setBoxValue(maxWaypointsBox, "");
        if (maxWaypointsSlider != null) maxWaypointsSlider.syncToGetter();
    }

    private void setBoxValue(EditBox box, String value) {
        if (box == null) return;
        updatingFields = true;
        box.setValue(value);
        updatingFields = false;
    }

    private Component toggleText(String setting, boolean enabled) {
        return Component.translatable("gui.odysseymap.server_config." + setting + ".toggle",
                Component.translatable(enabled ? "gui.odysseymap.value.on" : "gui.odysseymap.value.off"));
    }

    private void updateWidgetState() {
        boolean allowed = ServerConfigClient.canManage();
        if (caveMapButton != null) {
            ServerConfigSnapshot state = ServerConfigClient.snapshot();
            caveMapButton.active = allowed;
            caveMapButton.setMessage(toggleText("allow_cave_map",
                    state != null && state.values() != null && state.values().allowCaveMap()));
            webMapButton.active = allowed;
            webMapButton.setMessage(toggleText("web_map_enabled",
                    state != null && state.values() != null && state.values().webMapEnabled()));
            webMapUrlBox.active = allowed;
            maxWaypointsBox.active = allowed;
            maxWaypointsSlider.active = allowed;
        }
    }

    private Component statusMessage() {
        if (localErrorKey != null) return Component.translatable(localErrorKey);
        return Component.translatable(ServerConfigClient.statusTranslationKey());
    }

    @Override
    public void tick() {
        super.tick();
        syncFromServer();

        boolean flush = false;
        if (webMapDebounce > 0 && --webMapDebounce == 0) flush = true;
        if (maxWaypointsDebounce > 0 && --maxWaypointsDebounce == 0) flush = true;
        if (flush) flushTextDraft();

        updateWidgetState();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 242, 0xF0121219);
        graphics.renderOutline(panelLeft, panelTop, panelWidth, 242, 0xFF505060);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, panelTop + 9, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("gui.odysseymap.server_config.allow_cave_map"),
                panelLeft + 10, panelTop + 38, 0xFFDDDDDD, false);
        graphics.drawString(font, Component.translatable("gui.odysseymap.server_config.web_map_enabled"),
                panelLeft + 10, panelTop + 62, 0xFFDDDDDD, false);
        graphics.drawString(font, Component.translatable("gui.odysseymap.server_config.web_map_url"),
                panelLeft + 10, panelTop + 88, 0xFFDDDDDD, false);
        graphics.drawString(font, Component.translatable("gui.odysseymap.server_config.max_shared_waypoints"),
                panelLeft + 10, panelTop + 122, 0xFFDDDDDD, false);

        List<net.minecraft.util.FormattedCharSequence> statusLines = font.split(statusMessage(), Math.max(1, panelWidth - 20));
        for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
            int color = localErrorKey != null || ServerConfigClient.statusTranslationKey().endsWith("conflict")
                    || ServerConfigClient.statusTranslationKey().endsWith("timeout") ? 0xFFFF7777 : 0xFFAAAAAA;
            graphics.drawString(font, statusLines.get(i), panelLeft + 10, panelTop + 170 + i * 10, color, false);
        }
    }

    @Override
    public void onClose() {
        if (webMapDebounce > 0 || maxWaypointsDebounce > 0) flushTextDraft();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
