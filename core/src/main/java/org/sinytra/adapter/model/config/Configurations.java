package org.sinytra.adapter.model.config;

import org.sinytra.adapter.model.ann.AtData;

public class Configurations {
    public static final Configuration DELETE = MutableConfiguration.create(ConfigurationTemplates.DELETE)
        .setShouldDelete(true);

    public static MutableConfiguration changeMixinType(Configuration dirty, String mixinType, AtData at) {
        return changeMixinType(dirty.copyClean(), mixinType, at);
    }

    public static MutableConfiguration changeMixinType(Configuration dirty, String mixinType, PropertyContainerTemplate template, AtData at) {
        return changeMixinType(dirty.copyClean(template), mixinType, at);
    }

    private static MutableConfiguration changeMixinType(MutableConfiguration config, String mixinType, AtData at) {
        return config
            .setMixinType(mixinType)
            .inheritTargetClass()
            .inheritTargetMethod()
            .setAtData(at)
            .inheritSignature();
    }
}
