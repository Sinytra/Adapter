package org.sinytra.adapter.patch.config;

public final class ConfigurationTemplates {
    public static final PropertyContainerTemplate MIXIN_BASE = PropertyContainerTemplate.builder()
        .require(Keys.MIXIN_TYPE, Keys.TARGET_CLASS, Keys.TARGET_METHOD, Keys.PARAMETERS, Keys.RETURN_TYPE)
        .keys(Keys.LOCALS, Keys.REQUIRE)
        .build();
    public static final PropertyContainerTemplate MIXIN_AT = MIXIN_BASE.extend()
        .require(Keys.TARGET_AT)
        .build();
    public static final PropertyContainerTemplate DELETE = PropertyContainerTemplate.builder()
        .require(Keys.DELETE)
        .build();

    private ConfigurationTemplates() {
    }
}
