package org.sinytra.adapter.patch.test.mixin;

import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.junit.jupiter.api.Test;

public class ParameterRemoveTest extends MixinPatchTest {
    @Test
    void testSimpleRemove() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterRemoveMixin",
            "testSimpleRemove",
            Patch.builder()
                .targetInjectionPoint("")
                .targetMethod("testSimple")
                .targetMixinType(MixinConstants.INJECT)
                .transformParams(params -> params.remove(2))
        );
    }

    @Test
    void testWrapOpRemove() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixins/ParameterRemoveMixin",
            "testWrapOpRemove",
            Patch.builder()
                .targetMethod("testWrapOp")
                .transformParams(params -> params.remove(2))
        );
    }
}
