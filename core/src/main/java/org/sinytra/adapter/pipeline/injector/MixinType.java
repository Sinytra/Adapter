package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;

import java.util.EnumSet;
import java.util.Set;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType {
    PropertyContainerTemplate getConfigurationTemplate();

    HandlerSignature signature();

    default Set<MixinFlag> getFlags() {
        return EnumSet.noneOf(MixinFlag.class);
    }

    default boolean canInject(MixinContext context, Configuration config) {
        return true;
    }

    TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors);

    TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe);
}
