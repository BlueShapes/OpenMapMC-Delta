package net.minecraft.client.multiplayer;

import com.mojang.authlib.GameProfile;

public final class OnlinePlayer {
    private final GameProfile profile;
    public OnlinePlayer(GameProfile profile) { this.profile = profile; }
    public GameProfile getProfile() { return profile; }
}
