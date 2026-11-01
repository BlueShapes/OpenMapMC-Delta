package net.minecraft.world.entity;

import java.util.UUID;

public class Entity {
    private final UUID uuid;
    private final EntityType type;
    private final double distance;
    private final boolean baby;
    public Entity(UUID uuid, EntityType type, double distance, boolean baby) {
        this.uuid = uuid; this.type = type; this.distance = distance; this.baby = baby;
    }
    public UUID getUUID() { return uuid; }
    public EntityType getType() { return type; }
    public double distanceToSqr(Entity other) { return Math.pow(distance - other.distance, 2); }
    public boolean isBaby() { return baby; }
}
