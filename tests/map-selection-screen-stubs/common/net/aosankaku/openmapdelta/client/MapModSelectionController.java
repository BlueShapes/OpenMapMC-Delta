package net.aosankaku.openmapdelta.client;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MapModSelectionController {
    public static boolean failChoose;
    public static boolean failKeep;

    private MapModSelectionController() {}

    public static Map<String, String> installedMods() {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("odysseymap", "1.3.0");
        mods.put("journeymap", "6.0.7");
        return mods;
    }

    public static String name(String id) {
        return switch (id) {
            case "odysseymap" -> "OpenMap Delta";
            case "journeymap" -> "JourneyMap";
            default -> id;
        };
    }

    public static void choose(String id) throws IOException {
        if (failChoose) throw new IOException("choice write failed");
    }

    public static void keepCurrent() throws IOException {
        if (failKeep) throw new IOException("acknowledgement write failed");
    }

    public static void reset() {
        failChoose = false;
        failKeep = false;
    }
}
