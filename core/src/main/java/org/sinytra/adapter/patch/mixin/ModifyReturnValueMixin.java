package org.sinytra.adapter.patch.mixin;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.special.InjectorOrdinalResolver;

// TODO
public class ModifyReturnValueMixin implements MixinType {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return null;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers
            .addBefore(InjectionPointResolver.class, new InjectorOrdinalResolver());
        return null;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        return null;
    }
}
