package org.sinytra.adapter.next.pipeline.resolver;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;

import java.util.ArrayList;
import java.util.List;

public abstract class CompoundResolver implements Resolver {
    protected final List<SubResolver> subResolvers = new ArrayList<>();

    public CompoundResolver addSubResolverFirst(SubResolver subResolver) {
        this.subResolvers.addFirst(subResolver);
        return this;
    }

    public CompoundResolver addSubResolver(SubResolver subResolver) {
        this.subResolvers.add(subResolver);
        return this;
    }

    protected abstract boolean canApply(MixinData mixin, Recipe recipe);

    @Nullable
    protected Configuration tryReuse(MixinContext context, Recipe recipe) {
        return null;
    }

    @Nullable
    protected Configuration useFallback(MixinContext context, Recipe recipe) {
        return null;
    }

    @Override
    public ResolutionResult resolve(MixinData mixin, MixinContext context, Recipe recipe) {
        if (!canApply(mixin, recipe)) {
            return ResolutionResult.pass();
        }

        Configuration resused = tryReuse(context, recipe);
        if (resused != null) {
            return ResolutionResult.success(resused);
        }

        for (SubResolver subResolver : this.subResolvers) {
            Configuration result = subResolver.resolve(mixin, context, recipe);
            if (result != null) {
                return ResolutionResult.success(result);
            }
        }

        Configuration fallback = useFallback(context, recipe);
        if (fallback != null) {
            return ResolutionResult.success(fallback);
        }

        return ResolutionResult.fail();
    }
}
