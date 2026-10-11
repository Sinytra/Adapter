package org.sinytra.adapter.pipeline.resolver.target;

import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.CompoundResolver;

public class TargetMethodResolver extends CompoundResolver {
    public TargetMethodResolver() {
        addSubResolver(new SplitTargetMethodSubResolver());
        addSubResolver(TargetMethodSubResolvers.CHANGED_LAMBDA_INDEX);
        addSubResolver(TargetMethodSubResolvers.CHANGED_METHOD_PARAMS);
        addSubResolver(TargetMethodSubResolvers.MOVED_INTO_LAMBDA);
    }

    @Override
    protected boolean canApply(Recipe recipe) {
        return recipe.dirty().getTargetMethod() == null;
    }

    @Override
    protected Configuration tryReuse(MixinContext context, Recipe recipe) {
        TargetPair target = recipe.getNewCleanTarget();
        if (target != null && context.methods().hasInjectionTargetInsns(target)) {
            return recipe.dirty().copyClean()
                .inheritTargetMethod();
        }
        return null;
    }

    @Override
    protected Configuration useFallback(MixinContext context, Recipe recipe) {
        TargetPair target = recipe.getNewCleanTarget();
        if (target != null) {
            return recipe.dirty().copyClean()
                .inheritTargetMethod();
        }
        return null;
    }
}
