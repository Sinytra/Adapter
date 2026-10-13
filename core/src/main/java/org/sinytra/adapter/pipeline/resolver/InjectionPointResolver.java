package org.sinytra.adapter.pipeline.resolver;

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
import org.sinytra.adapter.pipeline.locate.Candidate;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.locate.LocalLocator;
import org.sinytra.adapter.pipeline.locate.LocatorChain;
import org.sinytra.adapter.pipeline.locate.ReplacedCallLocator;
import org.sinytra.adapter.pipeline.locate.SameMethodLocator;
import org.sinytra.adapter.pipeline.locate.Scope;
import org.sinytra.adapter.pipeline.locate.ScopeSearch;
import org.sinytra.adapter.pipeline.locate.SelectedLocator;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;

public class InjectionPointResolver implements Resolver {
    private final List<LocatorChain> chains = new ArrayList<>();

    public InjectionPointResolver() {
        addChain(new LocatorChain(List.of(new SameMethodLocator(), new SelectedLocator(), new ReplacedCallLocator()), ScopeSearch.Reach.CALLS));
    }

    public InjectionPointResolver addChain(LocatorChain chain) {
        this.chains.add(chain);
        return this;
    }

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        if (recipe.dirty().getAtData() != null) {
            return ResolutionResult.pass();
        }

        Configuration reused = tryReuse(context, recipe);
        if (reused != null) {
            return ResolutionResult.success(reused);
        }

        for (LocatorChain chain : this.chains) {
            Configuration result = context.audited(chain, () -> chain.resolve(context, recipe));
            if (result != null) {
                return ResolutionResult.success(result);
            }
        }
        return ResolutionResult.fail();
    }

    @Nullable
    private static Configuration tryReuse(MixinContext context, Recipe recipe) {
        Configuration dirty = recipe.dirty();

        String targetClass = dirty.getTargetClass();
        if (targetClass == null) return null;

        MethodQualifier targetQualifier = dirty.getTargetMethod();
        if (targetQualifier == null) return null;
        
        TargetPair dirtyTarget = context.dirtyMethods().resolveDeclared(targetQualifier.withOwner(Type.getObjectType(targetClass)));
        if (dirtyTarget == null) return null;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.injections().find(dirtyTarget);
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (!insns.isEmpty() && (cleanTarget == null || selectsLocatedInsns(context, cleanTarget, dirtyTarget, insns))
            && selectsSameLocal(context, recipe, dirtyTarget, insns.getFirst())
        ) {
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

    public static boolean selectsLocatedInsns(MixinContext context, TargetPair cleanTarget, TargetPair dirtyTarget, List<AbstractInsnNode> dirtyInsns) {
        if (!cleanTarget.methodNode().name.equals(dirtyTarget.methodNode().name)
            || !cleanTarget.methodNode().desc.equals(dirtyTarget.methodNode().desc)
        ) {
            return true;
        }
        Scope scope = Scope.of(new Targets(cleanTarget, dirtyTarget));
        for (AbstractInsnNode cleanInsn : context.injections().find(cleanTarget)) {
            List<Candidate> candidates = SameMethodLocator.find(context, scope, cleanInsn);
            if (!candidates.isEmpty() && candidates.stream().noneMatch(c -> dirtyInsns.contains(c.insn()))) {
                return false;
            }
        }
        return true;
    }
}
