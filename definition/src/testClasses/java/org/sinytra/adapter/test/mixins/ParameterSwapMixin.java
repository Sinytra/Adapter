package org.sinytra.adapter.test.mixins;

import org.sinytra.adapter.test.classes.ParameterSwap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParameterSwap.class)
public class ParameterSwapMixin {
    @Inject(method = "injectTarget(Ljava/lang/String;I)V", at = @At(value = "HEAD"))
    private void testSwap(int theInt, String theString, CallbackInfo ci) {
        System.out.println("Int: " + theInt);
        System.out.println("Str: " + theString);
    }

    private void testSwapExpected(String theString, int theInt, CallbackInfo ci) {
        System.out.println("Int: " + theInt);
        System.out.println("Str: " + theString);
    }

    @Inject(method = "injectTarget2(Ljava/lang/String;SJ)V", at = @At("HEAD"))
    private void testComplexSwap(int theInt, short theShort, String theString, CallbackInfo ci) {
        System.out.println(theString.repeat(theInt + theShort));
    }

    private void testComplexSwapExpected(String theString, int theInt, short theShort, CallbackInfo ci) {
        System.out.println(theString.repeat(theInt + theShort));
    }

    @Inject(method = "injectTarget3(Ljava/lang/String;J)V", at = @At("HEAD"))
    private void testBigSwap(long theLong, String theString, CallbackInfo ci) {
        System.out.println(theString.repeat((int)theLong / 2));
    }

    private void testBigSwapExpected(String theString, long theLong, CallbackInfo ci) {
        System.out.println(theString.repeat((int)theLong / 2));
    }
}
