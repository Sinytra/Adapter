package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.ConstantData;
import org.sinytra.adapter.next.env.ann.MixinAnnotationConstants;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Keys;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.MixinConstants;

public class InjectionTargetProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!dirty.hasProperty(Keys.TARGET_AT) && !dirty.hasProperty(Keys.TARGET_CONSTANT)) {
            return TxResult.PASS;
        }

        AnnotationHandle annotation = context.methodAnnotation();        
        if (dirty.getAtData() != null) {
            AnnotationHandle handle = annotation.getNestedOrAppend(MixinAnnotationConstants.PROPERTY_AT, MixinConstants.AT);
            dirty.getAtData().apply(handle);
        } else {
            annotation.removeValues(MixinAnnotationConstants.PROPERTY_AT);
        }

        if (dirty.hasProperty(Keys.TARGET_CONSTANT)) {
            AnnotationHandle handle = annotation.getNestedOrAppend(MixinAnnotationConstants.PROPERTY_CONSTANT, MixinConstants.CONSTANT);
            ConstantData cst = dirty.getProperty(Keys.TARGET_CONSTANT).orElseThrow();
            cst.apply(handle);
        } else {
            annotation.removeValues(MixinAnnotationConstants.PROPERTY_CONSTANT);
        }
        
        // methodContext.recordAudit(this, "Change injection point to %s", this.target);

        return TxResult.SUCCESS;
    }
}
