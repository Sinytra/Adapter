package org.sinytra.adapter.transform.patch;

import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.transform.MethodTransformer;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public interface MethodPatchBuilder {
    // Matching
    MethodPatchBuilder targetMixinType(String annotationDesc);

    MethodPatchBuilder targetClass(String... targets);

    MethodPatchBuilder targetMethod(String... targets);

    MethodPatchBuilder targetInjectionPoint(String target);

    MethodPatchBuilder targetInjectionPoint(String value, String target);

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

    MethodPatchBuilder divertRedirector(Consumer<InstructionAdapter> patcher);

    MethodPatchBuilder disable();

    MethodPatchBuilder transform(MethodTransformer transformer);

    MethodPatch build();
}
