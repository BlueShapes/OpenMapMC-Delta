// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.compat.mapselection.MapModSelectionEditor;
import net.aosankaku.openmapdelta.compat.mapselection.MapModSelectionModel;
import net.aosankaku.openmapdelta.compat.mapselection.MapModSelectionState;
import net.aosankaku.openmapdelta.gui.MapModSelectionScreen;
import net.aosankaku.openmapdelta.platform.Services;
import net.aosankaku.openmapdelta.platform.DataPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only, once per acknowledged installed map-mod configuration. */
public final class MapModSelectionController {
    private static Map<String, String> installed = Map.of();
    private static boolean pending;
    private MapModSelectionController() {}

    public static void init() {
        DataPaths.ensureMigrated();
        Map<String, String> versions = new LinkedHashMap<>();
        for (String id : new String[]{MapModSelectionModel.ODYSSEY_MAP, "journeymap", "xaerominimap"}) {
            String runtimeId = MapModSelectionModel.ODYSSEY_MAP.equals(id) ? OpenMapDeltaCommon.MOD_ID : id;
            if (Services.PLATFORM.isModLoaded(runtimeId)) versions.put(id, Services.PLATFORM.getModVersion(runtimeId));
        }
        installed = Map.copyOf(versions);
        MapModSelectionState state = null;
        try {
            state = MapModSelectionState.read(Services.PLATFORM.getConfigDir());
        } catch (IOException e) {
            LoggerFactory.getLogger("OpenMapDelta").warn("Cannot read map-mod selection state: {}", e.getClass().getSimpleName());
        }
        pending = MapModSelectionModel.requiresPrompt(state, installed);
    }

    public static Map<String, String> installedMods() { return installed; }

    public static void tick(Minecraft mc) {
        Screen current = mc.screen;
        if (pending && current instanceof TitleScreen) {
            pending = false;
            mc.setScreen(new MapModSelectionScreen(current));
        }
    }

    public static void choose(String selectedId) throws IOException {
        DataPaths.ensureMigrated();
        MapModSelectionEditor.apply(Services.PLATFORM.getConfigDir(), selectedId, installed);
        if ("odysseymap".equals(selectedId)) ClientEvents.showMinimap();
    }

    public static void keepCurrent() throws IOException {
        DataPaths.ensureMigrated();
        MapModSelectionEditor.acknowledge(Services.PLATFORM.getConfigDir(), installed);
    }

    public static String name(String id) {
        return switch (id) {
            case "journeymap" -> "JourneyMap";
            case "xaerominimap" -> "Xaero's Minimap";
            default -> "OpenMap Delta";
        };
    }
}
