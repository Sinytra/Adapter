package org.sinytra.adapter.test.mixin;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Level.class)
public class LevelMixin {
    // https://github.com/RelativityMC/C2ME-fabric/blob/93ffdce7b92051ab9d2d9f6ee7dd74b31a018533/c2me-notickvd/src/main/java/com/ishland/c2me/notickvd/mixin/MixinWorld.java
    @ModifyArg(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/FullChunkStatus;isOrAfter(Lnet/minecraft/server/level/FullChunkStatus;)Z"
        )
    )
    private FullChunkStatus modifyLeastStatus(FullChunkStatus levelType) {
        return levelType.ordinal() > FullChunkStatus.FULL.ordinal() ? FullChunkStatus.FULL : levelType;
    }

    @ModifyArg(
        method = "markAndNotifyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/FullChunkStatus;isOrAfter(Lnet/minecraft/server/level/FullChunkStatus;)Z"
        )
    )
    private FullChunkStatus modifyLeastStatusExpected(FullChunkStatus levelType) {
        return levelType.ordinal() > FullChunkStatus.FULL.ordinal() ? FullChunkStatus.FULL : levelType;
    }
}
