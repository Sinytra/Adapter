package org.sinytra.adapter.test.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RangedCrossbowAttackGoal.class)
public abstract class CrossbowAttackGoalMixin<T extends Monster & CrossbowAttackMob> extends Goal {
    @Shadow
    @Final
    private T mob;

    @Shadow
    @Final
    private Mob mobExpected;
}
