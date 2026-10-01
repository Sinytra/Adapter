package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * ASM turns a repeated annotation into a List, and a parser that assumes a single annotation then either
 * dereferences null or casts the list straight to AnnotationNode - either way aborting the transform for the
 * whole mod instead of skipping the property. TARGET_CONSTANT had exactly that bug: the repeated {@code constant}
 * below used to reach {@code new AnnotationHandle(null)} and throw a NullPointerException inside MixinParser
 * (reference case: shuttfup's DefaultLANPort). This fixture is the real-world shape that has to be parsed.
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
