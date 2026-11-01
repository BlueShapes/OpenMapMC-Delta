// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Restores the vanilla renderer's temporary variant model after probing its real submission. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererModelAccessor {
    @Accessor("model") EntityModel<?> odysseymap$getModel();
    @Accessor("model") void odysseymap$setModel(EntityModel<?> model);
}
