package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MultiPartModel.class)
public class MultiPartModelMixin {
    @Inject(method = "collectParts", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;setSeed(J)V"))
    private void onSetSeed(RandomSource random, List<?> output, CallbackInfo ci) {
        random.nextInt();
    }

    @Inject(
        method = "collectParts(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/List;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;setSeed(J)V")
    )
    private void onSetSeedExpected(BlockAndTintGetter adapter_injected_0, BlockPos adapter_injected_1, BlockState adapter_injected_2, RandomSource random, List<?> output, CallbackInfo ci) {
        random.nextInt();
    }

    @ModifyVariable(method = "collectParts", at = @At("HEAD"), argsOnly = true)
    private RandomSource modifyRandom(RandomSource value, RandomSource random) {
        return random;
    }

    @ModifyVariable(
        method = "collectParts(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/List;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private RandomSource modifyRandomExpected(RandomSource value, BlockAndTintGetter adapter_injected_1, BlockPos adapter_injected_2, BlockState adapter_injected_3, RandomSource random) {
        return random;
    }
}
