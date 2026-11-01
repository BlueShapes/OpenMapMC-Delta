// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.compat.players.*;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A website is always configured explicitly for the current server and Minecraft dimension. */
public final class BlueMapSettingsScreen extends Screen {
    private final Screen parent;
    private EditBox url, map, liveRoot;
    private String server, dimension;
    private Object connection, level;
    private Component failure;
    private int left, top, fieldWidth;
    public BlueMapSettingsScreen(Screen parent) { super(Component.translatable("screen.odysseymap.bluemap")); this.parent = parent; }

    @Override protected void init() {
        left = Math.max(4, (width - 310) / 2); fieldWidth = Math.min(310, width - 8); top = Math.max(2, (height - 220) / 2);
        String oldUrl = url == null ? "" : url.getValue(), oldMap = map == null ? "" : map.getValue(), oldRoot = liveRoot == null ? "maps" : liveRoot.getValue();
        boolean usable = minecraft != null && minecraft.level != null && minecraft.getConnection() != null
                && !ExternalPlayerClient.serverKey(minecraft).isBlank();
        if (server == null && usable) {
            server = ExternalPlayerClient.serverKey(minecraft); dimension = DimensionUtil.dimensionId(minecraft.level.dimension());
            connection = minecraft.getConnection().getConnection(); level = minecraft.level;
            try {
                var binding = ExternalPlayerClient.configuration().get(server, dimension).orElse(null);
                if (binding != null) { oldUrl = binding.baseUrl(); oldMap = binding.mapId(); oldRoot = binding.liveDataRoot(); }
            } catch (java.io.IOException invalid) { failure = Component.translatable("gui.odysseymap.remote_players.config_error"); usable = false; }
        }
        url = field("url", top + 64, oldUrl, 2048);
        map = field("map", top + 98, oldMap, 128);
        liveRoot = field("root", top + 132, oldRoot, 256);
        int buttonWidth = Math.max(1, (fieldWidth - 8) / 3);
        Button save = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.remote_players.save"), b -> save(false)).bounds(left, top + 192, buttonWidth, 20).build());
        Button clear = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.remote_players.remove"), b -> save(true)).bounds(left + buttonWidth + 4, top + 192, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + (buttonWidth + 4) * 2, top + 192, buttonWidth, 20).build());
        save.active = usable; clear.active = usable;
        setInitialFocus(url);
    }

    private EditBox field(String key, int y, String value, int limit) {
        EditBox box = new EditBox(font, left, y, fieldWidth, 18, Component.translatable("gui.odysseymap.remote_players." + key));
        box.setMaxLength(limit); box.setValue(value); return addRenderableWidget(box);
    }

    private void save(boolean remove) {
        try {
            if (minecraft == null || minecraft.getConnection() == null || minecraft.getConnection().getConnection() != connection
                    || minecraft.level != level) throw new IllegalArgumentException("Connection changed");
            ExternalPlayerClient.configuration().save(server, dimension, remove ? null : new BlueMapEndpoint(url.getValue(), map.getValue(), liveRoot.getValue()));
            ExternalPlayerClient.settingsChanged(); onClose();
        } catch (java.io.IOException | IllegalArgumentException invalid) { failure = Component.translatable("gui.odysseymap.remote_players.save_failed"); }
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top + 4, 0xFFFFFFFF);
        var lines = font.split(Component.translatable("gui.odysseymap.remote_players.hint"), fieldWidth);
        for (int i = 0; i < Math.min(3, lines.size()); i++) graphics.drawString(font, lines.get(i), left, top + 21 + i * 10, 0xFFDDDDDD, false);
        graphics.drawString(font, Component.translatable("gui.odysseymap.remote_players.url"), left, top + 53, 0xFFDDDDDD, false);
        graphics.drawString(font, Component.translatable("gui.odysseymap.remote_players.map"), left, top + 87, 0xFFDDDDDD, false);
        graphics.drawString(font, Component.translatable("gui.odysseymap.remote_players.root"), left, top + 121, 0xFFDDDDDD, false);
        graphics.drawString(font, font.plainSubstrByWidth(dimension == null ? "" : dimension, fieldWidth), left, top + 156, 0xFFAAAAAA, false);
        graphics.drawString(font, failure == null ? Component.translatable(ExternalPlayerClient.statusKey()) : failure, left, top + 174,
                failure == null ? 0xFFDDDDDD : 0xFFFF7777, false);
    }
}
