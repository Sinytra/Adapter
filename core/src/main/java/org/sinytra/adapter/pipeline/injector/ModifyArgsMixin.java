package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.MethodQualifier;
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

import java.util.Objects;

import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.CAPTURED_TARGET_ARGS;
import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.KEEP;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyArgsMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.VOID,
        slot(SINGLE_ANY, KEEP),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS)
    );

    public ModifyArgsMixin() {
        super(TEMPLATE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public RelocationPlan.Passthrough passthrough(MixinContext context, Configuration config) {
        return RelocationPlan.Passthrough.SKIP;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        clean.setParameters(SIGNATURE.parse(context.methodNode()));
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        dirty.inheritProperyIfAbsent(MixinKeys.ORDINAL);
        AtData cleanAt = clean.getAtData();
        AtData dirtyAt = dirty.getAtData();
        if (cleanAt == null || dirtyAt == null) {
            return TxResult.FAIL;
        }
        String cleanDesc = cleanAt.getTargetQualifier().map(MethodQualifier::desc).orElse(null);
        String dirtyDesc = dirtyAt.getTargetQualifier().map(MethodQualifier::desc).orElse(null);
        if (cleanDesc == null || !Objects.equals(cleanDesc, dirtyDesc)) {
            return TxResult.FAIL;
        }
        dirty.inheritSignature();
        return TxResult.SUCCESS;
    }
}
