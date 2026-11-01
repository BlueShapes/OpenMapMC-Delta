package net.minecraft.client.multiplayer;

import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.List;

public final class ClientLevel {
    public final List<Entity> entities = new ArrayList<>();
    private long gameTime;
    public Iterable<Entity> entitiesForRendering() { return entities; }
    public long getGameTime() { return gameTime; }
    public void setGameTime(long value) { gameTime = value; }
}
