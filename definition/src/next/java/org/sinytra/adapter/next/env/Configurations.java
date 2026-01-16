package org.sinytra.adapter.next.env;

import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;

public class Configurations {
    public static final Configuration DELETE = MutableConfiguration.create(ConfigurationTemplates.DELETE)
        .setShouldDelete(true);
}
