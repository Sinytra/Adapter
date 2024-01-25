package org.sinytra.adapter.test.mixins;

import org.sinytra.adapter.test.classes.ParameterInline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(ParameterInline.class)
public class ParameterInlineMixin {
    @Inject(method = "simple(ILjava/lang/String;ILjava/lang/String;)V", at = @At("HEAD"))
    private static void testSimpleInline(int p1, String p2, int p3, String p4) {
        System.out.println(p4);
    }

    private static void testSimpleInlineExpected(int p1, String p2, int p3) {
        System.out.println("inlined");
    }

    @Inject(method = "big(IDI)V", at = @At("HEAD"))
    private static void testBigInline(int p1, double p2, double p3, int p4) {
        System.out.println(p3 * p2);
        System.out.println("p4: " + p4);
    }

    private static void testBigInlineExpected(int p1, double p2, int p4) {
        System.out.println(43d * p2);
        System.out.println("p4: " + p4);
    }
}
