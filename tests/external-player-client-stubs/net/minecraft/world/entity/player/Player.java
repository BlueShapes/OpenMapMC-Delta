package net.minecraft.world.entity.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.Level;
import java.util.UUID;

public class Player {
    private final GameProfile profile;
    private final Level level;
    private final double x, y, z;
    private boolean invisible;
    public Player(UUID id, String name, Level level, double x, double y, double z) {
        profile = new GameProfile(id, name); this.level = level; this.x = x; this.y = y; this.z = z;
    }
    public Level level() { return level; }
    public boolean isInvisible() { return invisible; }
    public void setInvisible(boolean invisible) { this.invisible = invisible; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getBlockX() { return (int) Math.floor(x); }
    public int getBlockY() { return (int) Math.floor(y); }
    public int getBlockZ() { return (int) Math.floor(z); }
    public UUID getUUID() { return profile.getId(); }
    public GameProfile getGameProfile() { return profile; }
}
