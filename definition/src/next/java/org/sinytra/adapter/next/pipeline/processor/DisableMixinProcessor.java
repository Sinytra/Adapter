package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;

public class DisableMixinProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Configuration dirty, Recipe recipe) {
        if (!dirty.shouldDelete()) {
            return TxResult.PASS;
        }
        
        context.patchContext().postApply(() -> context.classNode().methods.remove(context.methodNode()));
        
        return TxResult.FINALIZE;
    }
}
