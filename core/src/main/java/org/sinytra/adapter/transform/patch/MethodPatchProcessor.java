package org.sinytra.adapter.transform.patch;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.transform.MethodTransformer;

import java.util.List;

public record MethodPatchProcessor(List<MethodTransformer> transforms) implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        for (MethodTransformer transformer : this.transforms) {
            transformer.apply(context, recipe.clean());
        }
        return TxResult.SUCCESS;
    }
}
