package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;

import java.util.List;

public class TargetMethodProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Recipe recipe) {
        context.getMethodContext().methodAnnotation().getValue("method")
            .orElseThrow()
            .set(List.of(recipe.dirty().getTargetMethod().asDescriptor()));
        return TxResult.PASS;
    }
}
