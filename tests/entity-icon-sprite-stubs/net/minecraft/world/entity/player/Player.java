package net.minecraft.world.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.util.UUID;

public class Player extends Entity {
    public Player(UUID uuid, EntityType type, double distance) { super(uuid, type, distance, false); }
}
