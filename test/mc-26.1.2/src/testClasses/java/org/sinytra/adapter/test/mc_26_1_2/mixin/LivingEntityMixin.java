package org.sinytra.adapter.test.mc_26_1_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    public LivingEntityMixin() {
    }

    @WrapOperation(
        method = "playBlockFallSound",
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"
        )}
    )
    private void playSoundCorrectlyForBlocks(LivingEntity instance, SoundEvent sound, float volume, float pitch, Operation<Void> original, @Local BlockState state) {
        original.call(instance, sound, volume, pitch);
    }

    @WrapOperation(
        method = "playFallSound(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V",
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"
        )}
    )
    private void playSoundCorrectlyForBlocksExpected(LivingEntity instance, SoundEvent sound, float volume, float pitch, Operation<Void> original, @Local BlockState state) {
        original.call(instance, sound, volume, pitch);
    }
}
