package org.sinytra.adapter.next.mixin;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyExpressionValueMixin implements MixinType {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new ArbitraryInjectionPointSubResolver());
        resolvers
            .addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(true));

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, CAPTURED_PARAMS)));
        
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null || !dirty.getAtData().getValue().equals(AT_VAL_INVOKE)) {
            return TxResult.FAIL;
        }

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);
        if (targetDesc == null) {
            // Best effort
            dirty.inheritParameters();
            dirty.inheritReturnType();
            return TxResult.SUCCESS;
        }

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        Type modifyingType = Type.getReturnType(targetDesc.desc());

        MethodParameters params = MethodParameters.builder()
            .putType(SINGLE_ANY, modifyingType)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(modifyingType);
        
        return TxResult.SUCCESS;
    }
}
