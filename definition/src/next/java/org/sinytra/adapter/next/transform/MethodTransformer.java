package org.sinytra.adapter.next.transform;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.api.PatchResult;

public interface MethodTransformer {
    PatchResult apply(MixinContext context, Configuration config);
}
