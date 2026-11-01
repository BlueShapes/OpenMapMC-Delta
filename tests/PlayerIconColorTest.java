// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.awt.Color;
import java.util.Random;
import java.util.UUID;

/** Independent vanilla formula oracle and known UUID vectors; no Minecraft process needed. */
public final class PlayerIconColorTest {
    private static int checks;

    public static void main(String[] args) {
        vector("069a79f4-44e9-4726-a5be-fca90e38aaf5", 0xFFE66185);
        vector("b50ad385-829d-3141-a216-7e7d7539ba7f", 0xFFD52CE6);
        vector("853c80ef-3c37-49fd-aa49-938b674adae6", 0xFF0EE6E4);
        vector("00000000-0000-0000-0000-000000000000", 0xFFE6E6E6);
        vector("00000000-0000-0000-0000-000000ff0000", 0xFFE60000);
        vector("00000000-0000-0000-0000-00000000ff00", 0xFF00E600);
        vector("00000000-0000-0000-0000-0000000000ff", 0xFF0000E6);
        vector("00000000-0000-0000-0000-000000000037", 0xFF0000E6);
        vector("00000000-0000-0000-0000-000000ffffff", 0xFFE6E6E6);

        Random random = new Random(20261009);
        for (int i = 0; i < 10000; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            int hash = id.hashCode();
            float[] hsv = Color.RGBtoHSB((hash >>> 16) & 255, (hash >>> 8) & 255, hash & 255, null);
            int expected = Color.HSBtoRGB(hsv[0], hsv[1], 0.9F);
            check(PlayerIconColor.fromUuid(id) == expected, "vanilla RGB/HSV formula for " + id);
            check(PlayerIconColor.fromUuid(UUID.fromString(id.toString())) == expected, "color survives UUID serialization");
        }
        // All 128 UUID bits contribute through hashCode, not just the final RGB-looking bytes.
        check(PlayerIconColor.fromUuid(new UUID(0x00000000FF000000L, 0)) == 0xFFE6E6E6,
                "high alpha-like byte is discarded after hashing");
        check(PlayerIconColor.fromUuid(new UUID(0x000000FF00000000L, 0)) == 0xFF0000E6,
                "upper UUID words contribute to color");
        System.out.println("Player icon color tests passed (" + checks + " assertions).");
    }

    private static void vector(String uuid, int expected) {
        check(PlayerIconColor.fromUuid(UUID.fromString(uuid)) == expected, "known UUID color " + uuid);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
