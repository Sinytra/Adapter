package org.sinytra.adapter.pipeline.rewrite;

import org.sinytra.adapter.model.config.Configuration;

public sealed interface Rewrite {
    record Replace(Configuration config) implements Rewrite {
    }

    record Delete(String reason) implements Rewrite {
    }
}
