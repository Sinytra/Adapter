package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.PROPERTY_ORDINAL;

public class PropertyProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getAtData() == null) return TxResult.FAIL;

        AnnotationHandle handle = context.methodAnnotation();
        dirty.<Integer>getProperty(PROPERTY_ORDINAL)
            .ifPresent(o -> handle.setOrAppendNonNull(PROPERTY_ORDINAL, o));

        return TxResult.SUCCESS;
    }
}
