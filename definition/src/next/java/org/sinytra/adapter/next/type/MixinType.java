package org.sinytra.adapter.next.type;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType<T extends MixinData> {
    T parse(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle);

    void preProcess(T mixin, MixinContext context, MutableConfiguration clean, Recipe recipe);

    void postProcess(T mixin, MixinContext context, MutableConfiguration dirty, Recipe recipe);
}
