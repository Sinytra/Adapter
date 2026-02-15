package org.sinytra.adapter.patch.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.resolver.CompoundResolver;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;

public class InjectionPointResolver extends CompoundResolver {

    public InjectionPointResolver() {
        addSubResolver(new OverloadedInjectionPointSubResolver());
        addSubResolver(new InheritedInjectionPointSubResolver());
        addSubResolver(InjectionPointSubResolvers.REPLACED_TYPE);
        addSubResolver(InjectionPointSubResolvers.EXTRACTED_CALL);
    }

    @Override
    protected boolean canApply(Recipe recipe) {
        return recipe.dirty().getAtData() == null;
    }

    @Nullable
    @Override
    protected Configuration tryReuse(MixinContext context, Recipe recipe) {
        Configuration dirty = recipe.dirty();

        String targetClass = dirty.getTargetClass();
        if (targetClass == null) return null;

        MethodQualifier targetQualifier = dirty.getTargetMethod();
        if (targetQualifier == null) return null;
        
        TargetPair dirtyTarget = context.methods().findMethodPair(context.dirtyLookup(), targetQualifier.withOwner(Type.getObjectType(targetClass)));
        if (dirtyTarget == null) return null;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(dirtyTarget);
        if (!insns.isEmpty()) {
            return dirty.copyClean()
                .inheritAtData();
        }

        return null;
    }
}
