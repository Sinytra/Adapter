package org.sinytra.adapter.test.mixin.pipeline;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(MultiPartBakedModel.class)
public class MultiPartBakedModelMixin {
    @Inject(method = "getQuads", at = @At("HEAD"), cancellable = true)
    private void onGetQuads(BlockState state, Direction side, RandomSource random, CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(List.of());
    }

    @Inject(
        method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onGetQuadsExpected(BlockState state, Direction side, RandomSource random, ModelData adapter_injected_3, RenderType adapter_injected_4, CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(List.of());
    }
}
