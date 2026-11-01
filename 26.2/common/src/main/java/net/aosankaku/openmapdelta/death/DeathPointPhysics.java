// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Item-sized vertical collision without spawning an entity or requesting chunks. */
public final class DeathPointPhysics implements DeathPointMotion.Terrain {
    private final Level level;
    public DeathPointPhysics(Level level) { this.level = level; }

    private static AABB box(double x, double y, double z) {
        return new AABB(x - 0.125, y, z - 0.125, x + 0.125, y + 0.25, z + 0.125);
    }

    @Override public boolean loaded(double x, double y, double z, double dy) {
        AABB area = box(x, y, z).expandTowards(0, dy, 0).inflate(1);
        for (int cx = ((int) Math.floor(area.minX)) >> 4; cx <= ((int) Math.floor(area.maxX)) >> 4; cx++)
            for (int cz = ((int) Math.floor(area.minZ)) >> 4; cz <= ((int) Math.floor(area.maxZ)) >> 4; cz++)
                if (!level.hasChunkAt(new BlockPos(cx << 4, (int) Math.floor(y), cz << 4))) return false;
        return true;
    }

    @Override public double collide(double x, double y, double z, double dy) {
        double actual = Entity.collideBoundingBox((Entity) null, new Vec3(0, dy, 0), box(x, y, z), level, List.of()).y;
        // Visible/tracked items supply exact buoyancy. Prediction waits at water/lava instead of sinking past them.
        if (dy < 0) for (int by = (int) Math.floor(y + dy); by <= (int) Math.floor(y); by++) {
            var pos = new BlockPos((int) Math.floor(x), by, (int) Math.floor(z));
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)) {
                double surface = by + fluid.getHeight(level, pos);
                if (surface >= y + dy) actual = Math.max(actual, Math.min(0, surface - y));
            }
        }
        return actual;
    }

    @Override public double minimumY() { return Math.max(-2048, level.getMinY() - 64); }
}
