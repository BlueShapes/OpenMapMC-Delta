// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.gui.ControllerNavigationBridge;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Optional client-only integration. No Controllable types enter Odyssey's linkage. */
public final class ControllerSupport {
    private static final ControllerNavigationBridge BRIDGE = new ControllerNavigationBridge();
    private static Class<?> controllable;
    private static Class<?> buttons;
    private static Class<?> bindingRegistry;
    private static boolean initialized;
    private static boolean reportedFailure;

    private ControllerSupport() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClassLoader loader = ControllerSupport.class.getClassLoader();
        if (!BRIDGE.connect(loader, () -> {
            var screen = Minecraft.getInstance().screen;
            return screen instanceof FullscreenMapScreen map ? map.getControllerNavigationPoints() : null;
        }, ControllerSupport::reportFailure)) return;
        try {
            controllable = Class.forName("com.mrcrayfish.controllable.Controllable", false, loader);
            buttons = Class.forName("com.mrcrayfish.controllable.client.input.Buttons", false, loader);
            bindingRegistry = Class.forName("com.mrcrayfish.controllable.client.binding.BindingRegistry", false, loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reportFailure(failure);
            controllable = null;
        }
    }

    /** Reads current bindings, including the active Shift layer when Delta supplies it. */
    public static List<Component> mapHints() {
        if (controllable == null) return List.of();
        try {
            Object controller = invoke(controllable, null, "getController");
            if (controller == null || Boolean.FALSE.equals(invoke(controller.getClass(), controller, "isOpen"))) {
                return List.of();
            }
            Object cursorActive = invoke(controller.getClass(), controller, "isUsingVirtualCursor");
            if (cursorActive == null) {
                Object input = invoke(controllable, null, "getInput");
                if (input == null || !Boolean.TRUE.equals(invoke(input.getClass(), input, "isControllerInUse"))) {
                    return List.of();
                }
                cursorActive = invoke(input.getClass(), input, "isVirtualCursorActive");
            }
            if (!Boolean.TRUE.equals(cursorActive)) return List.of();
            Object registry = invoke(controllable, null, "getBindingRegistry");
            if (registry == null) registry = invoke(bindingRegistry, null, "getInstance");
            if (registry == null) return List.of();
            if (Minecraft.getInstance().screen instanceof FullscreenMapScreen
                    && invoke(registry.getClass(), registry, "getBindingByDescriptionKey",
                    "controllable.key.openmap.confirm") != null) {
                return fullscreenProfileHints(registry, controller);
            }
            List<Component> hints = new ArrayList<>();
            addHint(hints, "select_drag", button(registry, controller, "controllable.key.pickup_item"));
            addHint(hints, "context", button(registry, controller, "controllable.key.split_stack"));
            Component up = button(registry, controller, "controllable.key.scroll_up");
            Component down = button(registry, controller, "controllable.key.scroll_down");
            if (up != null && down != null) {
                addHint(hints, "zoom", Component.empty().append(up).append(" / ").append(down));
            }
            addHint(hints, "back", button(registry, controller, "controllable.key.close_inventory"));
            return List.copyOf(hints);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reportFailure(failure);
            controllable = null;
            return List.of();
        }
    }

    private static List<Component> fullscreenProfileHints(Object registry, Object controller)
            throws ReflectiveOperationException {
        List<Component> hints = new ArrayList<>();
        addHint(hints, "confirm", profileButtons(registry, controller, "confirm"));
        addHint(hints, "context", profileButtons(registry, controller, "context", "context_alternate"));
        addHint(hints, "pan", profileButtons(registry, controller, "pan_up", "pan_down", "pan_left", "pan_right"));
        addHint(hints, "recenter", profileButtons(registry, controller, "recenter"));
        addHint(hints, "waypoints", profileButtons(registry, controller, "toggle_waypoints"));
        addHint(hints, "zoom_in", profileButtons(registry, controller, "zoom_in", "zoom_in_alternate"));
        addHint(hints, "zoom_out", profileButtons(registry, controller, "zoom_out", "zoom_out_alternate"));
        addHint(hints, "close", profileButtons(registry, controller, "close"));
        return List.copyOf(hints);
    }

    private static Component profileButtons(Object registry, Object controller, String... actions)
            throws ReflectiveOperationException {
        List<String> seen = new ArrayList<>();
        var combined = Component.empty();
        for (String action : actions) {
            Component name = button(registry, controller, "controllable.key.openmap." + action);
            if (name == null || seen.contains(name.getString())) continue;
            if (!seen.isEmpty()) combined = combined.append(" / ");
            combined = combined.append(name);
            seen.add(name.getString());
        }
        return seen.isEmpty() ? null : combined;
    }

    private static void addHint(List<Component> hints, String action, Component button) {
        if (button != null) hints.add(Component.translatable("gui.odysseymap.controller." + action, button));
    }

    private static Component button(Object registry, Object controller, String description)
            throws ReflectiveOperationException {
        Object binding = invoke(registry.getClass(), registry, "getBindingByDescriptionKey", description);
        if (binding == null) return null;
        Object activeButton = invoke(registry.getClass(), registry, "getActiveButton", binding, controller);
        if (activeButton == null) activeButton = invoke(binding.getClass(), binding, "getButton");
        if (!(activeButton instanceof Number value) || value.intValue() < 0) return null;
        Object name = invoke(buttons, null, "getNameForButton", value.intValue());
        return name instanceof String key ? Component.translatable(key) : null;
    }

    // Missing optional methods are expected between Controllable generations.
    private static Object invoke(Class<?> type, Object receiver, String name, Object... arguments)
            throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length
                    || (receiver == null && !Modifier.isStatic(method.getModifiers()))) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameters.length; i++) {
                Class<?> parameter = parameters[i] == int.class ? Integer.class : parameters[i];
                if (arguments[i] != null && !parameter.isInstance(arguments[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method.invoke(receiver, arguments);
        }
        return null;
    }

    private static void reportFailure(Throwable failure) {
        if (reportedFailure) return;
        reportedFailure = true;
        OpenMapDeltaCommon.LOGGER.warn("Optional Controllable map integration could not be used", failure);
    }
}
