package net.minecraft.world.entity.animal.fish;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

public final class TropicalFish extends LivingEntity {
    private final String pattern;
    private final DyeColor base;
    private final DyeColor overlay;
    public TropicalFish(UUID uuid, String pattern, int base, int overlay) {
        super(uuid, new EntityType("minecraft:tropical_fish"), 1);
        this.pattern = pattern; this.base = new DyeColor(base); this.overlay = new DyeColor(overlay);
    }
    public Pattern getPattern() { return new Pattern(pattern); }
    public DyeColor getBaseColor() { return base; }
    public DyeColor getPatternColor() { return overlay; }
    public record Pattern(String serializedName) { public String getSerializedName() { return serializedName; } }
    public record DyeColor(int id) { public int getId() { return id; } }
}
