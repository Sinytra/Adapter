package org.sinytra.adapter.patch.mixin;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;

import java.util.EnumSet;
import java.util.Set;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType {
    PropertyContainerTemplate getConfigurationTemplate();

    default Set<MixinFlag> getFlags() {
        return EnumSet.noneOf(MixinFlag.class);
    }

    TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors);

    TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe);
}
