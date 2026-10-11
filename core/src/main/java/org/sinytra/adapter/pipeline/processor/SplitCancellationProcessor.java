package org.sinytra.adapter.pipeline.processor;

import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.SpecialKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.target.SplitMethodCancellationHelper;

public class SplitCancellationProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!dirty.getProperty(SpecialKeys.SPLIT_CANCELLATION).orElse(false)) {
            return TxResult.PASS;
        }

        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) {
            return TxResult.PASS;
        }

        SplitMethodCancellationHelper.handle(context, recipe, dirtyTarget.methodNode());
        return TxResult.SUCCESS;
    }
}
