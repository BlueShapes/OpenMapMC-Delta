package net.minecraft.world.entity;

import java.util.UUID;

public class LivingEntity extends Entity {
    public LivingEntity(UUID uuid, EntityType type, double distance) { super(uuid, type, distance, false); }
}
