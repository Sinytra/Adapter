package org.sinytra.adapter.patch.mixin;

import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType {
    PropertyContainerTemplate getConfigurationTemplate();

    TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors);

    TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe);
}
