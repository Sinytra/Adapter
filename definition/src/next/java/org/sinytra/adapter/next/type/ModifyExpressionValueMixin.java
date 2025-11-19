package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.ModifyExpressionValueMixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class ModifyExpressionValueMixin implements MixinType<ModifyExpressionValueMixinData> {
    @Override
    public ModifyExpressionValueMixinData parse(MixinContext context, ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        return new ModifyExpressionValueMixinData(targetClass, targetMethod, atData);
    }

    @Override
    public void preProcess(ModifyExpressionValueMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, CAPTURED_PARAMS)));
    }

    @Override
    public void postProcess(ModifyExpressionValueMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null) return;

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null) return;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        Type modifyingType = Type.getReturnType(targetDesc.desc());

        MethodParameters params = MethodParameters.builder()
            .put(SINGLE_ANY, modifyingType)
            .put(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(modifyingType);
    }
}
