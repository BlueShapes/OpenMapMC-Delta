// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.LocationSharing;
import net.aosankaku.openmapdelta.client.LocationNotice;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.util.FormattedCharSequence;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

public class PlayerSelectScreen extends Screen {
    private final FullscreenMapScreen parent;
    private final ResourceKey<Level> dimension;
    private final int x;
    private final int y;
    private final int z;
    private final UUID targetPlayerId;
    private boolean followPlayer;
    private Object sessionConnection;
    private UUID sessionPlayerId;
    private final Set<UUID> selectedIds = new LinkedHashSet<>();
    private String searchText = "";
    private int durationSeconds = LocationNotice.DEFAULT_DURATION_SECONDS;
    private EditBox searchBox;
    private PlayerSelectionWidget selectionWidget;
    private Button sendButton;
    private Component feedback;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;

    public PlayerSelectScreen(FullscreenMapScreen parent, ResourceKey<Level> dimension, int x, int y, int z) {
        this(parent, dimension, x, y, z, null);
    }

    public PlayerSelectScreen(FullscreenMapScreen parent, ResourceKey<Level> dimension, int x, int y, int z,
                              UUID targetPlayerId) {
        super(Component.translatable("screen.odysseymap.notify"));
        this.parent = parent;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetPlayerId = targetPlayerId;
        this.followPlayer = targetPlayerId != null;
    }

    @Override
    protected void init() {
        if (sessionConnection == null && minecraft != null && minecraft.player != null) {
            sessionConnection = minecraft.getConnection();
            sessionPlayerId = minecraft.player.getUUID();
        }
        if (!sessionMatches()) {
            closeInvalidSession();
            return;
        }
        int panelWidth = Math.min(480, Math.max(1, width - 24));
        int left = (width - panelWidth) / 2;
        addRenderableOnly(this::renderPanel);
        searchBox = new EditBox(font, left, 30, panelWidth, 20, Component.translatable("gui.odysseymap.notify.search"));
        searchBox.setHint(Component.translatable("gui.odysseymap.notify.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            if (selectionWidget != null) selectionWidget.setFilter(value);
        });
        addRenderableWidget(searchBox);
        int halfWidth = Math.max(1, (panelWidth - 6) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.notify.all"), b -> {
            if (selectionWidget != null) selectionWidget.selectAllOnline();
            updateSendButton();
        }).bounds(left, 55, halfWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.notify.none"), b -> {
            if (selectionWidget != null) selectionWidget.deselectAll();
            updateSendButton();
        }).bounds(left + halfWidth + 6, 55, halfWidth, 20).build());

        addRenderableWidget(new NumericSettingSlider(left, 80, panelWidth, 20, "",
                new SettingSliderRange(5, 600, 1, true), () -> durationSeconds,
                value -> durationSeconds = (int) Math.round(value), PlayerSelectScreen::durationText,
                Component.empty()));

        int followRowHeight = targetPlayerId == null ? 0 : 25;
        if (targetPlayerId != null) {
            addRenderableWidget(Button.builder(followLabel(), button -> {
                followPlayer = !followPlayer;
                button.setMessage(followLabel());
            }).bounds(left, 105, panelWidth, 20).build());
        }
        listX = left;
        listY = 105 + followRowHeight;
        listWidth = panelWidth;
        listHeight = Math.max(28, height - 205 - followRowHeight);
        selectionWidget = new PlayerSelectionWidget(listX, listY, listWidth, listHeight, selectedIds);
        selectionWidget.setFilter(searchText);
        selectionWidget.refreshOnlinePlayers();
        addRenderableWidget(selectionWidget);

        int buttonWidth = Math.min(120, Math.max(1, (panelWidth - 8) / 2));
        int buttonY = height - 28;
        sendButton = Button.builder(Component.translatable("gui.odysseymap.notify.next"), b -> advanceToMessage())
                .bounds(width / 2 - buttonWidth - 4, buttonY, buttonWidth, 20).build();
        addRenderableWidget(sendButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.cancel"), b -> returnToMap())
                .bounds(width / 2 + 4, buttonY, buttonWidth, 20).build());
        updateSendButton();
    }

    private Component followLabel() {
        return Component.translatable("gui.odysseymap.notify.follow_player",
                Component.translatable(followPlayer ? "gui.odysseymap.value.on" : "gui.odysseymap.value.off"));
    }

    private static String durationText(double value) {
        int seconds = (int) Math.round(value);
        Component duration = seconds >= 60 && seconds % 60 == 0
                ? Component.translatable("gui.odysseymap.notify.minutes", seconds / 60)
                : Component.translatable("gui.odysseymap.notify.seconds", seconds);
        return Component.translatable("gui.odysseymap.notify.duration", duration).getString();
    }

    private void advanceToMessage() {
        if (!isSessionValid()) return;
        if (minecraft != null) minecraft.setScreen(new LocationMessageScreen(this));
    }

    boolean shareWithMessage(String message) {
        if (minecraft == null || selectionWidget == null
                || !LocationSharing.canShare(minecraft) || !currentDimensionMatches()) return false;
        List<UUID> recipients = selectionWidget.getSelectedOnlineIds();
        LocationNotice notice = previewNotice(message);
        if (notice == null) return false;
        notice = notice.withId(UUID.randomUUID());
        if (LocationSharing.share(minecraft, notice, recipients) > 0) {
            returnToMap();
            return true;
        }
        feedback = Component.translatable("odysseymap.notify.failed");
        return false;
    }

    LocationNotice previewNotice(String message) {
        if (minecraft == null || !LocationSharing.canShare(minecraft) || !currentDimensionMatches()) return null;
        ResourceKey<Level> noticeDimension = dimension;
        int noticeX = x, noticeY = y, noticeZ = z;
        var latest = LocationSharing.knownPlayerPosition(minecraft, targetPlayerId);
        if (latest.isPresent()) {
            var player = latest.get();
            var identifier = Identifier.tryParse(player.dimension());
            if (identifier != null) {
                noticeDimension = ResourceKey.create(Registries.DIMENSION, identifier);
                noticeX = player.x();
                noticeY = player.y();
                noticeZ = player.z();
            }
        }
        try {
            return new LocationNotice(new UUID(0, 1), DimensionUtil.dimensionId(noticeDimension),
                    noticeX, noticeY, noticeZ, durationSeconds, followPlayer ? targetPlayerId : null, message);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private boolean currentDimensionMatches() {
        return sessionMatches() && minecraft.level.dimension().equals(dimension);
    }

    private void updateSendButton() {
        if (sendButton != null) {
            sendButton.active = selectionWidget != null && minecraft != null
                    && LocationSharing.canShare(minecraft) && currentDimensionMatches();
        }
    }

    private boolean sessionMatches() {
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && sessionConnection != null && minecraft.getConnection() == sessionConnection
                && minecraft.player.getUUID().equals(sessionPlayerId);
    }

    boolean isSessionValid() { return currentDimensionMatches(); }

    void cancelToMap() { returnToMap(); }

    ResourceKey<Level> noticeDimension() { return dimension; }
    int noticeX() { return x; }
    int noticeY() { return y; }
    int noticeZ() { return z; }

    private void closeInvalidSession() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!sessionMatches()) closeInvalidSession();
    }

    private void returnToMap() {
        if (minecraft != null) minecraft.setScreen(sessionMatches() ? parent : null);
    }

    @Override
    public void onClose() {
        returnToMap();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            returnToMap();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (selectionWidget != null) selectionWidget.refreshOnlinePlayers();
        updateSendButton();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        if (selectionWidget != null) {
            graphics.centeredText(font,
                    Component.translatable("gui.odysseymap.notify.count", selectionWidget.getSelectedCount(), selectionWidget.getOnlineCount()),
                    width / 2, listY + listHeight + 3, 0xFFDDDDDD);
        }
        if (feedback != null) graphics.centeredText(font, feedback, width / 2, listY + listHeight + 17, 0xFFFF7777);
        else if (minecraft != null && !LocationSharing.canShare(minecraft)) {
            graphics.centeredText(font, Component.translatable("gui.odysseymap.notify.unavailable"),
                    width / 2, listY + listHeight + 17, 0xFFFF7777);
        }
        drawHelp(graphics, listX, height - 64, listWidth);
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0000000);
        graphics.fill(listX - 4, 6, listX + listWidth + 4, height - 6, 0xD0121219);
        graphics.outline(listX - 4, 6, listWidth + 8, height - 12, 0xFF505060);
    }

    private void drawHelp(GuiGraphicsExtractor graphics, int x, int y, int width) {
        List<FormattedCharSequence> lines = font.split(Component.translatable("gui.odysseymap.notify.help"), width);
        for (int i = 0; i < lines.size() && i < 3; i++) graphics.text(font, lines.get(i), x, y + i * 10, 0xFFAAAAAA, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
