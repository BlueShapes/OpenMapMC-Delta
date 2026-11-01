// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.LocationSharing;
import net.aosankaku.openmapdelta.client.PlayerCommandPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Vanilla whisper editor bound to one UUID and one connection session. */
public final class PlayerMessageScreen extends Screen {
    private final Screen parent;
    private final UUID targetPlayerId;
    private Object sessionConnection;
    private UUID sessionPlayerId;
    private EditBox messageBox;
    private Button sendButton;
    private String pendingMessage = "";
    private Component feedback;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;

    public PlayerMessageScreen(Screen parent, UUID targetPlayerId) {
        super(messageTitle(targetPlayerId));
        this.parent = parent;
        this.targetPlayerId = Objects.requireNonNull(targetPlayerId, "targetPlayerId");
    }

    private static Component messageTitle(UUID targetPlayerId) {
        String name = LocationSharing.onlinePlayerName(Minecraft.getInstance(), targetPlayerId);
        return Component.translatable("screen.odysseymap.message", name == null ? "?" : name);
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
        panelWidth = Math.min(360, Math.max(1, width - 24));
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(6, (height - 140) / 2);
        addRenderableOnly(this::renderPanel);
        messageBox = new EditBox(font, panelLeft + 10, panelTop + 38, Math.max(1, panelWidth - 20), 20,
                Component.translatable("gui.odysseymap.message.hint"));
        messageBox.setMaxLength(PlayerCommandPayload.MAX_PRIVATE_MESSAGE_LENGTH);
        messageBox.setHint(Component.translatable("gui.odysseymap.message.hint"));
        messageBox.setValue(pendingMessage);
        messageBox.setResponder(value -> {
            pendingMessage = value;
            feedback = null;
            updateSendButton();
        });
        addRenderableWidget(messageBox);
        int buttonWidth = Math.max(1, (panelWidth - 26) / 2);
        sendButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.message.send"), b -> send())
                .bounds(panelLeft + 10, panelTop + 110, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.cancel"), b -> returnToParent())
                .bounds(panelLeft + 16 + buttonWidth, panelTop + 110, buttonWidth, 20).build());
        setInitialFocus(messageBox);
        updateSendButton();
    }

    private boolean sessionMatches() {
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && sessionConnection != null && minecraft.getConnection() == sessionConnection
                && minecraft.player.getUUID().equals(sessionPlayerId);
    }

    private void updateSendButton() {
        if (sendButton != null) {
            sendButton.active = sessionMatches() && LocationSharing.canMessage(minecraft)
                    && LocationSharing.onlinePlayerName(minecraft, targetPlayerId) != null
                    && PlayerCommandPayload.validMessage(pendingMessage);
        }
    }

    private void send() {
        if (!sessionMatches()) {
            closeInvalidSession();
        } else if (LocationSharing.sendPrivateMessage(minecraft, targetPlayerId, pendingMessage)) {
            returnToParent();
        } else {
            feedback = Component.translatable("gui.odysseymap.message.failed");
            updateSendButton();
        }
    }

    private void closeInvalidSession() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    private void returnToParent() {
        if (minecraft != null) minecraft.setScreen(sessionMatches() ? parent : null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!sessionMatches()) closeInvalidSession();
        else updateSendButton();
    }

    @Override
    public void onClose() { returnToParent(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            returnToParent();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && sendButton != null && sendButton.active) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        Component failure = feedback;
        if (failure == null && sessionMatches() && (!LocationSharing.canMessage(minecraft)
                || LocationSharing.onlinePlayerName(minecraft, targetPlayerId) == null)) {
            failure = Component.translatable("gui.odysseymap.message.failed");
        }
        if (failure != null) {
            List<FormattedCharSequence> lines = font.split(failure, Math.max(1, panelWidth - 20));
            for (int i = 0; i < lines.size() && i < 3; i++) {
                graphics.drawString(font, lines.get(i), panelLeft + 10, panelTop + 66 + i * 10, 0xFFFF7777, false);
            }
        }
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 140, 0xF0121219);
        graphics.renderOutline(panelLeft, panelTop, panelWidth, 140, 0xFF505060);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
