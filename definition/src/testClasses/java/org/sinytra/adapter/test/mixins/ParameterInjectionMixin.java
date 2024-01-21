package org.sinytra.adapter.test.mixins;

import org.sinytra.adapter.test.classes.ParameterInjection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParameterInjection.class)
public class ParameterInjectionMixin {
    @Redirect(method = "testRedirect(Ljava/lang/String;I)V", at = @At(value = "INVOKE", target = "Ljava/lang/String;repeat()Ljava/lang/String;"))
    private String testRedirectInjection(String owner) {
        return owner.repeat(1);
    }

    private String testRedirectInjectionExpected(String owner, int adapter_injected_1) {
        return owner.repeat(1);
    }

    @Inject(method = "testTargetBig(JDF)V", at = @At("HEAD"))
    private void testBigParameterInjection(long a, float c, CallbackInfo ci) {
        System.out.println(c / a);
    }

    private void testBigParameterInjectionExpected(long a, double adapter_injected_1, float c, CallbackInfo ci) {
        System.out.println(c / a);
    }
}
