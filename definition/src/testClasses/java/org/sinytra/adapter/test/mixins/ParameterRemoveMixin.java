package org.sinytra.adapter.test.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.sinytra.adapter.test.classes.ParameterRemove;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(ParameterRemove.class)
public class ParameterRemoveMixin {
    @Inject(method = "testSimple(IC)V", at = @At("HEAD"))
    private static void testSimpleRemove(int p1, char p2, boolean p3) {
        System.out.println("Hi: " + p2 / p1);
    }

    private static void testSimpleRemoveExpected(int p1, char p2) {
        System.out.println("Hi: " + p2 / p1);
    }

    @WrapOperation(method = "testWrapOp(Lorg/sinytra/adapter/test/classes/ParameterRemove$SObj;Ljava/util/concurrent/atomic/AtomicBoolean;)V", at = @At(value = "INVOKE", target = "Lorg/sinytra/adapter/test/classes/ParameterRemove$SObj;call(Ljava/util/concurrent/atomic/AtomicBoolean;)V"))
    private static void testWrapOpRemove(ParameterRemove.SObj obj, AtomicBoolean p1, int p2, Operation<Void> operation) {
        System.out.println(operation.call(obj, p1, p2));
    }

    private static void testWrapOpRemoveExpected(ParameterRemove.SObj obj, AtomicBoolean p1, Operation<Void> operation) {
        System.out.println(operation.call(obj, p1));
    }
}
