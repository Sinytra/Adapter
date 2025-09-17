package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;

import java.util.List;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_METHOD;

public class TargetMethodProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;

        context.methodAnnotation()
            .setOrAppendNonNull(AT_METHOD, List.of(dirty.getTargetMethod().asDescriptor()));

        return TxResult.SUCCESS;
    }
}
