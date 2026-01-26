package org.sinytra.adapter.next.mixin;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType {
    PropertyContainerTemplate getConfigurationTemplate();

    TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors);

    TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe);
}
