package org.sinytra.adapter.next.pipeline.resolver;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;

public interface Resolver {
    TxResult resolve(MixinData mixin, MixinContext context, MutableConfiguration dirty, Recipe recipe);
}
