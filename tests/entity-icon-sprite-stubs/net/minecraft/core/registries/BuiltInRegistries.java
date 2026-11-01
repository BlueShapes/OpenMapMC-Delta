package net.minecraft.core.registries;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public final class BuiltInRegistries {
    public static final EntityRegistry ENTITY_TYPE = new EntityRegistry();
    private BuiltInRegistries() {}
    public static final class EntityRegistry {
        public Identifier getKey(EntityType type) { return type == null ? null : new Identifier(type.id()); }
    }
}
