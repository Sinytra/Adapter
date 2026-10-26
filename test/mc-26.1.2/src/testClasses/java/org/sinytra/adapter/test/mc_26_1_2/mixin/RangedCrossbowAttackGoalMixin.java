package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedCrossbowAttackGoal.class)
public class RangedCrossbowAttackGoalMixin {
    @Shadow
    @Final
    private Monster mob;

    @Inject(method = "tick", at = @At("HEAD"))
    private void readMob(CallbackInfo ci) {
        this.mob.getTarget();
    }
}
