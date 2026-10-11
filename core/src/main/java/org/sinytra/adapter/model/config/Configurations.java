package org.sinytra.adapter.model.config;

import org.sinytra.adapter.model.ann.AtData;

public class Configurations {
    public static final Configuration DELETE = MutableConfiguration.create(ConfigurationTemplates.DELETE)
        .setShouldDelete(true);

    public static MutableConfiguration changeMixinType(Configuration dirty, String mixinType, AtData at) {
        return dirty.copyClean()
            .setMixinType(mixinType)
            .inheritTargetClass()
            .inheritTargetMethod()
            .setAtData(at)
            .inheritSignature();
    }
}
