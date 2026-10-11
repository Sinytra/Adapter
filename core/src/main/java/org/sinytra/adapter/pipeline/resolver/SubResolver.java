package org.sinytra.adapter.pipeline.resolver;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public interface SubResolver {
    @Nullable
    Configuration resolve(MixinContext context, Recipe recipe);
}
