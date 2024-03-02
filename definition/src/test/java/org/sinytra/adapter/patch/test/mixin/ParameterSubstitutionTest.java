package org.sinytra.adapter.patch.test.mixin;

import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.junit.jupiter.api.Test;

public class ParameterSubstitutionTest extends MixinPatchTest {
    @Test
    void testSimpleSubstitution() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterSubstitutionMixin",
            "testSubstitution",
            Patch.builder()
                .targetInjectionPoint("")
                .targetMethod("injectTarget")
                .targetMixinType(MixinConstants.INJECT)
                .transformParams(params -> params.substitute(1, 0))
        );
    }

    @Test
    void testBigSubstitution() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterSubstitutionMixin",
            "testBigSubstitution",
            Patch.builder()
                .targetInjectionPoint("")
                .targetMethod("injectTarget2")
                .targetMixinType(MixinConstants.INJECT)
                .transformParams(params -> params.substitute(2, 0))
        );
    }

    @Test
    void testComplexSubstitution() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterSubstitutionMixin",
            "testComplexSubstitution",
            Patch.builder()
                .targetInjectionPoint("")
                .targetMethod("injectTarget3")
                .targetMixinType(MixinConstants.INJECT)
                .transformParams(params -> params.substitute(1, 2))
        );
    }

}
