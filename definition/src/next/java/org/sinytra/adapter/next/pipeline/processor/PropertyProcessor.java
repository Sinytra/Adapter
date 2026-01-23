package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

import static org.sinytra.adapter.next.pipeline.config.Keys.ORDINAL;
import static org.sinytra.adapter.next.pipeline.config.Keys.SLICE;

public class PropertyProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getAtData() == null) return TxResult.FAIL;

        // TODO Auto append all props
        AnnotationHandle handle = context.methodAnnotation();
        dirty.getProperty(ORDINAL)
            .ifPresent(o -> handle.setOrAppendNonNull(ORDINAL.name(), o));

        dirty.getProperty(SLICE)
            .ifPresent(s -> handle.setOrAppendNonNull(SLICE.name(), s.toAnnotationNode()));

        return TxResult.SUCCESS;
    }
}
