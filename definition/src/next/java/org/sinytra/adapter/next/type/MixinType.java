package org.sinytra.adapter.next.type;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.config.PropertyKey;

import java.util.Set;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType<T extends MixinData> {
    PropertyContainerTemplate getConfigurationTemplate();

    default Set<PropertyKey<?>> requestProperties() {
        return Set.of();
    }

    TxResult preProcess(T mixin, MixinContext context, MutableConfiguration clean, Recipe recipe);

    TxResult postProcess(T mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe);
}
