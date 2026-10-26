package org.sinytra.adapter.pipeline.manual;

import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processor;

import java.util.List;

public record MethodPatchProcessor(List<MethodTransformer> transforms) implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        for (MethodTransformer transformer : this.transforms) {
            transformer.apply(context);
        }
        return TxResult.SUCCESS;
    }
}
