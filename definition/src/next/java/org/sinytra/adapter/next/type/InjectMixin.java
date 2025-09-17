package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.InjectMixinData;
import org.sinytra.adapter.next.env.ann.SliceData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class InjectMixin implements MixinType<InjectMixinData> {
    @Override
    public InjectMixinData parse(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        List<SliceData> slice = handle.getNestedList("slice").stream().map(SliceData::parse).toList();
        return new InjectMixinData(targetClass, targetMethod, atData, slice);
    }

    @Override
    public void preProcess(InjectMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        clean.setParameters(MethodParameters.create(context.methodNode().desc, List.of(METHOD_PARAMS, CI_CIR, LOCALS)));
        clean.setReturnType(Type.VOID_TYPE);

        if (!mixin.getSlice().isEmpty()) {
            clean.setProperty("slice", mixin.getSlice());
        }
    }

    @Override
    public void postProcess(InjectMixinData mixin, MixinContext context, MutableConfiguration dirty, Recipe recipe) {
        dirty.setReturnType(Type.VOID_TYPE);

        Configuration clean = recipe.clean();
        if (dirty.getTargetMethod() != null && !dirty.getTargetMethod().desc().equals(clean.getTargetMethod().desc())) {
            List<Type> cleanParams = clean.getParameters().get(METHOD_PARAMS);
            if (!cleanParams.isEmpty()) {
                MethodParameters newParams = MethodParameters.create(context.methodNode().desc, List.of(METHOD_PARAMS, CI_CIR, LOCALS));
                List<Type> dirtyTargetMethodParams = MethodParameters.getParameterTypes(dirty.getTargetMethod().desc());
                newParams.set(METHOD_PARAMS, dirtyTargetMethodParams);

                dirty.setParameters(newParams);
            }
        }
    }
}
