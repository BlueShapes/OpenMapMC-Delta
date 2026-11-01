// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;
import java.util.UUID;
public final class DeathPointNoticeTest {
    public static void main(String[] args) {
        UUID noticeId = UUID.randomUUID();
        for (int deathY : new int[]{-128, -65, 320, 1000}) {
            var deathNotice = new LocationNotice(noticeId, "minecraft:overworld", 1000, deathY, 0,
                    300, null, "Please pick up my items");
            check(LocationNotice.validCoordinates(deathNotice.x(), deathNotice.y(), deathNotice.z()),
                    "void and sky deaths are valid notice locations: " + deathY);
            check(LocationNotice.decode(deathNotice.encode()).orElseThrow().equals(deathNotice),
                    "five-minute help notice keeps the actual out-of-build-height death coordinate: " + deathY);
        }
        check(LocationNotice.validCoordinates(-29_999_999, -30_000_000, 29_999_999)
                        && LocationNotice.validCoordinates(29_999_999, 30_000_000, -29_999_999),
                "notice limits retain inclusive vertical and exclusive horizontal boundaries");
        for (int[] position : new int[][]{{30_000_000, 0, 0}, {-30_000_000, 0, 0},
                {0, 0, 30_000_000}, {0, 0, -30_000_000}, {0, 30_000_001, 0},
                {0, -30_000_001, 0}, {Integer.MIN_VALUE, 0, 0}, {0, Integer.MIN_VALUE, 0}}) {
            check(!LocationNotice.validCoordinates(position[0], position[1], position[2]),
                    "notice limits reject coordinates beyond the bounded world without integer overflow");
            check(LocationNotice.decode(new LocationNotice(noticeId, "minecraft:overworld",
                    position[0], position[1], position[2]).encode()).isEmpty(),
                    "received notices enforce the same coordinate bounds as outgoing notices");
        }
        System.out.println("Deathpoint help-notice coordinate tests passed.");
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
