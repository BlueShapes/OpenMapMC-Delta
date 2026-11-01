package net.minecraft.client.player;

import com.mojang.authlib.GameProfile;
import java.util.UUID;

public final class LocalPlayer {
    public UUID getUUID() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }
    public GameProfile getGameProfile() { return new GameProfile(); }
}
