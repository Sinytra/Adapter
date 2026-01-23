package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;

public class DisableMixinProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!dirty.shouldDelete()) {
            return TxResult.PASS;
        }
        
        // methodContext.recordAudit(this, "Remove mixin method");
        context.patchContext().postApply(() -> context.classNode().methods.remove(context.methodNode()));
        
        return TxResult.FINALIZE;
    }
}
