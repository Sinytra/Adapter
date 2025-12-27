package org.sinytra.adapter.next.env;

import org.sinytra.adapter.next.pipeline.config.Configuration.Keys;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;

public final class ConfigurationTemplates {
    public static final PropertyContainerTemplate MIXIN_BASE = PropertyContainerTemplate.builder()
        .require(Keys.MIXIN_TYPE, Keys.TARGET_CLASS, Keys.TARGET_METHOD, Keys.PARAMETERS, Keys.RETURN_TYPE)
        .build();
    public static final PropertyContainerTemplate MIXIN_AT = MIXIN_BASE.extend()
        .require(Keys.TARGET_AT)
        .build();

    private ConfigurationTemplates() {
    }
}
