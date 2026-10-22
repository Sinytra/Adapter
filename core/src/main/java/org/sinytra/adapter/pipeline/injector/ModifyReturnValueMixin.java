package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolvers;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_RETURN;
import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class ModifyReturnValueMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.TARGET_RETURN,
        slot(SINGLE_ANY, VALUE),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS),
        slot(LOCALS, KEEP)
    );

    public ModifyReturnValueMixin() {
        super(TEMPLATE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public RelocationPlan.Passthrough passthrough(MixinContext context, Configuration config) {
        return RelocationPlan.Passthrough.KEEP_VALUE;
    }

    @Override
    public boolean canInject(MixinContext context, Configuration config) {
        AtData at = config.getAtData();
        MethodQualifier target = config.getTargetMethod();
        if (at == null || !AT_VAL_RETURN.equals(at.getValue()) || at.getProperty(AtData.Keys.ORDINAL).isEmpty() || target == null) {
            return true;
        }

        Targets targets = context.targets(target);
        return targets == null || context.dataFlow(targets.clean()).findReturns().size() == context.dataFlow(targets.dirty()).findReturns().size();
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        clean.setParameters(SIGNATURE.parse(context.methodNode()));

        return TxResult.SUCCESS;
    }

    // TODO Cleanup parameter processing in mixin types
    // 1. Method params vs Captured params + reconstruction
    // 2. Locals
    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        MethodQualifier target = dirty.getTargetMethod();
        if (target == null || target.desc() == null) {
            return TxResult.FAIL;
        }

        return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, null);
    }
}
