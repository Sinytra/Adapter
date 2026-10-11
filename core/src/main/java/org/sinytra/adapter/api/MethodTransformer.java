package org.sinytra.adapter.api;

import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public interface MethodTransformer {
    PatchResult apply(MixinContext context, Configuration config);
}
