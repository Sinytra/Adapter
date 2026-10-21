package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.LocatingSubResolver;
import org.sinytra.adapter.pipeline.locate.SameMethodLocator;
import org.sinytra.adapter.pipeline.resolver.CompoundResolver;

import java.util.List;

public class InjectionPointResolver extends CompoundResolver {

    public InjectionPointResolver() {
        addSubResolver(new LocatingSubResolver(List.of(new SameMethodLocator())));
        addSubResolver(new InheritedInjectionPointSubResolver());
        addSubResolver(InjectionPointSubResolvers.EXTRACTED_CALL);
        addSubResolver(InjectionPointSubResolvers.EXTRACTED_CODEPATH_CALL);
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
        
        TargetPair dirtyTarget = context.dirtyMethods().resolveDeclared(targetQualifier.withOwner(Type.getObjectType(targetClass)));
        if (dirtyTarget == null) return null;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.injections().find(dirtyTarget);
        if (!insns.isEmpty()) {
            return dirty.copyClean()
                .inheritAtData();
        }

        return null;
    }
}
