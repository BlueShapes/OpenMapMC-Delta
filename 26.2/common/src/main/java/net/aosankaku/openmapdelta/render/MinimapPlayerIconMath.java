// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Screen rotation for local and remote player icons. */
public final class MinimapPlayerIconMath {
    private MinimapPlayerIconMath() {}

    /**
     * Directional icons point north when unrotated. Minecraft yaw 0 faces south,
     * so north-up mode adds 180 degrees; heading-up mode keeps them pointing up.
     */
    public static float rotationDegrees(boolean head, boolean rotateHead, boolean headingUp, float yaw) {
        return rotationDegrees(head, rotateHead, headingUp, yaw, headingUp ? yaw : 0.0f);
    }

    /**
     * Returns a player's icon rotation in map space. Directional icons point north when
     * unrotated; Minecraft yaw 0 faces south. Heading-up maps subtract the local heading.
     */
    public static float rotationDegrees(boolean head, boolean rotateHead, boolean headingUp,
                                        float playerYaw, float localYaw) {
        if (head && !rotateHead) return 0.0f;
        return headingUp ? playerYaw - localYaw : playerYaw + 180.0f;
    }
}
