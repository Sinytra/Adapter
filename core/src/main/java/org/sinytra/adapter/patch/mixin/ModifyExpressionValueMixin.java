package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.ConfigurationTemplates;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.SINGLE_ANY;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;

public class ModifyExpressionValueMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public Set<MixinFlag> getFlags() {
        return EnumSet.of(MixinFlag.RETURN_TYPE_SENSITIVE);
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
