package org.sinytra.adapter.pipeline.resolver.injection;

import com.google.common.collect.Multimap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.compare.CodePaths;
import org.sinytra.adapter.analysis.compare.CodePaths.CodePath;
import org.sinytra.adapter.analysis.compare.CodePaths.CodePathStep;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.config.key.SpecialKeys;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.WeighedDisambiguation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class InjectionPointSubResolvers {
    public static final SubResolver REPLACED_TYPE = (MixinContext context, Recipe recipe) -> {
        Targets targets = recipe.targets();
        if (targets == null) return null;

        // Find single clean target minsn
        List<AbstractInsnNode> insns = context.injections().find(targets.clean());
        if (insns.isEmpty() || !(insns.getFirst() instanceof MethodInsnNode cleanInsn)) return null;

        InstructionMatcher cleanMatcher = MethodInsnMatcher.findSurroundingInstructions(cleanInsn);
        Multimap<String, MethodInsnNode> dirtyCalls = MethodAnalyzer.getMethodCalls(targets.dirty().methodNode());
        List<InstructionMatcher> dirtyMatchers = dirtyCalls.values().stream()
            .map(MethodInsnMatcher::findSurroundingInstructions)
            .toList();

        WeighedDisambiguation<MethodQualifier> magicBlackBox = WeighedDisambiguation.<MethodQualifier>builder()
            .match(() -> testMatchers(context, cleanInsn, cleanMatcher, dirtyMatchers, false))
            .match(() -> testMatchers(context, cleanInsn, cleanMatcher, dirtyMatchers, true))
            .match(() -> testOverloadedMethods(context, cleanInsn, targets.clean(), targets.dirty()))
            .resultsEqual(MethodQualifier::equals)
            .build();

        MethodQualifier replacement = magicBlackBox.findBestMatch();
        if (replacement != null) {
            return MutableConfiguration.create()
                .setAtData(recipe.clean().getAtData().withTarget(replacement));
        }

        return null;
    };

    public static final SubResolver EXTRACTED_CALL = (MixinContext context, Recipe recipe) -> {
        if (context.isStatic()) return null;

        Targets targets = recipe.targets();
        if (targets == null) return null;

        for (MethodInsnNode minsn : MethodAnalyzer.findDirtyOnlyCalls(targets.clean().methodNode(), targets.dirty().methodNode(), true)) {
            // We only want static external methods
            if (minsn.getOpcode() != Opcodes.INVOKESTATIC || minsn.owner.equals(targets.dirty().classNode().name))
                continue;

            List<AbstractInsnNode> argInsns = MethodAnalyzer.getMethodCallSrcInsns(targets.dirty().methodNode(), minsn);
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
                    .setProperty(SpecialKeys.EXTRACT_ORIGIN_PARAM, selfParamIndex)
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

    private static List<MethodQualifier> testMatchers(MixinContext context, MethodInsnNode cleanInsn, InstructionMatcher cleanMatcher, List<InstructionMatcher> dirtyMatchers, boolean partial) {
        return dirtyMatchers.stream()
            .map(m -> {
                boolean before = cleanMatcher.testBefore(m);
                boolean after = cleanMatcher.testAfter(m);
                boolean match = partial ? before || after : before && after;

                MethodInsnNode dirtyInsn = (MethodInsnNode) m.insn();
                if (match && matchesMethodCall(context, cleanInsn, dirtyInsn)) {
                    return dirtyInsn;
                }
                return null;
            })
            .filter(Objects::nonNull)
            .map(MethodQualifier::create)
            .toList();
    }

    private static List<MethodQualifier> testOverloadedMethods(MixinContext context, MethodInsnNode cleanInsn, TargetPair cleanPair, TargetPair dirtyPair) {
        ClassNode dirtyClass = context.dirtyLookup().getClass(cleanInsn.owner).orElse(null);
        if (dirtyClass == null) {
            return List.of();
        }

        List<Type> cleanParams = Parameters.getParameterTypes(cleanInsn.desc);
        List<MethodNode> methods = dirtyClass.methods.stream()
            .filter(m -> {
                if (m.name.equals(cleanInsn.name) && AdapterUtil.findMethod(cleanPair.classNode(), m.name, m.desc) == null) {
                    List<Type> dirtyParams = Parameters.getParameterTypes(m.desc);
                    return dirtyParams.size() > cleanParams.size() && dirtyParams.subList(0, cleanParams.size()).equals(cleanParams);
                }
                return false;
            })
            .filter(m -> !MethodAnalyzer.findCalls(dirtyPair.methodNode(), MethodQualifier.create(m)).isEmpty())
            .toList();
        return methods.size() == 1 ? List.of(MethodQualifier.create(dirtyClass, methods.getFirst())) : List.of();
    }

    private static boolean matchesMethodCall(MixinContext context, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        return cleanInsn.owner.equals(dirtyInsn.owner) && cleanInsn.name.equals(dirtyInsn.name)
            || context.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getObjectType(cleanInsn.owner)) != null
            && Type.getArgumentTypes(cleanInsn.desc).length == Type.getArgumentTypes(dirtyInsn.desc).length;
    }
}
