package org.sinytra.adapter.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FarmBlock.class)
public class FarmLandBlockMixin {
    // https://github.com/warior456/Sculk-Depths/blob/06870f1ab93b8d500bbbea285eebbfa7db6a5f89/src/main/java/net/ugi/sculk_depths/mixin/FarmLandBlockMixin.java#L23
    @ModifyExpressionValue(
        method = "isNearWater",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z")
    )
    private static boolean isFarmlandNearWater(boolean original, @Local(ordinal = 0) LevelReader world, @Local(ordinal = 1) BlockPos blockPos) {
        return original;
    }

    @ModifyExpressionValue(
        method = "isNearWater",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;canBeHydrated(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/core/BlockPos;)Z")
    )
    private static boolean isFarmlandNearWaterExpected(boolean original, @Local(ordinal = 0) LevelReader world, @Local(ordinal = 1) BlockPos blockPos) {
        return original;
    }
}
