package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RangedCrossbowAttackGoal.class)
public interface RangedCrossbowAttackGoalAccessor {
    @Accessor
    Monster getMob();
}
