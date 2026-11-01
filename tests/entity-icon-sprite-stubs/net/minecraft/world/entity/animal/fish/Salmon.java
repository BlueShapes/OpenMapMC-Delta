package net.minecraft.world.entity.animal.fish;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

public final class Salmon extends LivingEntity {
    private final Variant variant;
    public Salmon(UUID uuid, String variant) { super(uuid, new EntityType("minecraft:salmon"), 1); this.variant = new Variant(variant); }
    public Variant getVariant() { return variant; }
    public record Variant(String serializedName) { public String getSerializedName() { return serializedName; } }
}
