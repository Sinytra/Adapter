package org.sinytra.adapter.next.pipeline.processor.extract;

import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.operation.unit.ExtractMixin;

public class ExtractMixinProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Configuration dirty, Recipe recipe) {
        if (recipe.clean().getTargetClass().equals(dirty.getTargetClass())) return TxResult.PASS;

        Patch.Result result = new ExtractMixin(dirty.getTargetClass()).apply(context.legacy());
        if (result == Patch.Result.PASS && dirty.hasProperty(Configuration.SpecialKeys.EXTRACT_TARGET)) {
            MethodInsnNode minsn = dirty.getProperty(Configuration.SpecialKeys.EXTRACT_TARGET).orElseThrow();
            result = new MirrorableExtractMixin(dirty.getTargetClass(), minsn).apply(context.legacy());
        }
        if (result == Patch.Result.PASS) {
            return TxResult.FAIL;
        }

        return TxResult.SUCCESS;
    }
}
