// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.MapModSelectionController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/** Explicit startup choice; configuration edits leave installed JARs intact. */
public final class MapModSelectionScreen extends Screen {
    private final Screen parent;
    private List<FormattedCharSequence> explanation = List.of();
    private Component failure;
    private boolean allowUnsavedClose;
    private Button keepButton;
    private int panelLeft, panelTop, panelWidth, panelHeight;

    public MapModSelectionScreen(Screen parent) {
        super(Component.translatable("screen.odysseymap.map_mod_selection"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(420, Math.max(1, width - 24));
        explanation = font.split(Component.translatable(parent instanceof TitleScreen ? "gui.odysseymap.map_mod_selection.description" : "gui.odysseymap.map_mod_selection.instructions"), Math.max(1, panelWidth - 20));
        int count = MapModSelectionController.installedMods().size();
        panelHeight = 66 + explanation.size() * 10 + (count + 1) * 24;
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(4, (height - panelHeight) / 2);
        int y = panelTop + 34 + explanation.size() * 10;
        addRenderableOnly(this::renderPanel);
        for (String id : new String[]{"odysseymap", "journeymap", "xaerominimap"}) {
            if (!MapModSelectionController.installedMods().containsKey(id)) continue;
            addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.map_mod_selection.choose", MapModSelectionController.name(id)), b -> choose(id))
                    .bounds(panelLeft + 10, y, Math.max(1, panelWidth - 20), 20).build());
            y += 24;
        }
        keepButton = addRenderableWidget(Button.builder(keepButtonLabel(), b -> onClose())
                .bounds(panelLeft + 10, y, Math.max(1, panelWidth - 20), 20).build());
    }

    private void choose(String id) {
        try {
            MapModSelectionController.choose(id);
            returnToParent();
        } catch (IOException e) {
            failed(e);
        }
    }

    private void failed(IOException e) {
        failure = Component.translatable("gui.odysseymap.map_mod_selection.failed");
        LoggerFactory.getLogger("OpenMapDelta").warn("Cannot update map-mod configurations", e);
    }

    private void returnToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private Component keepButtonLabel() {
        return Component.translatable(allowUnsavedClose
                ? "gui.odysseymap.map_mod_selection.continue_unsaved"
                : "gui.odysseymap.map_mod_selection.keep");
    }

    @Override
    public void onClose() {
        if (allowUnsavedClose) {
            returnToParent();
            return;
        }
        try {
            MapModSelectionController.keepCurrent();
            returnToParent();
        } catch (IOException e) {
            failed(e);
            // An unwritable acknowledgement file must not block the title menu.
            allowUnsavedClose = true;
            if (keepButton != null) keepButton.setMessage(keepButtonLabel());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawFailure(graphics);
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF0121219);
        graphics.outline(panelLeft, panelTop, panelWidth, panelHeight, 0xFF505060);
        graphics.centeredText(font, title, width / 2, panelTop + 12, 0xFFFFFFFF);
        for (int i = 0; i < explanation.size(); i++) {
            graphics.text(font, explanation.get(i), panelLeft + 10, panelTop + 30 + i * 10, 0xFFDDDDDD, false);
        }
    }

    private void drawFailure(GuiGraphicsExtractor graphics) {
        if (failure != null) {
            List<FormattedCharSequence> lines = font.split(failure, Math.max(1, panelWidth - 20));
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                graphics.text(font, lines.get(i), panelLeft + 10, panelTop + panelHeight - 32 + i * 10, 0xFFFF7777, false);
            }
        }
    }
}
