package org.sinytra.adapter.test.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(RangedCrossbowAttackGoal.class)
public abstract class CrossbowAttackGoalMixin<T extends Monster & CrossbowAttackMob> extends Goal {
    // https://github.com/SolipIngen/minecraft.progressivearchery/blob/7af8bdb7ddc24d73163d17de082add89716755cb/src/main/java/solipingen/progressivearchery/mixin/entity/ai/goal/CrossbowAttackGoalMixin.java#L42
    @Redirect(
        method = "tick()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getWeaponHoldingHand(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/InteractionHand;"
        )
    )
    private InteractionHand redirectedGetHandPossiblyHolding(LivingEntity entity, Item item) {
        return ProjectileUtil.getWeaponHoldingHand(entity, item);
    }

    @Redirect(
        method = "tick()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getWeaponHoldingHand(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Lnet/minecraft/world/InteractionHand;"
        )
    )
    private InteractionHand redirectedGetHandPossiblyHoldingExpected(LivingEntity entity, Predicate<Item> item) {
        return ProjectileUtil.getWeaponHoldingHand(entity, item);
    }
}
