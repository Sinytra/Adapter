package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.ann.ConstantData;
import org.sinytra.adapter.env.util.MixinAnnotationConstants;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.Keys;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;

public class InjectionTargetProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!dirty.hasProperty(Keys.TARGET_AT) && !dirty.hasProperty(Keys.TARGET_CONSTANT)) {
            return TxResult.PASS;
        }

        AnnotationHandle annotation = context.methodAnnotation();        
        if (dirty.getAtData() != null) {
            AnnotationHandle handle = annotation.getNestedOrAppend(MixinAnnotationConstants.PROPERTY_AT, MixinAnnotations.AT);
            dirty.getAtData().apply(handle);
        } else {
            annotation.removeValues(MixinAnnotationConstants.PROPERTY_AT);
        }

        if (dirty.hasProperty(Keys.TARGET_CONSTANT)) {
            AnnotationHandle handle = annotation.getNestedOrAppend(MixinAnnotationConstants.PROPERTY_CONSTANT, MixinAnnotations.CONSTANT);
            ConstantData cst = dirty.getProperty(Keys.TARGET_CONSTANT).orElseThrow();
            cst.apply(handle);
        } else {
            annotation.removeValues(MixinAnnotationConstants.PROPERTY_CONSTANT);
        }
        
        // methodContext.recordAudit(this, "Change injection point to %s", this.target);

        return TxResult.SUCCESS;
    }
}
