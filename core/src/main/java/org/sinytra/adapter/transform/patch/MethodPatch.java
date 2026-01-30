package org.sinytra.adapter.transform.patch;

import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.transform.MethodTransformer;

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
