package org.sinytra.adapter.api;

import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.pipeline.manual.ConfigurationMatcher;
import org.sinytra.adapter.pipeline.manual.MethodPatchBuilderImpl;

import java.util.List;
import java.util.function.BiConsumer;

public interface MethodPatch {
    ConfigurationMatcher matcher();

    Configuration configuration();

    BiConsumer<Configuration, MutableConfiguration> configCompleter();

    List<MethodTransformer> transforms();

    static MethodPatchBuilder builder() {
        return new MethodPatchBuilderImpl();
    }
}
