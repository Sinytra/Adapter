package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.CalledMethodLocator;
import org.sinytra.adapter.pipeline.locate.Candidate;
import org.sinytra.adapter.pipeline.locate.LocatingSubResolver;
import org.sinytra.adapter.pipeline.locate.ReplacedCallLocator;
import org.sinytra.adapter.pipeline.locate.SameMethodLocator;
import org.sinytra.adapter.pipeline.resolver.CompoundResolver;

import java.util.List;

public class InjectionPointResolver extends CompoundResolver {
    private static final SameMethodLocator SAME_METHOD = new SameMethodLocator();

    public InjectionPointResolver() {
        addSubResolver(new LocatingSubResolver(List.of(SAME_METHOD, new CalledMethodLocator(), new ReplacedCallLocator())));
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
        if (!insns.isEmpty() && selectsLocatedInsns(context, recipe, dirtyTarget, insns)) {
            return dirty.copyClean()
                .inheritAtData();
        }

        return null;
    }

    private static boolean selectsLocatedInsns(MixinContext context, Recipe recipe, TargetPair dirtyTarget, List<AbstractInsnNode> dirtyInsns) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null || !cleanTarget.methodNode().name.equals(dirtyTarget.methodNode().name)
            || !cleanTarget.methodNode().desc.equals(dirtyTarget.methodNode().desc)
        ) {
            return true;
        }
        Targets targets = new Targets(cleanTarget, dirtyTarget);
        for (AbstractInsnNode cleanInsn : context.injections().find(cleanTarget)) {
            List<Candidate> candidates = SAME_METHOD.locate(context, recipe, targets, cleanInsn);
            if (!candidates.isEmpty() && candidates.stream().noneMatch(c -> dirtyInsns.contains(c.insn()))) {
                return false;
            }
        }
        return true;
    }
}
