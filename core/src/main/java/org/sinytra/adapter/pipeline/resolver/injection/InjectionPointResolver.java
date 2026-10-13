package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.CalledMethodLocator;
import org.sinytra.adapter.pipeline.locate.Candidate;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.locate.LocalLocator;
import org.sinytra.adapter.pipeline.locate.LocatingSubResolver;
import org.sinytra.adapter.pipeline.locate.ReplacedCallLocator;
import org.sinytra.adapter.pipeline.locate.SameMethodLocator;
import org.sinytra.adapter.pipeline.resolver.CompoundResolver;
import org.sinytra.adapter.util.AdapterUtil;

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
        if (!insns.isEmpty() && selectsLocatedInsns(context, recipe, dirtyTarget, insns) && selectsSameLocal(context, recipe, dirtyTarget, insns.getFirst())) {
            return dirty.copyClean()
                .inheritAtData();
        }

        return null;
    }

    private static boolean selectsSameLocal(MixinContext context, Recipe recipe, TargetPair dirtyTarget, AbstractInsnNode dirtyInsn) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        AtData cleanAt = recipe.clean().getAtData();
        if (cleanTarget == null || cleanAt == null || !Describer.isLocalAt(context, cleanAt)) {
            return true;
        }
        AbstractInsnNode cleanInsn = AdapterUtil.getFirstOrNull(context.injections().find(cleanTarget));
        if (cleanInsn == null) {
            return true;
        }
        Type type = Type.getReturnType(context.methodNode().desc);
        int cleanSlot = context.locals().resolveSelectedLocal(cleanTarget, cleanInsn, type);
        int expected = cleanSlot != -1 ? LocalLocator.resolve(context, recipe, cleanTarget, cleanSlot, cleanInsn, dirtyTarget) : -1;
        return expected == -1 || context.locals().resolveSelectedLocal(dirtyTarget, dirtyInsn, type) == expected;
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
