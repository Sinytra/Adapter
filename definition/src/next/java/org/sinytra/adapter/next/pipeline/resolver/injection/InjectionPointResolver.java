package org.sinytra.adapter.next.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.resolver.CompoundResolver;
import org.sinytra.adapter.patch.api.TargetPair;

import java.util.List;

public class InjectionPointResolver extends CompoundResolver {

    public InjectionPointResolver() {
        addSubResolver(new InheritedInjectionPointSubResolver());
        addSubResolver(InjectionPointSubResolvers.REPLACED_TYPE);
    }

    @Override
    protected boolean canApply(Recipe recipe) {
        return recipe.dirty().getAtData() == null;
    }

    @Nullable
    @Override
    protected Configuration tryReuse(MixinContext context, Recipe recipe) {
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) return null;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(dirtyTarget);
        if (!insns.isEmpty()) {
            return recipe.dirty().copyClean()
                .inheritAtData();
        }

        return null;
    }
}
