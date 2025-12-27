package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.ModifyExpressionValueMixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class ModifyExpressionValueMixin implements MixinType<ModifyExpressionValueMixinData> {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public ModifyExpressionValueMixinData parse(MixinContext context, ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        return new ModifyExpressionValueMixinData(targetClass, targetMethod, atData);
    }

    @Override
    public void preProcess(ModifyExpressionValueMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        recipe.resolvers().getOrThrow(InjectionPointResolver.class).addSubResolver(new ArbitraryInjectionPointSubResolver());
        recipe.resolvers().addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(true));

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, CAPTURED_PARAMS)));
    }

    @Override
    public void postProcess(ModifyExpressionValueMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null || !dirty.getAtData().getValue().equals(AT_VAL_INVOKE)) return;

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (targetDesc == null) {
            // Best effort
            dirty.inheritParameters();
            dirty.inheritReturnType();
            return;
        }

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
