package org.sinytra.adapter.api;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.model.param.MethodParameters;

import java.util.function.UnaryOperator;

public interface MethodPatchBuilder {
    // Matching
    MethodPatchBuilder targetMixinType(String annotationDesc);

    MethodPatchBuilder targetClass(String... targets);

    MethodPatchBuilder targetMethod(String... targets);

    MethodPatchBuilder targetInjectionPoint(String target);

    MethodPatchBuilder targetInjectionPoint(String value, @Nullable String target);

    MethodPatchBuilder targetConstant(double doubleValue);

    // Interface only
    MethodPatchBuilder targetField(String target);

    // Modifications
    MethodPatchBuilder extractMixin(String targetClass);
    
    MethodPatchBuilder modifyTarget(String method);

    MethodPatchBuilder modifyInjectionPoint(String target);

    MethodPatchBuilder modifyInjectionPoint(String value, String target);

    MethodPatchBuilder replaceInjectionPoint(String value, String target);
    
    MethodPatchBuilder modifyParams(UnaryOperator<MethodParameters> op);

    // TODO This should be automatic
    MethodPatchBuilder modifyStatic(boolean isStatic);

    MethodPatchBuilder modifyMixinType(String newType);

    MethodPatchBuilder disable();

    MethodPatchBuilder transform(MethodTransformer transformer);

    MethodPatch build();
}
