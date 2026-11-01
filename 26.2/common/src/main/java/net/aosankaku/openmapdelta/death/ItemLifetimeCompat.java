// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Forge/NeoForge expose lifespan; Fabric follows vanilla's 6000 ticks. Refresh on every sample. */
public final class ItemLifetimeCompat {
    private static final int VANILLA_LIFETIME = 6000;
    private static final Field LIFESPAN = findLifespan();
    private static final Method STACK_LIFESPAN = findStackLifespan();
    private ItemLifetimeCompat() {}

    public static long remaining(ItemEntity item) {
        int lifespan = VANILLA_LIFETIME;
        try { if (LIFESPAN != null) lifespan = LIFESPAN.getInt(item); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { /* Conservative vanilla fallback. */ }
        return remaining(item.getAge(), lifespan);
    }

    static long remaining(int age, int lifespan) {
        // Extended lifetime (-6000) is finite. Unlimited lifetime has its own exact sentinel.
        return age == -32768 ? -1 : Math.max(1, (long) lifespan - age);
    }

    public static long futureLifetime(ItemStack stack, Level level) {
        try {
            if (STACK_LIFESPAN != null) return Math.max(1, ((Number) STACK_LIFESPAN.invoke(stack, level)).longValue());
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Vanilla lifespan when unsupported. */ }
        return VANILLA_LIFETIME;
    }

    private static Field findLifespan() {
        try { return ItemEntity.class.getField("lifespan"); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private static Method findStackLifespan() {
        try { return ItemStack.class.getMethod("getEntityLifespan", Level.class); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
}
