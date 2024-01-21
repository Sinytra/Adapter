package org.sinytra.adapter.test.mixins;

import org.sinytra.adapter.test.classes.ParameterSubstitution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParameterSubstitution.class)
public class ParameterSubstitutionMixin {
    @Inject(at = @At("HEAD"), method = "injectTarget(I)V")
    private void testSubstitution(int a, int a1, CallbackInfo ci) {
        System.out.println(a + a1);
    }

    private void testSubstitutionExpected(int a, CallbackInfo ci) {
        System.out.println(a + a);
    }

    @Inject(at = @At("HEAD"), method = "injectTarget2(JLjava/lang/String;)V")
    private void testBigSubstitution(long a, String b, long a1, CallbackInfo ci) {
        System.out.println(b + ": " + a + a1);
    }

    private void testBigSubstitutionExpected(long a, String b, CallbackInfo ci) {
        System.out.println(b + ": " + a + a);
    }

    @Inject(at = @At("HEAD"), method = "injectTarget3(JDLjava/lang/String;)V")
    private void testComplexSubstitution(long a, double b, double b1, String c, CallbackInfo ci) {
        System.out.println(c + ": " + a + (b + b1 / 2));
    }

    // Substitute b with b1
    private void testComplexSubstitutionExpected(long a, double b1, String c, CallbackInfo ci) {
        System.out.println(c + ": " + a + (b1 + b1 / 2));
    }
}
