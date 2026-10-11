package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.config.PropertyContainerTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public abstract class AbstractMixinType implements MixinType {
    private final PropertyContainerTemplate template;
    private final Set<MixinFlag> flags;

    protected AbstractMixinType(PropertyContainerTemplate template, MixinFlag... flags) {
        this.template = template;

        EnumSet<MixinFlag> set = EnumSet.noneOf(MixinFlag.class);
        set.addAll(Arrays.asList(flags));
        this.flags = Collections.unmodifiableSet(set);
    }

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return this.template;
    }

    @Override
    public Set<MixinFlag> getFlags() {
        return this.flags;
    }
}
