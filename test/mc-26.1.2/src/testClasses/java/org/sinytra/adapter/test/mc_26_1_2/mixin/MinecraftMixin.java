package org.sinytra.adapter.test.mc_26_1_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    // NeoForge adds a local for the input event and the block position moves from index 6 to 7
    @ModifyVariable(
        method = "startAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getBlockPos()Lnet/minecraft/core/BlockPos;", shift = At.Shift.BY, by = 2),
        index = 6
    )
    private BlockPos modifyAttackedPos(BlockPos pos) {
        return pos;
    }

    @ModifyExpressionValue(
        method = "startAttack()Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getBlockPos()Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos modifyAttackedPosExpected(BlockPos pos) {
        return pos;
    }
}
