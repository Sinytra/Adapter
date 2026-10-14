package org.sinytra.adapter.pipeline.injector;

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

import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.CAPTURED_TARGET_ARGS;
import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.VALUE;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyConstantMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .require(MixinKeys.TARGET_CONSTANT)
        .keys(MixinKeys.SLICES)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_CONSTANT)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.CONSTANT,
        slot(SINGLE_ANY, VALUE),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS)
    );

    public ModifyConstantMixin() {
        super(TEMPLATE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        clean.setParameters(SIGNATURE.parse(context.methodNode()));
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        dirty.inheritProperyIfAbsent(MixinKeys.TARGET_CONSTANT);
        if (dirty.getTargetMethod() == null || !dirty.hasProperty(MixinKeys.TARGET_CONSTANT)) {
            return TxResult.FAIL;
        }
        dirty.inheritSignature();
        return TxResult.SUCCESS;
    }
}
