package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

public class InjectionTargetProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getAtData() == null) return TxResult.FAIL;

        AnnotationHandle handle = context.injectionPointAnnotation();
        dirty.getAtData().apply(handle);

        return TxResult.SUCCESS;
    }
}
