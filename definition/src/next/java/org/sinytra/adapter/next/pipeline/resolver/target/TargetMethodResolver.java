package org.sinytra.adapter.next.pipeline.resolver.target;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.resolver.CompoundResolver;
import org.sinytra.adapter.patch.api.TargetPair;
import org.sinytra.adapter.patch.util.MethodQualifier;

public class TargetMethodResolver extends CompoundResolver {
    public TargetMethodResolver() {
        addSubResolver(new SplitTargetMethodSubResolver());
        addSubResolver(TargetMethodSubResolvers.CHANGED_METHOD_PARAMS);
        addSubResolver(TargetMethodSubResolvers.MOVED_INTO_LAMBDA);
    }

    @Override
    protected boolean canApply(Recipe recipe) {
        return recipe.dirty().getTargetMethod() == null;
    }

    @Override
    protected Configuration tryReuse(MixinContext context, Recipe recipe) {
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();
        TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), cleanQualifier);
        if (target != null && context.methods().hasInjectionTargetInsns(target)) {
            return recipe.dirty().copyClean()
                .inheritTargetMethod();
        }
        return null;
    }

    @Override
    protected Configuration useFallback(MixinContext context, Recipe recipe) {
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();
        TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), cleanQualifier);
        if (target != null) {
            return recipe.dirty().copyClean()
                .inheritTargetMethod();
        }
        return null;
    }
}
