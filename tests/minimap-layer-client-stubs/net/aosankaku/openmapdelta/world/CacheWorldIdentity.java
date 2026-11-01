package net.aosankaku.openmapdelta.world;

import java.nio.file.Path;

public final class CacheWorldIdentity {
    private CacheWorldIdentity() {}
    public static String singleplayer(Path root, long seed, String worldId) { return "sp:" + root + ':' + seed + ':' + worldId; }
    public static String multiplayer(String address, long seed, String worldId, boolean required) {
        return "mp:" + address + ':' + seed + ':' + worldId + ':' + required;
    }
}
