package org.sinytra.adapter.patch.resolver.target;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.resolver.CompoundResolver;
import org.sinytra.adapter.util.MethodQualifier;

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
