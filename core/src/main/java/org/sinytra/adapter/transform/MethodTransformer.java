package org.sinytra.adapter.transform;

import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.env.ctx.PatchResult;

public interface MethodTransformer {
    PatchResult apply(MixinContext context, Configuration config);
}
