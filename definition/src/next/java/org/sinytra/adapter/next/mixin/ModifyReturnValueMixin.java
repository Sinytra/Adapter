package org.sinytra.adapter.next.mixin;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.InjectorOrdinalResolver;

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
