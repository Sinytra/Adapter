package org.sinytra.adapter.patch.config;

import org.sinytra.adapter.env.ann.AtData;

public class Configurations {
    public static final Configuration DELETE = MutableConfiguration.create(ConfigurationTemplates.DELETE)
        .setShouldDelete(true);

    public static MutableConfiguration changeMixinType(Configuration dirty, String mixinType, AtData at) {
        return dirty.copyClean()
            .setMixinType(mixinType)
            .inheritTargetClass()
            .inheritTargetMethod()
            .setAtData(at)
            .inheritParameters()
            .inheritReturnType();
    }
}
