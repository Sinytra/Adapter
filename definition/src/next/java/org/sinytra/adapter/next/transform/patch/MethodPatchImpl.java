package org.sinytra.adapter.next.transform.patch;

import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.transform.MethodTransformer;

import java.util.List;

public class MethodPatchImpl implements MethodPatch {
    private final ConfigurationMatcher matcher;
    private final Configuration config;
    private final List<MethodTransformer> transforms;

    public MethodPatchImpl(ConfigurationMatcher matcher, Configuration config, List<MethodTransformer> transforms) {
        this.matcher = matcher;
        this.config = config;
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
    public List<MethodTransformer> transforms() {
        return this.transforms;
    }
}
