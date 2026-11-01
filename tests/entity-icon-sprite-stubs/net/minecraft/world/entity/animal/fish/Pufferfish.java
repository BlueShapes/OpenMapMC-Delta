package net.minecraft.world.entity.animal.fish;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

public final class Pufferfish extends LivingEntity {
    private final int puffState;
    public Pufferfish(UUID uuid, int puffState) { super(uuid, new EntityType("minecraft:pufferfish"), 1); this.puffState = puffState; }
    public int getPuffState() { return puffState; }
}
