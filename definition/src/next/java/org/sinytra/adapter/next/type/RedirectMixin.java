package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.MixinAnnotationConstants;
import org.sinytra.adapter.next.env.ann.RedirectMixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.ParameterUsageProcessor;
import org.sinytra.adapter.next.pipeline.processor.ParametersProcessor;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.METHOD_PARAMS;

public class RedirectMixin implements MixinType<RedirectMixinData> {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public RedirectMixinData parse(MixinContext context, ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        return new RedirectMixinData(targetClass, targetMethod, atData);
    }

    @Override
    public void preProcess(RedirectMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        recipe.resolvers().addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(false));
        recipe.processors().addAfter(ParametersProcessor.class, new ParameterUsageProcessor());

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
    }

    @Override
    public void postProcess(RedirectMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return;

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null) return;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        List<Type> callTypes = MethodParameters.getParameterTypes(targetDesc.desc());

        MethodParameters params = MethodParameters.builder()
            .put(METHOD_PARAMS, callTypes)
            .put(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(targetDesc.desc()));
    }
}
