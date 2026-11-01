// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.minecraft.client.gui.Draw;
import net.minecraft.client.gui.RecordingSurface;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Exercises the real waypoint editor through each version's screen render entry point. */
public final class WaypointEditScreenRenderTest {
    private static final ResourceKey<Level> DIMENSION = ResourceKey.create("minecraft:overworld");
    private static int assertions;
    private static String version;

    private WaypointEditScreenRenderTest() {}

    public static void main(String[] args) throws Exception {
        version = System.getProperty("waypoint.edit.version");
        if (version == null) throw new IllegalStateException("waypoint.edit.version is required");

        verifyMode("local create", () -> WaypointEditScreen.forCreate(null, DIMENSION, 8, 70, -42, "Default Spawn"));
        verifyMode("shared create", () -> WaypointEditScreen.forSharedCreate(null, DIMENSION, 8, 70, -42, "Shared Spawn"));
        verifyMode("edit", () -> WaypointEditScreen.forEdit(null, marker(MarkerType.WAYPOINT, false)));
        verifyMode("station appearance", () -> WaypointEditScreen.forEdit(null, marker(MarkerType.TRANSIT_STATION, false)));
        if (version.equals("1.20.1") || version.equals("1.21.1")) {
            verifyMode("deathpoint edit", () -> WaypointEditScreen.forEdit(null, marker(MarkerType.DEATH, false)));
            verifyDeathpointReadOnlyFields();
        }

        System.out.println("WaypointEditScreenRenderTest " + version + " passed " + assertions + " assertions.");
    }

    private static Marker marker(MarkerType type, boolean shared) {
        return new Marker(UUID.fromString("00000000-0000-0000-0000-000000000001"), DIMENSION,
                8, 70, -42, "Existing waypoint", 0xFFFFFF55, "", "Travel", type, shared);
    }

    private static void verifyDeathpointReadOnlyFields() {
        WaypointEditScreen screen = WaypointEditScreen.forEdit(null, marker(MarkerType.DEATH, false));
        screen.initForTest();
        for (boolean resize : new boolean[]{false, true}) {
            if (resize) screen.resizeForTest(1024, 768);
            List<EditBox> fields = screen.editBoxesForTest();
            check(fields.get(0).isEditable() && fields.get(1).isEditable(), "deathpoint name and category remain editable");
            for (int field = 2; field < 6; field++) {
                check(!fields.get(field).isEditable(), "death coordinates and state color remain read-only after resize");
            }
            List<Button> buttons = screen.buttonsForTest().stream().filter(button -> !(button instanceof EditBox)).toList();
            equal(2L, buttons.stream().filter(button -> !button.active).count(), "both icon controls remain disabled for deathpoints");
        }
    }

    private static void verifyMode(String mode, Supplier<WaypointEditScreen> factory) throws Exception {
        WaypointEditScreen screen = factory.get();
        screen.initForTest();
        Frame initial = render(screen);
        readableFrame(initial, mode + " initial");

        List<EditBox> fields = screen.editBoxesForTest();
        equal(6, fields.size(), mode + " has six edit boxes");
        fields.get(0).setValue(mode + " edited name");
        fields.get(1).setValue("category draft");
        fields.get(2).setValue("-123456");
        fields.get(3).setValue("72");
        fields.get(4).setValue("987654");
        fields.get(5).setValue("#12ABEF");

        screen.resizeForTest(1024, 768);
        List<EditBox> resizedFields = screen.editBoxesForTest();
        equal(6, resizedFields.size(), mode + " resize rebuilds six edit boxes exactly once");
        equal(List.of(mode + " edited name", "category draft", "-123456", "72", "987654", "#12ABEF"),
                resizedFields.stream().map(EditBox::getValue).toList(), mode + " resize preserves the draft text");
        equal(10, screen.buttonsForTest().size(), mode + " resize has ten total interactive widgets exactly once");

        Frame resized = render(screen);
        readableFrame(resized, mode + " resized");
    }

    private static void readableFrame(Frame frame, String context) {
        equal(1, frame.backgrounds(), context + " renders exactly one background");
        int background = indexOf(frame, "background");
        int panel = indexOf(frame, "panel");
        check(background >= 0 && background < panel, context + " draws the panel after the background");
        check(count(frame, "panel") == 1, context + " draws one panel");
        int panelOutline = indexOf(frame, "panel-outline");
        check(panelOutline > panel, context + " draws the panel outline after the panel fill");

        int title = indexOfPrefix(frame, "text:screen.odysseymap.");
        check(title > panelOutline, context + " draws the title over the outlined panel");
        int firstWidget = Math.min(indexOfPrefix(frame, "editbox:"), indexOfPrefix(frame, "button:"));
        check(firstWidget > title, context + " draws the title before every widget");
        for (String label : List.of("screen.odysseymap.waypoint_name", "gui.odysseymap.waypoint_category",
                "screen.odysseymap.waypoint_coords", "screen.odysseymap.waypoint_color")) {
            int index = indexOf(frame, "text:" + label);
            check(index > background, context + " draws label after background: " + label);
            check(index > panel, context + " draws label over the panel: " + label);
            check(index < firstWidget, context + " draws label before every widget: " + label);
        }

        for (Draw draw : frame.draws()) {
            if (!draw.value.equals("background")) check(!draw.blurred, context + " keeps UI sharp: " + draw.value);
        }

        equal(6, countPrefix(frame, "editbox:"), context + " renders six text fields");
        equal(4, countPrefix(frame, "button:"), context + " renders four buttons without duplicates");
        int lastWidget = Math.max(lastIndexOfPrefix(frame, "editbox:"), lastIndexOfPrefix(frame, "button:"));
        int firstPreview = indexOf(frame, "fill");
        check(firstPreview > lastWidget, context + " draws icon preview and color swatches after widgets");
        equal(11, count(frame, "fill"), context + " draws one icon preview and ten color swatches");
        equal(1, count(frame, "panel-outline"), context + " draws one panel outline");
        equal(1, count(frame, "swatch-outline"), context + " draws one selected swatch outline");
    }

    private static Frame render(WaypointEditScreen screen) throws Exception {
        boolean extractionApi = version.equals("26.1.2") || version.equals("26.2");
        String graphicsClass = extractionApi
                ? "net.minecraft.client.gui.GuiGraphicsExtractor" : "net.minecraft.client.gui.GuiGraphics";
        Object graphics = Class.forName(graphicsClass).getConstructor().newInstance();
        String entryPoint = extractionApi ? "extractRenderStateWithTooltipAndSubtitles" : "renderWithTooltip";
        Method frameMethod = screen.getClass().getMethod(entryPoint, graphics.getClass(), int.class, int.class, float.class);
        frameMethod.invoke(screen, graphics, 0, 0, 0.0f);
        RecordingSurface surface = (RecordingSurface) graphics;
        return new Frame(surface.backgrounds(), surface.draws());
    }

    private static int count(Frame frame, String value) {
        return (int) frame.draws().stream().filter(draw -> draw.value.equals(value)).count();
    }

    private static int countPrefix(Frame frame, String prefix) {
        return (int) frame.draws().stream().filter(draw -> draw.value.startsWith(prefix)).count();
    }

    private static int indexOf(Frame frame, String value) {
        for (int i = 0; i < frame.draws().size(); i++) if (frame.draws().get(i).value.equals(value)) return i;
        return -1;
    }

    private static int indexOfPrefix(Frame frame, String prefix) {
        for (int i = 0; i < frame.draws().size(); i++) if (frame.draws().get(i).value.startsWith(prefix)) return i;
        return -1;
    }

    private static int lastIndexOfPrefix(Frame frame, String prefix) {
        for (int i = frame.draws().size() - 1; i >= 0; i--) if (frame.draws().get(i).value.startsWith(prefix)) return i;
        return -1;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private record Frame(int backgrounds, List<Draw> draws) {}
}
