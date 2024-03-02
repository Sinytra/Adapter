package org.sinytra.adapter.patch.test.mixin;

import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.junit.jupiter.api.Test;

public class ParameterInlineTest extends MixinPatchTest {
    @Test
    void testSimpleInline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterInlineMixin",
            "testSimpleInline",
            Patch.builder()
                .targetInjectionPoint("")
                .targetMethod("simple")
                .targetMixinType(MixinConstants.INJECT)
                .transformParams(params -> params.inline(3, adapter -> adapter.visitLdcInsn("inlined")))
        );
    }

    @Test
    void testBigInline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterInlineMixin",
            "testBigInline",
            Patch.builder()
                .targetInjectionPoint("")
                .targetMethod("big")
                .targetMixinType(MixinConstants.INJECT)
                .transformParams(params -> params.inline(2, adapter -> adapter.visitLdcInsn(43.0d)))
        );
    }
}
