package org.sinytra.adapter.next.pipeline.resolver;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;

public interface SubResolver {
    @Nullable
    Configuration resolve(MixinData mixin, MixinContext context, Recipe recipe);
}
