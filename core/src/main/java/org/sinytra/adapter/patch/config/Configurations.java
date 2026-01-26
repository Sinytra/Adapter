package org.sinytra.adapter.patch.config;

public class Configurations {
    public static final Configuration DELETE = MutableConfiguration.create(ConfigurationTemplates.DELETE)
        .setShouldDelete(true);
}
