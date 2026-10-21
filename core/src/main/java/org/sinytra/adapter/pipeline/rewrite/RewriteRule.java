package org.sinytra.adapter.pipeline.rewrite;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public interface RewriteRule {
    @Nullable
    Rewrite apply(MixinContext context, Recipe recipe);
}
