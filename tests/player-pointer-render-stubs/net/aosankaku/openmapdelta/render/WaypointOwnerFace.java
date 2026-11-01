package net.aosankaku.openmapdelta.render;

import java.util.UUID;

public final class WaypointOwnerFace {
    public static boolean textureAvailable = true;
    private WaypointOwnerFace() {}
    @SuppressWarnings("unchecked")
    public static <T> T texture(UUID playerId, String playerName) {
        if (!textureAvailable) return null;
        Object texture = System.getProperty("pointer.version", "26.2").startsWith("1.")
                ? net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/skin.png")
                : net.minecraft.resources.Identifier.parse("minecraft:textures/skin.png");
        return (T) texture;
    }
}
