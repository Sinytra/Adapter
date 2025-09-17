package org.sinytra.adapter.next.pipeline.resolver;

import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class InjectionTargetResolver implements Resolver {
    private static final Set<String> IGNORED_VALUES = Set.of("HEAD", "TAIL");

    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getAtData() != null) {
            return TxResult.PASS;
        }
        if (IGNORED_VALUES.contains(recipe.clean().getAtData().getValue())) {
            dirty.setAtData(recipe.clean().getAtData());
            return TxResult.SUCCESS;
        }
        if (dirty.getTargetMethod() == null) {
            return TxResult.FAIL;
        }

        MethodContext.TargetPair pair = findInjectionTarget(context.getMethodContext().patchContext().environment().dirtyClassLookup(), dirty.getTargetMethod(), context.getMethodContext());
        if (pair == null) {
            return TxResult.FAIL;
        }

        List<AbstractInsnNode> insns = context.getMethodContext().findInjectionTargetInsns(pair);
        if (!insns.isEmpty()) {
            dirty.setAtData(recipe.clean().getAtData());
            return TxResult.SUCCESS;
        }

        AtData replaced = findReplacedType(context, recipe.clean().getAtData());
        if (replaced != null) {
            dirty.setAtData(replaced);
            return TxResult.SUCCESS;
        }

        return TxResult.FAIL;
    }

    @Nullable
    private static AtData findReplacedType(MixinContext context, AtData original) {
        List<AbstractInsnNode> insns = context.getMethodContext().findInjectionTargetInsns(context.getMethodContext().findCleanInjectionTarget());
        if (insns.size() != 1 || !(insns.getFirst() instanceof MethodInsnNode cleanInsn)) {
            return null;
        }

        int insnRange = 5;
        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findSurroundingInstructions(cleanInsn, 5);

        // TODO API
        Pair<ClassNode, List<MethodNode>> candidates = context.getMethodContext().findInjectionTargetCandidates(context.getMethodContext().patchContext().environment().dirtyClassLookup(), true);
        if (candidates == null || candidates.getSecond().size() != 1) {
            return null;
        }

        MethodNode dirtyMethod = candidates.getSecond().getFirst();
        Multimap<String, MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCalls(dirtyMethod, new ArrayList<>());

        List<InstructionMatcher> dirtyMatchers = dirtyCalls.values().stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();
        for (InstructionMatcher dirtyMatcher : dirtyMatchers) {
            if (cleanMatcher.test(dirtyMatcher)) {
                MethodInsnNode minsn = (MethodInsnNode) dirtyMatcher.insn();
                Integer ordinal = original.getOrdinal().stream().boxed().findFirst().orElse(null);
                String target = Type.getObjectType(minsn.owner).getDescriptor() + minsn.name + minsn.desc;
                return new AtData(original.getValue(), target, ordinal);
            }
        }

        return null;
    }

    @Nullable
    private static MethodContext.TargetPair findInjectionTarget(ClassLookup lookup, MethodQualifier qualifier, MethodContext ctx) {
        Pair<ClassNode, List<MethodNode>> pair = findInjectionTargetCandidates(lookup, qualifier, ctx, false);
        if (pair == null) {
            return null;
        }

        if (pair.getSecond().isEmpty()) {
            return null;
        } else if (pair.getSecond().size() > 1) {
            return null;
        }
        return new MethodContext.TargetPair(pair.getFirst(), pair.getSecond().getFirst());
    }

    @Nullable
    private static Pair<ClassNode, List<MethodNode>> findInjectionTargetCandidates(ClassLookup lookup, MethodQualifier qualifier, MethodContext ctx, boolean ignoreDesc) {
        if (qualifier == null || qualifier.name() == null) {
            return null;
        }
        String owner = Optional.ofNullable(qualifier.internalOwnerName())
            .orElseGet(() -> {
                List<Type> targetTypes = ctx.targetTypes();
                if (targetTypes.size() == 1) {
                    return targetTypes.getFirst().getInternalName();
                }
                return null;
            });
        if (owner == null) {
            return null;
        }
        // Find target class
        ClassNode targetClass = lookup.getClass(owner).orElse(null);
        if (targetClass == null) {
            return null;
        }
        // Find target method in class
        String desc = qualifier.desc();
        List<MethodNode> candidates = targetClass.methods.stream()
            .filter(mtd -> mtd.name.equals(qualifier.name()) && (ignoreDesc || desc == null || mtd.desc.equals(desc)))
            .toList();
        // If there's multiple candidates, try removing bouncer methods
        if (candidates.size() > 1 && desc == null) {
            candidates = candidates.stream().filter(mtd -> (mtd.access & Opcodes.ACC_SYNTHETIC) == 0 && (mtd.access & Opcodes.ACC_BRIDGE) == 0).toList();
        }
        return Pair.of(targetClass, candidates);
    }
}
