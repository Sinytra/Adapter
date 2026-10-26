package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ParamSource;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.rewrite.InstanceofToConstant;
import org.sinytra.adapter.pipeline.resolver.rewrite.RewriteResolver;

import java.util.List;


import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class WrapOperationMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .requireOne(MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.CALL_RETURN,
        slot(METHOD_PARAMS, CALL_OPERANDS),
        slot(MethodParameters.ParamGroup.OPERATION, ParamSource.OPERATION),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS),
        slot(LOCALS, KEEP)
    );

    public WrapOperationMixin() {
        super(TEMPLATE, MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public RelocationPlan.Passthrough passthrough(MixinContext context, Configuration config) {
        boolean hasOperation = Parameters.getParameterTypes(context.methodNode().desc).contains(TypeConstants.OPERATION_TYPE);
        return hasOperation ? RelocationPlan.Passthrough.CALL_ORIGINAL : null;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        resolvers.addBefore(InjectionPointResolver.class, new RewriteResolver(List.of(new InstanceofToConstant())));

        clean.setParameters(SIGNATURE.parse(context.methodNode()));
        
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;
        if (dirty.getAtData() == null) return TxResult.PASS;

        return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, null);
    }
}
