// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.marker.WaypointImportManager;
import net.aosankaku.openmapdelta.marker.WaypointImportReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.nio.file.Path;
import java.util.Locale;

/** Source selection and explicit waypoint import UI. */
public final class WaypointImportScreen extends Screen {
    private final Screen parent;
    private final WaypointImportManager manager = WaypointImportManager.get();
    private final long sessionId;
    private int selectedSource;
    private boolean importing;
    private Component resultMessage = Component.empty();
    private Button previousButton;
    private Button nextButton;
    private Button rescanButton;
    private Button importButton;
    private int tooltipSourceIndex = Integer.MIN_VALUE;
    private String tooltipSourcePath;
    private Tooltip sourceTooltip;

    public WaypointImportScreen(Screen parent) {
        super(Component.translatable("screen.odysseymap.import.title"));
        this.parent = parent;
        this.sessionId = manager.sessionId(Minecraft.getInstance());
    }

    @Override
    protected void init() {
        tooltipSourceIndex = Integer.MIN_VALUE;
        tooltipSourcePath = null;
        sourceTooltip = null;
        Minecraft mc = Minecraft.getInstance();
        if (manager.isCurrentSession(mc, sessionId)) manager.ensureScan(mc);

        int centerX = width / 2;
        int controlWidth = Math.min(120, Math.max(70, (width - 30) / 2));
        int controlY = sourceControlsY();
        previousButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.import.previous"), b -> cycle(-1))
                .bounds(centerX - controlWidth - 4, controlY, controlWidth, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.import.next"), b -> cycle(1))
                .bounds(centerX + 4, controlY, controlWidth, 20).build());

        int footerWidth = Math.min(100, Math.max(70, (width - 20) / 3));
        int footerGap = 5;
        int footerLeft = centerX - (footerWidth * 3 + footerGap * 2) / 2;
        int footerY = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.import.back"), b -> onClose())
                .bounds(footerLeft, footerY, footerWidth, 20).build());
        rescanButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.import.rescan"), b -> rescan())
                .bounds(footerLeft + footerWidth + footerGap, footerY, footerWidth, 20).build());
        importButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.import.action"), b -> importSelected())
                .bounds(footerLeft + (footerWidth + footerGap) * 2, footerY, footerWidth, 20).build());
        updateButtons(mc);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!manager.isCurrentSession(mc, sessionId)) {
            mc.setScreen(null);
            return;
        }
        manager.tick(mc);
        updateButtons(mc);
    }

    private void cycle(int delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!manager.isCurrentSession(mc, sessionId) || !manager.isReady()) return;
        List<WaypointImportReader.Source> sources = manager.getSources();
        if (sources.isEmpty()) return;
        selectedSource = Math.floorMod(selectedSource + delta, sources.size());
        resultMessage = Component.empty();
        updateButtons(mc);
    }

    private void rescan() {
        Minecraft mc = Minecraft.getInstance();
        if (!manager.isCurrentSession(mc, sessionId) || manager.isScanning()) return;
        selectedSource = 0;
        resultMessage = Component.empty();
        manager.rescan(mc);
        updateButtons(mc);
    }

    private void updateButtons(Minecraft mc) {
        boolean current = manager.isCurrentSession(mc, sessionId);
        List<WaypointImportReader.Source> sources = manager.getSources();
        boolean ready = current && manager.isReady();
        if (ready && selectedSource >= sources.size()) selectedSource = Math.max(0, sources.size() - 1);
        updateSourceTooltip(current && ready, sources);
        if (previousButton != null) previousButton.active = ready && sources.size() > 1;
        if (nextButton != null) nextButton.active = ready && sources.size() > 1;
        if (rescanButton != null) rescanButton.active = current && !manager.isScanning();
        if (importButton != null) {
            importButton.active = ready && !importing && selectedSource >= 0 && selectedSource < sources.size()
                    && manager.countValid(sources.get(selectedSource)) > 0;
        }
    }

    private void updateSourceTooltip(boolean ready, List<WaypointImportReader.Source> sources) {
        int sourceIndex = ready && selectedSource >= 0 && selectedSource < sources.size() ? selectedSource : -1;
        String path = sourceIndex >= 0
                ? sources.get(sourceIndex).directory().toAbsolutePath().normalize().toString()
                : null;
        if (sourceIndex == tooltipSourceIndex && java.util.Objects.equals(path, tooltipSourcePath)) return;

        tooltipSourceIndex = sourceIndex;
        tooltipSourcePath = path;
        sourceTooltip = path == null ? null : Tooltip.create(Component.literal(path));
        if (previousButton != null) previousButton.setTooltip(sourceTooltip);
        if (nextButton != null) nextButton.setTooltip(sourceTooltip);
    }

    private void importSelected() {
        Minecraft mc = Minecraft.getInstance();
        if (importing || !manager.isCurrentSession(mc, sessionId) || !manager.isReady()) return;
        List<WaypointImportReader.Source> sources = manager.getSources();
        if (selectedSource < 0 || selectedSource >= sources.size()) return;
        importing = true;
        updateButtons(mc);
        try {
            WaypointImportManager.ImportResult result = manager.importSource(sources.get(selectedSource), sessionId);
            if (!result.currentSession()) {
                resultMessage = Component.translatable("gui.odysseymap.import.world_changed");
            } else if (!result.saved()) {
                resultMessage = Component.translatable("gui.odysseymap.import.save_failed");
            } else if (result.added() == 0) {
                resultMessage = Component.translatable("gui.odysseymap.import.none",
                        result.duplicates(), result.skipped());
            } else {
                resultMessage = Component.translatable("gui.odysseymap.import.result",
                        result.added(), result.duplicates(), result.skipped());
            }
        } finally {
            importing = false;
            updateButtons(mc);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        boolean current = manager.isCurrentSession(mc, sessionId);
        int centerX = width / 2;
        int left = Math.max(12, centerX - 170);
        int textWidth = Math.max(1, Math.min(width - 24, 340));
        int titleY = height >= 240 ? 12 : 4;
        int helpY = titleY + 18;
        if (height >= 180) {
            drawLimited(graphics, Component.translatable("gui.odysseymap.import.help"), left, helpY,
                    textWidth, 10, 3, 0xFFCCCCCC);
        }
        graphics.drawCenteredString(font, title, centerX, titleY, 0xFFFFFFFF);

        int indexY = sourceIndexY();
        int nameY = sourceNameY();
        int controlY = sourceControlsY();
        int countY = countsY();
        int resultY = resultY();

        if (!current) {
            graphics.drawCenteredString(font, Component.translatable("gui.odysseymap.import.world_changed"),
                    centerX, countY, 0xFFFF7777);
        } else if (manager.isScanning()) {
            graphics.drawCenteredString(font, Component.translatable("gui.odysseymap.import.scanning"),
                    centerX, countY, 0xFFFFFFFF);
        } else if (manager.getScanError() != null) {
            graphics.drawCenteredString(font, Component.translatable("gui.odysseymap.import.scan_failed"),
                    centerX, countY, 0xFFFF7777);
        } else if (!manager.isReady() || manager.getSources().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.odysseymap.import.no_sources"),
                    centerX, countY, 0xFFFFFFFF);
        } else {
            List<WaypointImportReader.Source> sources = manager.getSources();
            WaypointImportReader.Source source = sources.get(selectedSource);
            graphics.drawCenteredString(font, Component.translatable("gui.odysseymap.import.source_position",
                    selectedSource + 1, sources.size()), centerX, indexY, 0xFFFFFFFF);
            String name = sourceName(source, textWidth);
            graphics.drawString(font, name, left, nameY, 0xFFAAAAAA, false);
            Component counts = Component.translatable("gui.odysseymap.import.counts",
                    manager.countValid(source), manager.countSkipped(source));
            drawLimited(graphics, counts, left, countY, textWidth, 11, height < 180 ? 1 : 2, 0xFFFFFFFF);
        }

        Component visibleResult = current ? resultMessage : Component.empty();
        if (height >= 115 && !visibleResult.getString().isEmpty()) {
            drawLimited(graphics, visibleResult, left, resultY, textWidth, 11, height < 210 ? 1 : 2, 0xFFFFFFFF);
        }
    }

    private int sourceControlsY() {
        if (height >= 240) return 118;
        if (height >= 180) return 88;
        return height < 150 ? 40 : 56;
    }

    private int sourceIndexY() {
        if (height >= 240) return 80;
        if (height >= 180) return 58;
        return height < 150 ? 16 : 28;
    }

    private int sourceNameY() {
        if (height >= 240) return 94;
        if (height >= 180) return 72;
        return height < 150 ? 27 : 42;
    }

    private int countsY() {
        if (height >= 240) return 144;
        if (height >= 180) return 112;
        return height < 150 ? 62 : 79;
    }

    private int resultY() {
        if (height >= 240) return 171;
        if (height >= 180) return 135;
        return height < 150 ? 74 : 99;
    }

    private String sourceName(WaypointImportReader.Source source, int maxWidth) {
        String directory = source.directory().getFileName() == null
                ? source.directory().toString() : source.directory().getFileName().toString();
        Path gameDirectory = Path.of(Minecraft.getInstance().gameDirectory.toString()).toAbsolutePath().normalize();
        Path sourceDirectory = source.directory().toAbsolutePath().normalize();
        String relative = sourceDirectory.startsWith(gameDirectory)
                ? gameDirectory.relativize(sourceDirectory).toString().replace('\\', '/')
                : sourceDirectory.toString().replace('\\', '/');
        String normalized = relative.toLowerCase(Locale.ROOT);
        String qualifier;
        if (source.modName().equals("JourneyMap") && normalized.startsWith("journeymap/data/sp/")) {
            qualifier = Component.translatable("gui.odysseymap.import.source.journeymap_sp").getString();
        } else if (source.modName().equals("JourneyMap") && normalized.startsWith("journeymap/data/mp/")) {
            qualifier = Component.translatable("gui.odysseymap.import.source.journeymap_mp").getString();
        } else if (normalized.startsWith("xaero/minimap/")) {
            qualifier = Component.translatable("gui.odysseymap.import.source.xaero_minimap").getString();
        } else if (normalized.startsWith("xaerowaypoints/")) {
            qualifier = Component.translatable("gui.odysseymap.import.source.xaero_legacy").getString();
        } else if (normalized.startsWith("config/xaerowaypoints/")) {
            qualifier = Component.translatable("gui.odysseymap.import.source.xaero_config").getString();
        } else if (normalized.startsWith("mods/xaerowaypoints/")) {
            qualifier = Component.translatable("gui.odysseymap.import.source.xaero_mods").getString();
        } else {
            qualifier = source.modName();
        }
        String prefix = qualifier + " / ";
        while (font.width(prefix + directory) > maxWidth && directory.length() > 1) {
            directory = directory.substring(1);
        }
        String value = prefix + directory;
        while (font.width(value) > maxWidth && value.length() > 1) value = value.substring(0, value.length() - 1);
        return value;
    }

    private void drawLimited(GuiGraphics graphics, Component component, int x, int y, int maxWidth,
                             int lineHeight, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(component, maxWidth);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(manager.isCurrentSession(mc, sessionId) ? parent : null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
