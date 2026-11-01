// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.world.ChunkMapRevision;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class ChunkMapRevisionMixin implements ChunkMapRevision {
    @Unique private long odysseymap$mapRevision;

    @Override public long odysseymap$getMapRevision() { return odysseymap$mapRevision; }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void odysseymap$blockChanged(CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null) odysseymap$mapRevision++;
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void odysseymap$packetChanged(CallbackInfo callback) { odysseymap$mapRevision++; }
}
