package dev.su5ed.sinytra.adapter.patch.test.mixin;

import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import org.junit.jupiter.api.Test;

public class ParameterSwapTest extends MixinPatchTest {

    // Test swap between two parameters of size 1
    @Test
    void testSimpleSwap() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterSwapMixin",
                "testSwap",
                Patch.builder()
                        .targetInjectionPoint("")
                        .targetMethod("injectTarget")
                        .targetMixinType(MixinConstants.INJECT)
                        .transformParams(params -> params.swap(0, 1))
        );
    }

    // Test multiple sequential swaps
    @Test
    void testComplexSwap() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterSwapMixin",
                "testComplexSwap",
                Patch.builder()
                        .targetInjectionPoint("")
                        .targetMethod("injectTarget2")
                        .targetMixinType(MixinConstants.INJECT)
                        .transformParams(params -> params.swap(2, 1)
                                .swap(1, 0))
        );
    }

    // Test to make sure that longs and doubles (which use 2 LVT spaces) are accounted for
    @Test
    void testBigSwap() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterSwapMixin",
                "testBigSwap",
                Patch.builder()
                        .targetInjectionPoint("")
                        .targetMethod("injectTarget3")
                        .targetMixinType(MixinConstants.INJECT)
                        .transformParams(params -> params.swap(0, 1))
        );
    }
}
