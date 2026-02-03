package org.sinytra.adapter.patch.resolver;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;

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

    protected abstract boolean canApply(Recipe recipe);

    @Nullable
    protected Configuration tryReuse(MixinContext context, Recipe recipe) {
        return null;
    }

    @Nullable
    protected Configuration useFallback(MixinContext context, Recipe recipe) {
        return null;
    }

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        if (!canApply(recipe)) {
            return ResolutionResult.pass();
        }

        Configuration resused = tryReuse(context, recipe);
        if (resused != null) {
            return ResolutionResult.success(resused);
        }

        for (SubResolver subResolver : this.subResolvers) {
            context.pushAudit(subResolver);
            Configuration result = subResolver.resolve(context, recipe);
            context.popAudit();
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
