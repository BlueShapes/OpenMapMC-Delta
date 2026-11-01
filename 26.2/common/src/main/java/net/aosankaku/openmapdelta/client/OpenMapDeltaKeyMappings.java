// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class OpenMapDeltaKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "keys"));

    public static final int KEY_TOGGLE_MINIMAP = GLFW.GLFW_KEY_M;
    public static final int KEY_OPEN_FULLSCREEN = GLFW.GLFW_KEY_J;
    public static final int KEY_ZOOM_IN = GLFW.GLFW_KEY_EQUAL;
    public static final int KEY_ZOOM_OUT = GLFW.GLFW_KEY_MINUS;
    public static final int KEY_CREATE_WAYPOINT = GLFW.GLFW_KEY_B;
    public static final int KEY_OPEN_SETTINGS = GLFW.GLFW_KEY_O;

    private OpenMapDeltaKeyMappings() {}
}
