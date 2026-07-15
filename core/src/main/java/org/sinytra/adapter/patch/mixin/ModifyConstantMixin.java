package org.sinytra.adapter.patch.mixin;

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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyConstantMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .require(MixinKeys.TARGET_CONSTANT)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_CONSTANT)
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
        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, CAPTURED_PARAMS)));
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) {
            return TxResult.FAIL;
        }

        dirty.inheritProperyIfAbsent(MixinKeys.TARGET_CONSTANT);
        dirty.inheritParameters();
        dirty.inheritReturnType();
        return TxResult.SUCCESS;
    }
}
