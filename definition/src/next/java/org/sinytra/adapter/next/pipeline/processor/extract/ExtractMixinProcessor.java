package org.sinytra.adapter.next.pipeline.processor.extract;

import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.SpecialKeys;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.patch.api.PatchResult;

public class ExtractMixinProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (recipe.clean().getTargetClass().equals(dirty.getTargetClass())) return TxResult.PASS;

        PatchResult result = new ExtractMixin(dirty.getTargetClass()).apply(context.legacy());
        if (result == PatchResult.PASS && dirty.hasProperty(SpecialKeys.EXTRACT_TARGET)) {
            MethodInsnNode minsn = dirty.getProperty(SpecialKeys.EXTRACT_TARGET).orElseThrow();
            result = new MirrorableExtractMixin(dirty.getTargetClass(), minsn).apply(context.legacy());
        }
        if (result == PatchResult.PASS) {
            return TxResult.FAIL;
        }

        return TxResult.SUCCESS;
    }
}
