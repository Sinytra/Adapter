package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Fixture for the real-world shape of a repeated annotation, which ASM stores as a List.
 */
@Mixin(LivingEntity.class)
public class RepeatedAnnotationsMixin {
    @ModifyConstant(
        method = "getMaxAirSupply",
        constant = {@Constant(intValue = 1), @Constant(intValue = 2)},
        slice = {@Slice(from = @At("HEAD")), @Slice(to = @At("RETURN"))}
    )
    private static int repeatedAnnotations(int original) {
        return original;
    }
}
