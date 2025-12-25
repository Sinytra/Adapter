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

    public void addSubResolver(SubResolver subResolver) {
        this.subResolvers.add(subResolver);
    }

    protected abstract boolean canApply(MixinData mixin, Configuration clean, Configuration dirty);

    @Nullable
    protected Configuration tryReuse(MixinContext context, Configuration clean, Configuration dirty) {
        return null;
    }

    @Nullable
    protected Configuration useFallback(MixinContext context, Configuration clean, Configuration dirty) {
        return null;
    }

    @Override
    public ResolutionResult resolve(MixinData mixin, MixinContext context, Configuration clean, Configuration dirty, Recipe recipe) {
        if (!canApply(mixin, clean, dirty)) {
            return ResolutionResult.pass();
        }

        Configuration resused = tryReuse(context, clean, dirty); 
        if (resused != null) {
            return ResolutionResult.success(resused);
        }

        for (SubResolver subResolver : this.subResolvers) {
            Configuration result = subResolver.resolve(mixin, context, clean, dirty, recipe);
            if (result != null) {
                return ResolutionResult.success(result);
            }
        }

        Configuration fallback = useFallback(context, clean, dirty); 
        if (fallback != null) {
            return ResolutionResult.success(fallback);
        }

        return ResolutionResult.fail();
    }
}
