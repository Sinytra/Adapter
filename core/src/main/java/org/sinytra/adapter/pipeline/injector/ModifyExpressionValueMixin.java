package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.special.ResolverSyntheticInstanceof;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyExpressionValueMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    public ModifyExpressionValueMixin() {
        super(TEMPLATE, MixinFlag.RETURN_TYPE_SENSITIVE);
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new ArbitraryInjectionPointSubResolver());
        resolvers
            .addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(true));

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, CAPTURED_PARAMS, LOCALS)));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null) {
            return TxResult.FAIL;
        }
        // Best effort for non-INVOKE
        if (!dirty.getAtData().getValue().equals(AT_VAL_INVOKE)) {
            dirty.inheritSignature();
            return TxResult.SUCCESS;
        }

        MethodQualifier targetDesc = dirty.getAtData().getTargetQualifier().orElse(null);
        if (targetDesc == null || targetDesc.desc() == null) {
            // Best effort
            dirty.inheritSignature();
            return TxResult.SUCCESS;
        }

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        Type modifyingType = Type.getReturnType(targetDesc.desc());

        MethodParameters params = clean.getParameters().mutableCopy()
            .putType(SINGLE_ANY, modifyingType)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(modifyingType);

        return TxResult.SUCCESS;
    }
}
