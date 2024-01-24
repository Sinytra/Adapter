package dev.su5ed.sinytra.adapter.patch.test.mixin;

import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.transformer.param.InjectParameterTransform;
import dev.su5ed.sinytra.adapter.patch.transformer.param.TransformParameters;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
                    // This is with cursed auto offsets
                    .transformParams(builder -> builder.inject(0, Type.INT_TYPE)
                            .withOffset())
        );
    }

    @Test
    void testBigParameterInjection() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterInjectionMixin",
                "testBigParameterInjection",
                Patch.builder()
                    .targetMethod("testTargetBig")
                    .transformParams(builder -> builder.inject(1, Type.DOUBLE_TYPE))
        );
    }

    @Test
    void testWrapOpInjection() throws Exception {
        assertSameCode(
                "org/sinytra/adapter/test/mixins/ParameterInjectionMixin",
                "testWrapOperation",
                Patch.builder()
                    .targetMethod("testTargetWrap")
                    .transformParams(builder -> builder.inject(2, Type.getType(AtomicInteger.class)))
        );
    }
}
