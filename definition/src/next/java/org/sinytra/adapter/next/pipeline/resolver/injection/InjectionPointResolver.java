package org.sinytra.adapter.next.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.resolver.CompoundResolver;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public class InjectionPointResolver extends CompoundResolver {

    public InjectionPointResolver() {
        addSubResolver(InjectionPointSubResolvers.REPLACED_TYPE);
        addSubResolver(new ArbitraryInjectionPointSubResolver());
    }

    @Override
    protected boolean canApply(MixinData mixin, Configuration clean, Configuration dirty) {
        return dirty.getAtData() == null;
    }

    @Nullable
    @Override
    protected Configuration tryReuse(MixinContext context, Configuration clean, Configuration dirty) {
        MethodQualifier dirtyQualifier = dirty.getTargetMethod();
        if (dirtyQualifier == null) return null;

        MethodContext.TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), dirtyQualifier);
        if (dirtyTarget == null) return null;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(dirtyTarget);
        if (!insns.isEmpty()) {
            return dirty.subConfig()
                .inheritAtData();
        }

        return null;
    }
}
