package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.MixinAnnotationConstants;
import org.sinytra.adapter.next.env.ann.RedirectMixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.ParamDiffResolver;
import org.sinytra.adapter.next.env.param.ParamDiffResolver.ParamEvalResult;
import org.sinytra.adapter.next.env.param.ParamDiffResolver.ParamState;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class RedirectMixin implements MixinType<RedirectMixinData> {
    @Override
    public RedirectMixinData parse(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        return new RedirectMixinData(targetClass, targetMethod, atData);
    }

    @Override
    public void preProcess(RedirectMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        if (clean.getAtData() == null || !MixinAnnotationConstants.AT_VAL_INVOKE.equals(clean.getAtData().getValue()))
            return;

        MethodQualifier targetDesc = clean.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null) return;

        List<Type> callTypes = MethodParameters.getParameterTypes(targetDesc.desc());
        List<Type> methodTypes = MethodParameters.getParameterTypes(context.methodNode().desc);
        List<Type> capturedMethodParams = new ArrayList<>(methodTypes.subList(callTypes.size(), methodTypes.size()));

        MethodParameters params = MethodParameters.builder()
            .put(METHOD_PARAMS, callTypes)
            .put(CAPTURED_PARAMS, capturedMethodParams)
            .build();

        clean.setParameters(params);
        clean.setReturnType(Type.getReturnType(targetDesc.desc()));
    }

    @Override
    public void postProcess(RedirectMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return;

        MethodQualifier targetDesc = recipe.clean().getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null) return;
        
        List<Type> cleanCaptured = recipe.clean().getParameters().get(CAPTURED_PARAMS);
        List<Type> dirtyCaptured = new ArrayList<>();

        // Evaluate parameter difference, capture additional params when necessary
        if (!cleanCaptured.isEmpty()) {
            // TODO Clean up boilerplate
            MethodNode cleanTarget = context.methods().findMethod(context.cleanLookup(), recipe.clean().getTargetMethod()).methodNode();
            MethodNode dirtyTarget = context.methods().findMethod(context.dirtyLookup(), dirty.getTargetMethod()).methodNode();
            LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.compareMethodParameters(cleanTarget, dirtyTarget);

            List<Type> cleanTargetParams = MethodParameters.getParameterTypes(cleanTarget.desc);
            ParamEvalResult evalResult = ParamDiffResolver.resolve(cleanTargetParams, diff);
            
            int maxIndex = cleanCaptured.stream()
                .map(evalResult::getUpdated)
                .filter(Objects::nonNull)
                .mapToInt(ParamState::dirtyIndex)
                .max()
                .orElse(-1);
            
            if (maxIndex != -1) {
                List<Type> dirtyTargetParams = MethodParameters.getParameterTypes(dirtyTarget.desc);
                dirtyCaptured = dirtyTargetParams.subList(0, maxIndex + 1);
            }
        }

        List<Type> callTypes = MethodParameters.getParameterTypes(targetDesc.desc());

        MethodParameters params = MethodParameters.builder()
            .put(METHOD_PARAMS, callTypes)
            .put(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(targetDesc.desc()));
    }
}
