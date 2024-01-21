package dev.su5ed.sinytra.adapter.patch.test.mixin;

import dev.su5ed.sinytra.adapter.patch.api.Patch;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

public class ParameterInjectionTest extends MixinPatchTest {
    @Test
    void testRedirectInjection() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterInjectionMixin",
                "testRedirectInjection",
                Patch.builder()
                    .targetMethod("testRedirect")
                    .targetInjectionPoint("Ljava/lang/String;repeat()Ljava/lang/String;")
                    .modifyInjectionPoint("Ljava/lang/String;repeat(I)Ljava/lang/String;")
                    // This is with auto offset
                    .modifyParams(p -> p.insert(0, Type.INT_TYPE))
        );
    }

    @Test
    void testBigParameterInjection() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterInjectionMixin",
                "testBigParameterInjection",
                Patch.builder()
                    .targetMethod("testTargetBig")
                    .modifyParams(p -> p.insert(1, Type.DOUBLE_TYPE))
        );
    }
}
