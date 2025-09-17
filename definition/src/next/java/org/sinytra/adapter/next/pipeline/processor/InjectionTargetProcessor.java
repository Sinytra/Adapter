package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

public class InjectionTargetProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Recipe recipe) {
        if (recipe.dirty().getAtData() == null) {
            return TxResult.FAIL;
        }

        // TODO Add if missing
        AnnotationHandle handle = context.injectionPointAnnotation();
        if (handle == null) {
            return TxResult.FAIL;
        }

        recipe.dirty().getAtData().apply(handle);

        return TxResult.SUCCESS;
    }
}
