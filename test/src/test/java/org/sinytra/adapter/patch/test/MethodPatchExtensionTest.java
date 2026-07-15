package org.sinytra.adapter.patch.test;

import org.junit.jupiter.api.Test;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.mixin.MixinTypes;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MethodPatchExtensionTest {
    @Test
    void recognizesModifyConstantMixins() {
        assertSame(
            MixinTypes.MODIFY_CONST,
            MixinTypes.getMixinType(ModifyConstant.class.getName().replace('.', '/'))
        );
    }

    @Test
    void updatesMethodAndInjectionPointOrdinalsTogether() {
        MethodPatch patch = MethodPatch.builder()
            .modifyOrdinal(3)
            .modifyInjectionPointOrdinal(1)
            .build();
        MutableConfiguration clean = MutableConfiguration.create()
            .setAtData(AtData.builder("INVOKE").target("Lexample/Target;call()V").build());
        MutableConfiguration dirty = MutableConfiguration.create();

        patch.configCompleter().accept(clean, dirty);

        assertEquals(3, patch.configuration().getProperty(MixinKeys.ORDINAL).orElseThrow());
        assertEquals(1, dirty.getAtData().getOrdinal().orElseThrow());
        assertEquals("Lexample/Target;call()V", dirty.getAtData().getTargetOrThrow());
    }
}
