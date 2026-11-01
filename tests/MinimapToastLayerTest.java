// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.render.MinimapHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Runs both real mixin handlers in native call order, without a GPU or client startup. */
public final class MinimapToastLayerTest {
    private static int assertions;
    private static boolean neo;
    private static List<String> calls;
    private static final Method MINIMAP = handler(MixinGui.class, "odysseymap$renderMinimap");
    private static final Method SCREEN = handler(MapScreenToastLayerMixin.class, "openmapdelta$toastsBehindMap");
    private static final Method TOAST = handler(MapScreenToastLayerMixin.class, "openmapdelta$remainingToasts");
    private static final Inject INJECT = MINIMAP.getAnnotation(Inject.class);
    private static final String ANCHOR = INJECT.at()[0].target();
    private static final String SCREEN_ANCHOR = SCREEN.getAnnotation(WrapOperation.class).at()[0].target();
    private static final String TOAST_ANCHOR = TOAST.getAnnotation(WrapOperation.class).at()[0].target();

    public static void main(String[] args) throws Exception {
        neo = args[0].equals("neoforge");
        calls = nativeCalls(Path.of(args[1]));
        check(Collections.frequency(calls, ANCHOR) == 1, "unique native minimap anchor");
        check(List.of(INJECT.method()).equals(List.of("extractRenderState")), "GUI extraction target");
        check(INJECT.at()[0].shift() == At.Shift.BEFORE || INJECT.at()[0].shift() == At.Shift.AFTER,
                "unambiguous injection side");
        frame(null, true, true);
        frame(new Screen(), true, true);
        frame(new FullscreenMapScreen(), true, true);
        frame(new FullscreenMapScreen() {}, true, true);
        frame(null, false, true);
        frame(null, true, false);
        check(calls.indexOf(ANCHOR) <= calls.indexOf(TOAST_ANCHOR), "minimap extracted before native toast");
        System.out.println(args[0] + " minimap/toast checks passed (" + assertions + " assertions).");
    }

    private static void frame(Screen screen, boolean renderLevel, boolean hasWindow) throws Exception {
        Minecraft mc = new Minecraft();
        mc.gui.screen = screen;
        if (!hasWindow) mc.window = null;
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(mc, state, 31, 42);
        DeltaTracker delta = new DeltaTracker();
        MixinGui minimap = new MixinGui();
        set(minimap, "minecraft", mc);
        set(minimap, "guiRenderState", state);
        MapScreenToastLayerMixin toast = new MapScreenToastLayerMixin();
        set(toast, "minecraft", mc);
        LocalBooleanRef moved = new LocalBooleanRef() {
            private boolean value;
            public boolean get() { return value; }
            public void set(boolean value) { this.value = value; }
        };
        MinimapHudRenderer.calls = 0;
        MinimapHudRenderer.lastGraphics = null;
        // The production HUD renderer suppresses itself for the full map.
        MinimapHudRenderer.visible = !(screen instanceof FullscreenMapScreen);
        Operation<Void> originalScreen = args -> {
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, 31, 42, 0.25f);
            return null;
        };
        Operation<Void> originalToast = args -> {
            mc.gui.toastManager().extractRenderState(graphics);
            return null;
        };
        for (String call : calls) {
            if (call.equals(ANCHOR) && INJECT.at()[0].shift() == At.Shift.BEFORE)
                MINIMAP.invoke(minimap, delta, renderLevel, true, null);
            if (call.equals(SCREEN_ANCHOR) && screen != null) {
                if (neo) SCREEN.invoke(toast, screen, new Stack<Screen>(), graphics, 31, 42, 0.25f, originalScreen, moved);
                else SCREEN.invoke(toast, screen, graphics, 31, 42, 0.25f, originalScreen, moved);
            }
            if (call.equals(TOAST_ANCHOR)) TOAST.invoke(toast, mc.gui.toastManager(), graphics, originalToast, moved);
            if (call.equals(ANCHOR) && INJECT.at()[0].shift() == At.Shift.AFTER)
                MINIMAP.invoke(minimap, delta, renderLevel, true, null);
        }
        boolean eligible = renderLevel && hasWindow;
        check(MinimapHudRenderer.calls == (eligible ? 1 : 0), "level/window guards preserve extraction count");
        check(delta.calls == (eligible ? 1 : 0), "partial tick sampled only for valid HUD");
        check(mc.gui.toastManager().extractions == 1, "one toast extraction including fullscreen map");
        if (eligible) {
            GuiGraphicsExtractor hud = MinimapHudRenderer.lastGraphics;
            check(hud.state == state, "minimap and toast use shared GUI render state");
            check(hud.mouseX == 31 && hud.mouseY == 42, "scaled mouse coordinates retained");
        }
        if (eligible && !(screen instanceof FullscreenMapScreen)) {
            for (String element : List.of("terrain", "markers", "info")) {
                check(index(state, element) < index(state, "toast"), element + " must be behind notifications");
                check(layer(state, element) < layer(state, "toast"), element + " must use a lower stratum");
            }
            if (screen != null) check(index(state, "screen") < index(state, "terrain"), "ordinary screen/minimap order retained");
        }
        if (screen instanceof FullscreenMapScreen) {
            check(index(state, "toast") < index(state, "screen"), "fullscreen map still covers toast");
            check(layer(state, "toast") < layer(state, "screen"), "fullscreen screen stratum retained");
            check(index(state, "terrain") == -1, "fullscreen minimap remains hidden");
        }
    }

    private static List<String> nativeCalls(Path jar) throws Exception {
        List<String> result = new ArrayList<>();
        try (JarFile zip = new JarFile(jar.toFile())) {
            new ClassReader(zip.getInputStream(zip.getJarEntry("net/minecraft/client/gui/Gui.class")))
                    .accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                            if (!name.equals("extractRenderState") || !desc.equals("(Lnet/minecraft/client/DeltaTracker;ZZ)V")) return null;
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                                    result.add("L" + owner + ";" + method + descriptor);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        check(!result.isEmpty(), "native GUI extraction exists");
        return result;
    }
    private static Method handler(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name)) {
            method.setAccessible(true); return method;
        }
        throw new AssertionError("Missing handler: " + name);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static int index(GuiRenderState state, String name) {
        for (int i = 0; i < state.events.size(); i++) if (state.events.get(i).startsWith(name + "@")) return i;
        return -1;
    }
    private static int layer(GuiRenderState state, String name) {
        String event = state.events.get(index(state, name));
        return Integer.parseInt(event.substring(event.indexOf('@') + 1));
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
