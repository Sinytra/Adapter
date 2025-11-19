package org.sinytra.adapter.next.pipeline.resolver.target;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.resolver.CompoundResolver;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;

public class TargetMethodResolver extends CompoundResolver {
    public TargetMethodResolver() {
        addSubResolver(TargetMethodSubResolvers.CHANGED_METHOD_PARAMS);
        addSubResolver(TargetMethodSubResolvers.MOVED_INTO_LAMBDA);
        addSubResolver(new SplitTargetMethodSubResolver());
    }

    @Override
    protected boolean canApply(MixinData mixin, Configuration clean, Configuration dirty) {
        return dirty.getTargetMethod() == null;
    }

    @Override
    protected Configuration tryReuse(MixinContext context, Configuration clean, Configuration dirty) {
        MethodQualifier cleanQualifier = clean.getTargetMethod();
        MethodContext.TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), cleanQualifier);
        if (target != null && !context.methods().findInjectionTargetInsns(target).isEmpty()) {
            return dirty.subConfig()
                .inheritTargetMethod();
        }
        return null;
    }

    @Override
    protected Configuration useFallback(MixinContext context, Configuration clean, Configuration dirty) {
        MethodQualifier cleanQualifier = clean.getTargetMethod();
        MethodContext.TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), cleanQualifier);
        if (target != null) {
            return dirty.subConfig()
                .inheritTargetMethod();
        }
        return null;
    }
}
