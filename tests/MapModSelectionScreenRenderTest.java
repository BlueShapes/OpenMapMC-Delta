package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.client.MapModSelectionController;
import net.minecraft.client.gui.Draw;
import net.minecraft.client.gui.RecordingSurface;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.List;

/** Calls the same version-specific Screen entry point used by Minecraft and checks draw order. */
public final class MapModSelectionScreenRenderTest {
    private static int assertions;
    private static String version;

    private MapModSelectionScreenRenderTest() {}

    public static void main(String[] args) throws Exception {
        version = System.getProperty("map.selection.version");
        if (version == null) throw new IllegalStateException("map.selection.version is required");
        for (boolean titleParent : new boolean[]{true, false}) {
            rendersReadableScreen(titleParent);
            rendersReadableChoiceFailure(titleParent);
            rendersReadableKeepFailure(titleParent);
        }
        System.out.println("MapModSelectionScreenRenderTest " + version + " passed " + assertions + " assertions.");
    }

    private static void rendersReadableScreen(boolean titleParent) throws Exception {
        MapModSelectionController.reset();
        MapModSelectionScreen screen = screen(titleParent);
        Frame first = render(screen);
        readableFrame(first, titleParent ? "Your installed map mods have changed." : "Choose one map mod to enable.",
                "Keep current settings");
        equal(4, screen.renderableCountForTest(), "panel and three buttons registered once");

        screen.resizeForTest(640, 480);
        equal(4, screen.renderableCountForTest(), "resize does not duplicate registered renderables");
        Frame resized = render(screen);
        readableFrame(resized, titleParent ? "Your installed map mods have changed." : "Choose one map mod to enable.",
                "Keep current settings");
    }

    private static void rendersReadableChoiceFailure(boolean titleParent) throws Exception {
        MapModSelectionController.reset();
        MapModSelectionController.failChoose = true;
        MapModSelectionScreen screen = screen(titleParent);
        screen.buttonsForTest().get(0).press();
        Frame frame = render(screen);
        readableFrame(frame, titleParent ? "Your installed map mods have changed." : "Choose one map mod to enable.",
                "Keep current settings");
        assertFailureAfterButtons(frame);
    }

    private static void rendersReadableKeepFailure(boolean titleParent) throws Exception {
        MapModSelectionController.reset();
        MapModSelectionController.failKeep = true;
        MapModSelectionScreen screen = screen(titleParent);
        screen.buttonsForTest().get(screen.buttonsForTest().size() - 1).press();
        Frame frame = render(screen);
        readableFrame(frame, titleParent ? "Your installed map mods have changed." : "Choose one map mod to enable.",
                "Continue without saving");
        assertFailureAfterButtons(frame);
        equal("Continue without saving", screen.buttonsForTest().get(2).message(), "keep failure updates its button label");
    }

    private static MapModSelectionScreen screen(boolean titleParent) {
        Screen parent = titleParent ? new TitleScreen() : new Screen(Component.translatable("in-game parent"));
        MapModSelectionScreen screen = new MapModSelectionScreen(parent);
        screen.initForTest();
        return screen;
    }

    private static Frame render(MapModSelectionScreen screen) throws Exception {
        String graphicsClass = version.equals("26.1.2") || version.equals("26.2")
                ? "net.minecraft.client.gui.GuiGraphicsExtractor" : "net.minecraft.client.gui.GuiGraphics";
        Object graphics = Class.forName(graphicsClass).getConstructor().newInstance();
        String methodName = version.equals("26.1.2") || version.equals("26.2")
                ? "extractRenderStateWithTooltipAndSubtitles" : "renderWithTooltip";
        Method frameMethod = screen.getClass().getMethod(methodName, graphics.getClass(), int.class, int.class, float.class);
        frameMethod.invoke(screen, graphics, 0, 0, 0.0f);
        RecordingSurface surface = (RecordingSurface) graphics;
        return new Frame(surface.backgrounds(), surface.draws());
    }

    private static void readableFrame(Frame frame, String expectedExplanation, String expectedKeepLabel) {
        equal(1, frame.backgrounds(), "frame draws one background");
        check(contains(frame, "panel"), "panel is drawn");
        check(contains(frame, "text:Choose a map mod"), "title is drawn");
        check(contains(frame, "text:" + expectedExplanation), "localized explanation is drawn");
        check(contains(frame, "text:Choose again in OpenMap Delta settings.") ||
                        contains(frame, "text:Installed mods and map data are kept."),
                "second explanation line is drawn");
        check(contains(frame, "button:Enable OpenMap Delta"), "OpenMap choice is drawn");
        check(contains(frame, "button:Enable JourneyMap"), "JourneyMap choice is drawn");
        check(contains(frame, "button:" + expectedKeepLabel), "keep choice is drawn");
        for (Draw draw : frame.draws()) {
            if (!draw.value.equals("background")) check(!draw.blurred, "UI remains sharp: " + draw.value);
        }
        check(indexOf(frame, "panel") < indexOfPrefix(frame, "text:Choose a map mod"), "panel draws before its title");
        check(indexOfPrefix(frame, "text:" + expectedExplanation) < indexOfPrefix(frame, "button:"),
                "explanation draws before buttons");
        equal(3, countPrefix(frame, "button:"), "buttons are not duplicated");
    }

    private static void assertFailureAfterButtons(Frame frame) {
        check(contains(frame, "text:Could not save the selection. Check the log and try again."),
                "failure text is visible");
        check(lastIndexOfPrefix(frame, "button:") < indexOfPrefix(frame, "text:Could not save the selection."),
                "failure text remains above the panel widgets");
    }

    private static boolean contains(Frame frame, String expected) {
        return frame.draws().stream().anyMatch(draw -> draw.value.equals(expected));
    }

    private static int indexOf(Frame frame, String expected) {
        for (int i = 0; i < frame.draws().size(); i++) if (frame.draws().get(i).value.equals(expected)) return i;
        return Integer.MAX_VALUE;
    }

    private static int indexOfPrefix(Frame frame, String prefix) {
        for (int i = 0; i < frame.draws().size(); i++) if (frame.draws().get(i).value.startsWith(prefix)) return i;
        return Integer.MAX_VALUE;
    }

    private static int lastIndexOfPrefix(Frame frame, String prefix) {
        for (int i = frame.draws().size() - 1; i >= 0; i--) if (frame.draws().get(i).value.startsWith(prefix)) return i;
        return -1;
    }

    private static int countPrefix(Frame frame, String prefix) {
        return (int) frame.draws().stream().filter(draw -> draw.value.startsWith(prefix)).count();
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
