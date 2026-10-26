package org.sinytra.adapter.test.mixin.pipeline;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FlowerPotBlock.class)
public class FlowerPotBlockMixin {
    // NeoForge calls defaultBlockState on the empty pot, a FlowerPotBlock, instead of on Blocks.FLOWER_POT
    @Redirect(
        method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState emptyPotState(Block instance) {
        return instance.defaultBlockState();
    }

    @Redirect(
        method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FlowerPotBlock;defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState emptyPotStateExpected(FlowerPotBlock instance) {
        return instance.defaultBlockState();
    }
}
