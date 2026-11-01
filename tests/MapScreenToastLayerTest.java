// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.jar.JarFile;

/** Executes the real handlers; checks their anchors against each loader's real game bytecode. */
public final class MapScreenToastLayerTest {
    private static int assertions;
    private static boolean neo;
    private static final Method SCREEN = handler("openmapdelta$toastsBehindMap");
    private static final Method TOAST = handler("openmapdelta$remainingToasts");

    public static void main(String[] args) throws Exception {
        neo = args[0].equals("neoforge");
        verifyNativeAnchors(Path.of(args[1]));
        // Reuse the mixin across frames: @Share's marker must be per invocation.
        MapScreenToastLayerMixin mixin = new MapScreenToastLayerMixin();
        Minecraft minecraft = new Minecraft();
        Field field = MapScreenToastLayerMixin.class.getDeclaredField("minecraft");
        field.setAccessible(true);
        field.set(mixin, minecraft);
        frame(mixin, minecraft, new FullscreenMapScreen(), true);
        frame(mixin, minecraft, new Screen(), false);
        frame(mixin, minecraft, null, false); // No screen / reload-overlay branch.
        frame(mixin, minecraft, new FullscreenMapScreen() {}, true);
        frame(mixin, minecraft, new Screen(), false);
        System.out.println(args[0] + " toast layer checks passed (" + assertions + " assertions).");
    }

    private static void frame(MapScreenToastLayerMixin mixin, Minecraft mc, Screen screen, boolean map) throws Exception {
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor();
        Stack<Screen> layers = new Stack<>();
        layers.push(new Screen());
        LocalBooleanRef shared = new LocalBooleanRef() {
            private boolean value;
            public boolean get() { return value; }
            public void set(boolean value) { this.value = value; }
        };
        int before = mc.gui.toastManager().extractions;
        int[] screenCalls = {0}, toastCalls = {0};
        Operation<Void> screenOperation = arguments -> {
            screenCalls[0]++;
            check(arguments[0] == screen, "same screen passed to original operation");
            int offset = neo ? 2 : 1;
            if (neo) check(arguments[1] == layers, "NeoForge background layer stack preserved");
            check(arguments[offset] == graphics, "same graphics passed to original operation");
            check(arguments[offset + 1].equals(31) && arguments[offset + 2].equals(42)
                    && arguments[offset + 3].equals(0.25f), "mouse and partial tick preserved");
            if (neo) graphics.record("screen-pre");
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, 31, 42, 0.25f);
            if (neo) graphics.record("screen-post");
            // Even a synchronous screen change must not duplicate this frame's toast extraction.
            mc.gui.screen = new Screen();
            return null;
        };
        if (screen != null) {
            if (neo) SCREEN.invoke(mixin, screen, layers, graphics, 31, 42, 0.25f, screenOperation, shared);
            else SCREEN.invoke(mixin, screen, graphics, 31, 42, 0.25f, screenOperation, shared);
        }
        Operation<Void> toastOperation = arguments -> {
            toastCalls[0]++;
            check(arguments[0] == mc.gui.toastManager() && arguments[1] == graphics, "toast arguments preserved");
            mc.gui.toastManager().extractRenderState(graphics);
            return null;
        };
        TOAST.invoke(mixin, mc.gui.toastManager(), graphics, toastOperation, shared);
        check(screenCalls[0] == (screen == null ? 0 : 1), "screen extracted exactly once when present");
        check(mc.gui.toastManager().extractions == before + 1, "toasts extracted exactly once per frame");
        check(toastCalls[0] == (map ? 0 : 1), "only the moved toast call is suppressed");
        check(shared.get() == map, "invocation marker only set for full map");
        int toast = index(graphics, "toast"), content = index(graphics, "screen");
        check(toast >= 0, "toast content retained");
        if (screen != null) {
            check(map ? toast < content : toast > content, "map order changed; other screens keep vanilla order");
            int toastLayer = layer(graphics.events.get(toast)), contentLayer = layer(graphics.events.get(content));
            check(map ? toastLayer < contentLayer : toastLayer > contentLayer, "strata agree with draw order");
            if (neo) {
                check(index(graphics, "screen-pre") < content && content < index(graphics, "screen-post"),
                        "NeoForge pre/post event operation preserved");
            }
        }
    }

    private static void verifyNativeAnchors(Path gameJar) throws Exception {
        List<String> gui = calls(gameJar, "net/minecraft/client/gui/Gui", "extractRenderState",
                "(Lnet/minecraft/client/DeltaTracker;ZZ)V");
        String screenTarget = target(SCREEN), toastTarget = target(TOAST);
        check(Collections.frequency(gui, screenTarget) == 1, "screen anchor matches exactly one native invocation");
        check(Collections.frequency(gui, toastTarget) == 1, "toast anchor matches exactly one native invocation");
        check(gui.indexOf(screenTarget) < gui.indexOf(toastTarget), "native screen precedes toast before fix");
        for (Method method : new Method[]{SCREEN, TOAST}) {
            WrapOperation wrap = method.getAnnotation(WrapOperation.class);
            check(wrap.require() == 1, "missing injection anchor fails closed");
            check(List.of(wrap.method()).equals(List.of("extractRenderState")), "wrapper targets GUI extraction");
        }
        List<String> screen = calls(gameJar, "net/minecraft/client/gui/screens/Screen",
                "extractRenderStateWithTooltipAndSubtitles", "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V");
        String next = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V";
        check(Collections.frequency(screen, next) >= 2, "native screen separates backdrop and content strata");
        check(screen.indexOf(next) < screen.indexOf("Lnet/minecraft/client/gui/screens/Screen;extractBackground"
                + "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"), "backdrop starts after a new stratum");
        List<String> toast = calls(gameJar, "net/minecraft/client/gui/components/toasts/ToastManager",
                "extractRenderState", "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V");
        check(toast.contains(next), "native toast owns its stratum");
    }

    private static String target(Method method) {
        WrapOperation wrap = method.getAnnotation(WrapOperation.class);
        if (wrap == null || wrap.at().length != 1) throw new AssertionError("Missing unique WrapOperation target");
        return wrap.at()[0].target();
    }

    private static List<String> calls(Path jar, String owner, String name, String descriptor) throws Exception {
        List<String> calls = new ArrayList<>();
        boolean[] found = {false};
        try (JarFile zip = new JarFile(jar.toFile())) {
            var entry = zip.getJarEntry(owner + ".class");
            if (entry == null) throw new AssertionError("Missing native class: " + owner);
            new ClassReader(zip.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] exceptions) {
                    if (!method.equals(name) || !desc.equals(descriptor)) return null;
                    found[0] = true;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String type, String method, String desc, boolean itf) {
                            calls.add("L" + type + ";" + method + desc);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        check(found[0], "native method exists: " + owner + "." + name);
        return calls;
    }

    private static Method handler(String name) {
        for (Method method : MapScreenToastLayerMixin.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) { method.setAccessible(true); return method; }
        }
        throw new AssertionError("Missing handler: " + name);
    }
    private static int index(GuiGraphicsExtractor graphics, String name) {
        for (int i = 0; i < graphics.events.size(); i++)
            if (graphics.events.get(i).startsWith(name + "@")) return i;
        return -1;
    }
    private static int layer(String event) { return Integer.parseInt(event.substring(event.indexOf('@') + 1)); }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
