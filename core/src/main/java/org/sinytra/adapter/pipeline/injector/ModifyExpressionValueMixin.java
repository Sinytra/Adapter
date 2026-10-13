package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.MethodQualifier;
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
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.rewrite.InstanceofToModifyInstanceofValue;
import org.sinytra.adapter.pipeline.rewrite.RewriteResolver;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyExpressionValueMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.CALL_RETURN,
        slot(SINGLE_ANY, VALUE),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS),
        slot(LOCALS, KEEP)
    );

    public ModifyExpressionValueMixin() {
        super(TEMPLATE, MixinFlag.RETURN_TYPE_SENSITIVE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers
            .addBefore(InjectionPointResolver.class, new RewriteResolver(List.of(new InstanceofToModifyInstanceofValue())));

        clean.setParameters(SIGNATURE.parse(context.methodNode()));

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

        return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, null);
    }
}
