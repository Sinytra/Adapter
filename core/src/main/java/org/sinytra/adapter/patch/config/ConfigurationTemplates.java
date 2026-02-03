package org.sinytra.adapter.patch.config;

import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;

public final class ConfigurationTemplates {
    public static final PropertyContainerTemplate MIXIN_BASE = PropertyContainerTemplate.builder()
        .require(ControlKeys.MIXIN_TYPE, ControlKeys.TARGET_CLASS, MixinKeys.TARGET_METHOD, ControlKeys.PARAMETERS, ControlKeys.RETURN_TYPE)
        .keys(MixinKeys.LOCALS, MixinKeys.REQUIRE)
        .build();
    public static final PropertyContainerTemplate MIXIN_AT = MIXIN_BASE.extend()
        .require(MixinKeys.TARGET_AT)
        .build();
    public static final PropertyContainerTemplate DELETE = PropertyContainerTemplate.builder()
        .require(ControlKeys.DELETE)
        .build();

    private ConfigurationTemplates() {
    }
}
