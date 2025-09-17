package org.sinytra.adapter.next.pipeline.processor;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.operation.param.ParamTransformTarget;

import java.util.List;

public class MixinMethodParametersProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Recipe recipe) {
        if (recipe.dirty().getParameters() == null)
            return TxResult.FAIL;

        List<Type> cleanParams = recipe.clean().getParameters().merge();
        List<Type> dirtyParams = recipe.dirty().getParameters().merge();
        ParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(cleanParams, dirtyParams);

        if (!diff.isEmpty()) {
            Patch.Result result = diff.asParameterTransformer(ParamTransformTarget.ALL, false)
                .apply(context.legacy());
            return result == Patch.Result.PASS ? TxResult.FAIL : TxResult.SUCCESS;
        }

        return TxResult.PASS;
    }
}
