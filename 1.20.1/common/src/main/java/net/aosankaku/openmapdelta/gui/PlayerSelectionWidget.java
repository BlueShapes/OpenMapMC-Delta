// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;

public class PlayerSelectionWidget extends AbstractWidget {
    private static final int ROW_HEIGHT = 28;
    private final Set<UUID> selectedIds;
    private final List<PlayerRow> onlinePlayers = new ArrayList<>();
    private final List<PlayerRow> filteredPlayers = new ArrayList<>();
    private String filter = "";
    private int scrollOffset;
    private long nextOnlineRefreshMillis;

    public PlayerSelectionWidget(int x, int y, int width, int height, Set<UUID> selectedIds) {
        super(x, y, width, height, Component.translatable("gui.odysseymap.notify.count", selectedIds.size(), 0));
        this.selectedIds = selectedIds;
    }

    public void refreshOnlinePlayers() {
        long now = System.currentTimeMillis();
        if (now < nextOnlineRefreshMillis) return;
        nextOnlineRefreshMillis = now + 250;
        Minecraft mc = Minecraft.getInstance();
        Map<UUID, Double> loadedDistances = new HashMap<>();
        if (mc.player != null && mc.level != null) {
            for (AbstractClientPlayer player : mc.level.players()) {
                loadedDistances.put(player.getUUID(), player.distanceToSqr(mc.player));
            }
        }
        List<PlayerRow> refreshed = new ArrayList<>();
        if (mc.getConnection() != null) {
            Collection<PlayerInfo> infos = mc.getConnection().getOnlinePlayers();
            for (PlayerInfo info : infos) {
                UUID id = info.getProfile().getId();
                if (mc.player != null && id.equals(mc.player.getUUID())) continue;
                String name = info.getProfile().getName();
                refreshed.add(new PlayerRow(info, id, name == null ? id.toString() : name, loadedDistances.get(id)));
            }
        }
        refreshed.sort((left, right) -> {
            boolean leftKnown = left.distanceSqr != null;
            boolean rightKnown = right.distanceSqr != null;
            if (leftKnown != rightKnown) return leftKnown ? -1 : 1;
            if (leftKnown) {
                int order = Double.compare(left.distanceSqr, right.distanceSqr);
                if (order != 0) return order;
            }
            return left.name.compareToIgnoreCase(right.name);
        });
        onlinePlayers.clear();
        onlinePlayers.addAll(refreshed);
        Set<UUID> onlineIds = new HashSet<>();
        for (PlayerRow player : onlinePlayers) onlineIds.add(player.id);
        selectedIds.retainAll(onlineIds);
        applyFilter();
        setMessage(Component.translatable("gui.odysseymap.notify.count", selectedIds.size(), onlinePlayers.size()));
    }

    public void setFilter(String value) {
        filter = value == null ? "" : value.toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        filteredPlayers.clear();
        for (PlayerRow player : onlinePlayers) {
            if (filter.isEmpty() || player.name.toLowerCase(Locale.ROOT).contains(filter)) filteredPlayers.add(player);
        }
        scrollOffset = Mth.clamp(scrollOffset, 0,
                Math.max(0, filteredPlayers.size() - getHeight() / ROW_HEIGHT));
    }

    private void toggleRow(double mouseY) {
        int row = (int) ((mouseY - getY()) / ROW_HEIGHT);
        int visibleRows = getHeight() / ROW_HEIGHT;
        if (row < 0 || row >= visibleRows) return;
        int index = row + scrollOffset;
        if (index >= 0 && index < filteredPlayers.size()) {
            UUID id = filteredPlayers.get(index).id;
            if (!selectedIds.remove(id)) selectedIds.add(id);
            setMessage(Component.translatable("gui.odysseymap.notify.count", selectedIds.size(), onlinePlayers.size()));
        }
    }

    public void selectAllOnline() {
        for (PlayerRow player : onlinePlayers) selectedIds.add(player.id);
        setMessage(Component.translatable("gui.odysseymap.notify.count", selectedIds.size(), onlinePlayers.size()));
    }

    public void deselectAll() {
        selectedIds.clear();
        setMessage(Component.translatable("gui.odysseymap.notify.count", 0, onlinePlayers.size()));
    }

    public int getSelectedCount() { return selectedIds.size(); }
    public int getOnlineCount() { return onlinePlayers.size(); }

    public List<UUID> getSelectedOnlineIds() {
        List<UUID> recipients = new ArrayList<>();
        for (PlayerRow player : onlinePlayers) if (selectedIds.contains(player.id)) recipients.add(player.id);
        return recipients;
    }

    private Component distanceComponent(PlayerRow player) {
        return player.distanceSqr == null
                ? Component.translatable("gui.odysseymap.notify.unknown_distance")
                : Component.literal(String.format(Locale.ROOT, "%.0fm", Math.sqrt(player.distanceSqr)));
    }

    private String truncatedName(PlayerRow player, int distanceWidth) {
        String value = player.name;
        int available = Math.max(0, width - 78 - distanceWidth);
        while (!value.isEmpty() && Minecraft.getInstance().font.width(value) > available) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xE0181820);
        if (filteredPlayers.isEmpty()) {
            graphics.drawCenteredString(mc.font, Component.translatable("gui.odysseymap.notify.no_players"),
                    getX() + width / 2, getY() + Math.max(4, height / 2 - 4), 0xFFAAAAAA);
            return;
        }
        int visibleRows = Math.max(0, getHeight() / ROW_HEIGHT);
        for (int row = 0; row < visibleRows && row + scrollOffset < filteredPlayers.size(); row++) {
            PlayerRow player = filteredPlayers.get(row + scrollOffset);
            int rowY = getY() + row * ROW_HEIGHT;
            boolean hover = mouseX >= getX() && mouseX < getX() + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean selected = selectedIds.contains(player.id);
            graphics.fill(getX(), rowY, getX() + width, rowY + ROW_HEIGHT,
                    selected ? (hover ? 0xFF385C70 : 0xFF2B4658) : (hover ? 0xFF33343A : 0xFF24252A));
            int boxX = getX() + 4;
            int boxY = rowY + 8;
            graphics.renderOutline(boxX, boxY, 12, 12, selected ? 0xFFB7D7EC : 0xFF777780);
            if (selected) graphics.fill(boxX + 3, boxY + 3, boxX + 9, boxY + 9, 0xFFB7D7EC);
            PlayerFaceRenderer.draw(graphics, player.info.getSkinLocation(), getX() + 22, rowY + 4, 20);
            String displayName = truncatedName(player, mc.font.width(distanceComponent(player)));
            graphics.drawString(mc.font, displayName, getX() + 48, rowY + 9, 0xFFFFFFFF, false);
            Component distance = distanceComponent(player);
            graphics.drawString(mc.font, distance, getX() + width - 6 - mc.font.width(distance), rowY + 9, 0xFFAAAAAA, false);
        }
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || mouseX < getX() || mouseX >= getX() + width
                || mouseY < getY() || mouseY >= getY() + height) return false;
        toggleRow(mouseY);
        setFocused(true);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < getX() || mouseX >= getX() + width || mouseY < getY() || mouseY >= getY() + height) return false;
        scrollOffset = Mth.clamp(scrollOffset + (delta < 0 ? 1 : -1), 0,
                Math.max(0, filteredPlayers.size() - getHeight() / ROW_HEIGHT));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private static final class PlayerRow {
        private final PlayerInfo info;
        private final UUID id;
        private final String name;
        private final Double distanceSqr;

        private PlayerRow(PlayerInfo info, UUID id, String name, Double distanceSqr) {
            this.info = info;
            this.id = id;
            this.name = name;
            this.distanceSqr = distanceSqr;
        }
    }
}
