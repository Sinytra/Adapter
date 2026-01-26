package org.sinytra.adapter.patch.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.resolver.CompoundResolver;
import org.sinytra.adapter.env.ctx.TargetPair;

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
