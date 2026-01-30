package org.sinytra.adapter.transform.patch;

import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.transform.MethodTransformer;

import java.util.List;
import java.util.function.BiConsumer;

public class MethodPatchImpl implements MethodPatch {
    private final ConfigurationMatcher matcher;
    private final Configuration config;
    private final BiConsumer<Configuration, MutableConfiguration> configCompleter;
    private final List<MethodTransformer> transforms;

    public MethodPatchImpl(ConfigurationMatcher matcher, Configuration config, BiConsumer<Configuration, MutableConfiguration> configCompleter, List<MethodTransformer> transforms) {
        this.matcher = matcher;
        this.config = config;
        this.configCompleter = configCompleter;
        this.transforms = transforms;
    }

    @Override
    public ConfigurationMatcher matcher() {
        return this.matcher;
    }

    @Override
    public Configuration configuration() {
        return this.config;
    }

    @Override
    public BiConsumer<Configuration, MutableConfiguration> configCompleter() {
        return this.configCompleter;
    }

    @Override
    public List<MethodTransformer> transforms() {
        return this.transforms;
    }
}
