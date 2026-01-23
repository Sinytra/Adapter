package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinAnnotationConstants;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.processor.redirect.ParameterUsageProcessor;
import org.sinytra.adapter.next.pipeline.processor.ParametersProcessor;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.next.pipeline.processor.redirect.DivertRedirectProcessor;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.METHOD_PARAMS;

public class RedirectMixin implements MixinType {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.
            addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(false));
        processors
            .addAfter(ParametersProcessor.class, new DivertRedirectProcessor())
            .addAfter(ParametersProcessor.class, new ParameterUsageProcessor());

        if (clean.getAtData() == null || !MixinAnnotationConstants.AT_VAL_INVOKE.equals(clean.getAtData().getValue()))
            return TxResult.FAIL;

        MethodQualifier targetDesc = clean.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null)
            return TxResult.FAIL;

        List<Type> callTypes = Parameters.getParameterTypes(targetDesc.desc());
        List<Type> methodTypes = Parameters.getParameterTypes(context.methodNode().desc);
        List<Type> capturedMethodParams = new ArrayList<>(methodTypes.subList(callTypes.size(), methodTypes.size()));

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, capturedMethodParams)
            .build();

        clean.setParameters(params);
        
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null)
            return TxResult.FAIL;

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null)
            return TxResult.FAIL;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        List<Type> callTypes = Parameters.getParameterTypes(targetDesc.desc());

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(targetDesc.desc()));

        return TxResult.SUCCESS;
    }
}
