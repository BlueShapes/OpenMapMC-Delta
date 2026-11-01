package net.minecraft.world.entity.animal.fish;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

public class Cod extends LivingEntity {
    public Cod(UUID uuid) { this(uuid, "minecraft:cod"); }
    public Cod(UUID uuid, String typeId) { super(uuid, new EntityType(typeId), 1); }
}
