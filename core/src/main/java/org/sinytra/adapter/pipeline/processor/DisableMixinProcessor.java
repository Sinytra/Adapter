package org.sinytra.adapter.pipeline.processor;

import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public class DisableMixinProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!dirty.shouldDelete()) {
            return TxResult.PASS;
        }

        context.recordCtxAudit("Remove mixin method");
        context.patchContext().postApply(() -> context.classNode().methods.remove(context.methodNode()));

        return TxResult.FINALIZE;
    }
}
