package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;

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
