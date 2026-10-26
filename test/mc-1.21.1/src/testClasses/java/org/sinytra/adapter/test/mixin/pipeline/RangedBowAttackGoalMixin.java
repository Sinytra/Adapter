package org.sinytra.adapter.test.mixin.pipeline;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RangedBowAttackGoal.class)
public class RangedBowAttackGoalMixin {
    // NeoForge widened the goal's mob from Monster to Mob, so the calls now go to Mob
    @Redirect(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Monster;getTarget()Lnet/minecraft/world/entity/LivingEntity;")
    )
    private LivingEntity redirectTarget(Monster instance) {
        return instance.getTarget();
    }

    @Redirect(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getTarget()Lnet/minecraft/world/entity/LivingEntity;")
    )
    private LivingEntity redirectTargetExpected(Mob instance) {
        return instance.getTarget();
    }

    @WrapOperation(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Monster;getTarget()Lnet/minecraft/world/entity/LivingEntity;")
    )
    private LivingEntity wrapTarget(Monster instance, Operation<LivingEntity> original) {
        return original.call(instance);
    }

    @WrapOperation(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getTarget()Lnet/minecraft/world/entity/LivingEntity;")
    )
    private LivingEntity wrapTargetExpected(Mob instance, Operation<LivingEntity> original) {
        return original.call(instance);
    }
}
