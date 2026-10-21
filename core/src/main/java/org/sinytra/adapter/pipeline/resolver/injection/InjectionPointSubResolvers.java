package org.sinytra.adapter.pipeline.resolver.injection;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.compare.CodePaths;
import org.sinytra.adapter.analysis.compare.CodePaths.CodePath;
import org.sinytra.adapter.analysis.compare.CodePaths.CodePathStep;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.List;
import java.util.Set;

public class InjectionPointSubResolvers {
    public static final SubResolver EXTRACTED_CALL = (MixinContext context, Recipe recipe) -> {
        if (context.isStatic()) return null;

        Targets targets = recipe.targets();
        if (targets == null) return null;

        for (MethodInsnNode minsn : MethodAnalyzer.findDirtyOnlyCalls(targets.clean().methodNode(), targets.dirty().methodNode(), true)) {
            // We only want static external methods
            if (minsn.getOpcode() != Opcodes.INVOKESTATIC || minsn.owner.equals(targets.dirty().classNode().name))
                continue;

            List<AbstractInsnNode> argInsns = context.dataFlow(targets.dirty()).resolveCallOperandProducers(minsn);
            if (argInsns == null) continue;

            AbstractInsnNode self = argInsns.stream()
                .filter(i -> i instanceof VarInsnNode vinsn && vinsn.var == 0)
                .findFirst()
                .orElse(null);
            if (self == null) continue;

            int selfParamIndex = argInsns.indexOf(self);
            MethodQualifier target = MethodQualifier.create(minsn);

            TargetPair pair = context.dirtyMethods().resolveDeclared(target);
            if (pair != null && context.injections().matches(pair)) {
                return recipe.dirty().copyClean()
                    .setTargetClass(minsn.owner)
                    .setTargetMethod(minsn)
                    .inheritAtData()
                    .setProperty(IntentKeys.RELOCATION, new Relocation.ReceivesOrigin(selfParamIndex))
                    .inheritProperyIfAbsent(MixinKeys.INDEX)
                    .inheritProperyIfAbsent(MixinKeys.ORDINAL);
            }
        }

        return null;
    };

    public static final SubResolver EXTRACTED_CODEPATH_CALL = (MixinContext context, Recipe recipe) -> {
        Targets targets = recipe.targets();
        if (targets == null) return null;
        if (!(recipe.getCleanInjectionInsn() instanceof MethodInsnNode minsn)) return null;

        Set<MethodInsnNode> newCalls = Set.copyOf(MethodAnalyzer.findDirtyOnlyCalls(targets.clean().methodNode(), targets.dirty().methodNode(), true));
        if (newCalls.isEmpty()) return null;
        Set<Integer> trackLocals = context.isStatic() ? Set.of() : Set.of(0);

        TargetPair to = context.dirtyMethods().resolveInherited(MethodQualifier.create(minsn));
        CodePath path = CodePaths.findCodePath(targets.dirty(), to, trackLocals, context, newCalls::contains);
        if (path == null) return null;

        CodePathStep last = path.steps().getLast();
        return recipe.dirty().copyClean()
            .setTargetClass(last.from().classNode())
            .setTargetMethod(last.from().methodNode())
            .inheritAtData();
    };
}
