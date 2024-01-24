package org.sinytra.adapter.test.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.sinytra.adapter.test.classes.ParameterInjection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

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

    @WrapOperation(method = "testTargetWrap(Lorg/sinytra/adapter/test/classes/ParameterInjection$SomeClass;Ljava/lang/String;Ljava/util/concurrent/atomic/AtomicInteger;)V", at = @At(value = "INVOKE", target = "Lorg/sinytra/adapter/test/classes/ParameterInjection$SomeClass;execute(Ljava/lang/String;Ljava/util/concurrent/atomic/AtomicInteger;)V"))
    private void testWrapOperation(ParameterInjection.SomeClass someClass, String p1, Operation<Void> operation) {
        operation.call(someClass, p1);
    }

    private void testWrapOperationExpected(ParameterInjection.SomeClass someClass, String p1, AtomicInteger adapter_injected_2, Operation<Void> operation) {
        operation.call(someClass, p1, adapter_injected_2);
    }
}
