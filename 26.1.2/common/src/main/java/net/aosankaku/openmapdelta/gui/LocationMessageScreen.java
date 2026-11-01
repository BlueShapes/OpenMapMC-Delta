// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.aosankaku.openmapdelta.client.LocationNotice;
import net.aosankaku.openmapdelta.client.LocationSharing;
import net.aosankaku.openmapdelta.network.LocationContextResponse;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Message editor shown after choosing the recipients for a location notice. */
public final class LocationMessageScreen extends Screen {
    private record Template(String label, String message) {}

    private final PlayerSelectScreen recipients;
    private final List<Template> templates = new ArrayList<>();
    private Object sessionConnection;
    private Object contextConnection;
    private UUID sessionPlayerId;
    private UUID contextRequestId;
    private String pendingMessage = "";
    private String lookedAtBlock = "";
    private EditBox messageBox;
    private Button sendButton;
    private Component feedback;
    private Component contextStatus;
    private int panelLeft, panelTop, panelWidth;
    private int listTop, listHeight, scrollOffset;

    public LocationMessageScreen(PlayerSelectScreen recipients) {
        super(Component.translatable("screen.odysseymap.notify.message"));
        this.recipients = recipients;
    }

    @Override
    protected void init() {
        if (sessionConnection == null && minecraft != null && minecraft.player != null) {
            sessionConnection = minecraft.getConnection();
            contextConnection = minecraft.getConnection().getConnection();
            sessionPlayerId = minecraft.player.getUUID();
        }
        if (!sessionMatches()) {
            closeInvalidSession();
            return;
        }
        panelWidth = Math.min(560, Math.max(1, width - 24));
        panelLeft = (width - panelWidth) / 2;
        panelTop = 8;
        addRenderableOnly(this::renderPanel);
        if (lookedAtBlock.isEmpty()) lookedAtBlock = lookedAtBlockName();
        messageBox = new EditBox(font, panelLeft + 10, panelTop + 39, panelWidth - 20, 20,
                Component.translatable("gui.odysseymap.notify.message.hint"));
        messageBox.setMaxLength(4_096);
        messageBox.setHint(Component.translatable("gui.odysseymap.notify.message.hint"));
        messageBox.setValue(pendingMessage);
        messageBox.setResponder(value -> {
            pendingMessage = value;
            feedback = null;
            updateSendButton();
        });
        addRenderableWidget(messageBox);
        listTop = panelTop + 99;
        listHeight = Math.max(18, height - listTop - 37);
        requestContextOnce();
        refreshTemplates();
        int buttonWidth = Math.max(1, (panelWidth - 28) / 3);
        int buttonY = height - 27;
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.notify.message.back"), b -> back())
                .bounds(panelLeft + 10, buttonY, buttonWidth, 20).build());
        sendButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.notify.message.send"), b -> send())
                .bounds(panelLeft + 14 + buttonWidth, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.cancel"), b -> cancel())
                .bounds(panelLeft + 18 + buttonWidth * 2, buttonY, buttonWidth, 20).build());
        setInitialFocus(messageBox);
        updateSendButton();
    }

    private void requestContextOnce() {
        if (contextRequestId != null) return;
        String dimension = DimensionUtil.dimensionId(recipients.noticeDimension());
        contextRequestId = LocationContextClient.request(contextConnection, dimension,
                recipients.noticeX(), recipients.noticeY(), recipients.noticeZ());
    }

    private String lookedAtBlockName() {
        if (minecraft == null || minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return "";
        return minecraft.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString();
    }

    private String loadedBiomeId() {
        if (minecraft == null || minecraft.level == null
                || !minecraft.level.dimension().equals(recipients.noticeDimension())) return "";
        if (minecraft.level.getChunkSource().getChunk(recipients.noticeX() >> 4,
                recipients.noticeZ() >> 4, ChunkStatus.FULL, false) == null) return "";
        var position = new BlockPos(recipients.noticeX(), recipients.noticeY(), recipients.noticeZ());
        var key = minecraft.level.getBiome(position).unwrapKey().orElse(null);
        return key == null ? "" : key.identifier().toString();
    }

    private void refreshTemplates() {
        List<Template> refreshed = new ArrayList<>();
        addFixed(refreshed, "gather");
        addFixed(refreshed, "come_here");
        addFixed(refreshed, "base_site");
        addFixed(refreshed, "cave");
        addFixed(refreshed, "building");
        addFixed(refreshed, "trade");
        if (!lookedAtBlock.isBlank()) addFound(refreshed, "block", lookedAtBlock);

        LocationContextResponse response = LocationContextClient.response(contextRequestId);
        String biomeId = response != null && response.available() && !response.biomeId().isEmpty()
                ? response.biomeId() : loadedBiomeId();
        if (!biomeId.isEmpty()) addFound(refreshed, "biome", localizedName("biome", biomeId));
        contextStatus = null;
        if (contextRequestId == null || response == null || !response.available()) {
            contextStatus = Component.translatable(contextRequestId != null && response == null
                    ? "gui.odysseymap.notify.context.loading" : "gui.odysseymap.notify.context.unavailable");
        } else {
            for (String structure : response.structureIds()) {
                addFound(refreshed, "structure", localizedName("structure", structure));
            }
            if (!response.complete()) {
                contextStatus = Component.translatable("gui.odysseymap.notify.context.incomplete");
            } else if (response.structureIds().isEmpty()) {
                contextStatus = Component.translatable("gui.odysseymap.notify.context.no_structures");
            }
        }
        if (!templates.equals(refreshed)) {
            templates.clear();
            templates.addAll(refreshed);
            scrollOffset = Math.min(scrollOffset, Math.max(0, templates.size() - visibleRows()));
        }
    }

    private void addFixed(List<Template> values, String id) {
        String key = "gui.odysseymap.notify.template." + id;
        String text = Component.translatable(key).getString();
        values.add(new Template(text, text));
    }

    private void addFound(List<Template> values, String kind, String name) {
        String message = Component.translatable("gui.odysseymap.notify.template.found", name).getString();
        String label = Component.translatable("gui.odysseymap.notify.template." + kind, name).getString();
        values.add(new Template(label, message));
    }

    private String localizedName(String kind, String id) {
        String translationKey = kind + "." + id.replace(':', '.').replace('/', '.');
        String translated = Component.translatable(translationKey).getString();
        if (!translated.equals(translationKey)) return translated;
        int separator = id.indexOf(':');
        String namespace = separator >= 0 ? id.substring(0, separator) : "unknown";
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        String[] words = path.replace('/', '_').split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return namespace.equals("minecraft") ? name.toString() : namespace + ":" + name;
    }

    private int visibleRows() { return Math.max(1, listHeight / 18); }

    private boolean sessionMatches() {
        return recipients.isSessionValid() && minecraft != null && minecraft.player != null
                && minecraft.getConnection() == sessionConnection
                && minecraft.getConnection().getConnection() == contextConnection
                && minecraft.player.getUUID().equals(sessionPlayerId);
    }

    private boolean wireMessageValid() {
        LocationNotice probe = recipients.previewNotice(pendingMessage);
        return probe != null && LocationNotice.decode(probe.encode()).filter(probe::equals).isPresent();
    }

    private int remainingCharacters() {
        LocationNotice empty = recipients.previewNotice("");
        if (empty == null) return 0;
        return LocationNotice.MAX_MESSAGE_LENGTH - empty.encode().length()
                - (pendingMessage.isEmpty() ? 0 : 3) - pendingMessage.length();
    }

    private void updateSendButton() {
        if (sendButton != null) sendButton.active = sessionMatches() && LocationSharing.canShare(minecraft)
                && !pendingMessage.isBlank() && wireMessageValid();
    }

    private void chooseTemplate(int index) {
        if (index < 0 || index >= templates.size() || messageBox == null) return;
        messageBox.setValue(templates.get(index).message());
        messageBox.setFocused(true);
    }

    private void send() {
        if (!sessionMatches()) {
            closeInvalidSession();
        } else if (sendButton != null && sendButton.active && recipients.shareWithMessage(pendingMessage)) {
            return;
        } else {
            feedback = Component.translatable("odysseymap.notify.failed");
        }
    }

    private void back() {
        if (minecraft != null) minecraft.setScreen(sessionMatches() ? recipients : null);
    }

    private void cancel() {
        if (sessionMatches()) recipients.cancelToMap();
        else closeInvalidSession();
    }

    private void closeInvalidSession() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!sessionMatches()) closeInvalidSession();
        else {
            refreshTemplates();
            updateSendButton();
        }
    }

    @Override
    public void onClose() { back(); }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { back(); return true; }
        if ((event.key() == 257 || event.key() == 335) && sendButton != null && sendButton.active) { send(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        if (event.buttonInfo().button() == 0 && mouseX >= panelLeft + 8 && mouseX < panelLeft + panelWidth - 8
                && mouseY >= listTop && mouseY < listTop + listHeight) {
            int index = (int) ((mouseY - listTop) / 18) + scrollOffset;
            if (index < templates.size()) chooseTemplate(index);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft + 8 && mouseX < panelLeft + panelWidth - 8
                && mouseY >= listTop && mouseY < listTop + listHeight && scrollY != 0) {
            scrollOffset = Math.max(0, Math.min(templates.size() - visibleRows(), scrollOffset + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        refreshTemplates();
        updateSendButton();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, Component.translatable("gui.odysseymap.notify.message.remaining",
                remainingCharacters(), LocationNotice.MAX_MESSAGE_LENGTH), panelLeft + 10, panelTop + 62,
                remainingCharacters() < 0 ? 0xFFFF7777 : 0xFFAAAAAA, false);
        graphics.text(font, Component.translatable("gui.odysseymap.notify.message.templates"),
                panelLeft + 10, panelTop + 78, 0xFFFFFFFF, false);
        graphics.enableScissor(panelLeft + 8, listTop, panelLeft + panelWidth - 8, listTop + listHeight);
        try {
            for (int row = 0; row < visibleRows() && row + scrollOffset < templates.size(); row++) {
                int index = row + scrollOffset;
                int y = listTop + row * 18;
                boolean hover = mouseX >= panelLeft + 8 && mouseX < panelLeft + panelWidth - 8
                        && mouseY >= y && mouseY < y + 18;
                graphics.fill(panelLeft + 8, y, panelLeft + panelWidth - 8, y + 17,
                        hover ? 0xFF35495B : 0xFF24252A);
                String label = font.plainSubstrByWidth(templates.get(index).label(), panelWidth - 28);
                graphics.text(font, label, panelLeft + 13, y + 5, 0xFFFFFFFF, false);
            }
        } finally {
            graphics.disableScissor();
        }
        if (contextStatus != null) graphics.text(font, contextStatus, panelLeft + 10, panelTop + 87, 0xFFAAAAAA, false);
        if (feedback != null) graphics.centeredText(font, feedback, width / 2, height - 40, 0xFFFF7777);
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, height - 6, 0xF0121219);
        graphics.outline(panelLeft, panelTop, panelWidth, height - panelTop - 6, 0xFF505060);
        graphics.centeredText(font, title, width / 2, panelTop + 8, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("gui.odysseymap.notify.message.custom"),
                panelLeft + 10, panelTop + 27, 0xFFCCCCCC, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
