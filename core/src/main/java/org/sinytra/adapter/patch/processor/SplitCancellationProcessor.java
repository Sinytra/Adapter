package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.resolver.target.SplitMethodCancellationHelper;

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
