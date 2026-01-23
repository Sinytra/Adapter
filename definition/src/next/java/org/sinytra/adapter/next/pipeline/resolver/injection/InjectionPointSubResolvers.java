package org.sinytra.adapter.next.pipeline.resolver.injection;

import com.google.common.collect.Multimap;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.WeighedDisambiguation;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.patch.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.patch.api.TargetPair;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;

public class InjectionPointSubResolvers {
    public static final SubResolver REPLACED_TYPE = (MixinContext context, Recipe recipe) -> {
        if (!recipe.clean().getAtData().getValue().equals(AT_VAL_INVOKE)) return null;

        TargetPair cleanPair = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        // Find single clean target minsn
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(cleanPair);
        if (insns.isEmpty() || !(insns.getFirst() instanceof MethodInsnNode cleanInsn)) return null;

        InstructionMatcher cleanMatcher = MethodInsnMatcher.findSurroundingInstructions(cleanInsn);
        Multimap<String, MethodInsnNode> dirtyCalls = MethodAnalyzer.getMethodCalls(dirtyTarget.methodNode(), new ArrayList<>());
        List<InstructionMatcher> dirtyMatchers = dirtyCalls.values().stream()
            .map(MethodInsnMatcher::findSurroundingInstructions)
            .toList();

        WeighedDisambiguation<MethodQualifier> magicBlackBox = WeighedDisambiguation.<MethodQualifier>builder()
            .match(() -> testMatchers(context, cleanInsn, cleanMatcher, dirtyMatchers, false))
            .match(() -> testMatchers(context, cleanInsn, cleanMatcher, dirtyMatchers, true))
            .match(() -> testOverloadedMethods(context, cleanInsn, cleanPair, dirtyTarget))
            .resultsEqual(MethodQualifier::equals)
            .build();

        MethodQualifier replacement = magicBlackBox.findBestMatch();
        if (replacement != null) {
            return MutableConfiguration.create()
                .setAtData(recipe.clean().getAtData().withTarget(replacement));
        }

        return null;
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
                if (cleanPair.classNode().methods.stream()
                    .noneMatch(c -> c.name.equals(m.name) && c.desc.equals(m.desc)) && m.name.equals(cleanInsn.name)
                ) {
                    List<Type> dirtyParams = Parameters.getParameterTypes(m.desc);
                    return dirtyParams.size() > cleanParams.size() && dirtyParams.subList(0, cleanParams.size()).equals(cleanParams);
                }
                return false;
            })
            .filter(m -> MethodAnalyzer.containsMethodCall(dirtyPair.methodNode(), MethodQualifier.create(m)))
            .toList();
        return methods.size() == 1 ? List.of(MethodQualifier.create(dirtyClass, methods.getFirst())) : List.of();
    }

    private static boolean matchesMethodCall(MixinContext context, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        return cleanInsn.owner.equals(dirtyInsn.owner) && cleanInsn.name.equals(dirtyInsn.name)
            || context.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getObjectType(cleanInsn.owner)) != null
            && Type.getArgumentTypes(cleanInsn.desc).length == Type.getArgumentTypes(dirtyInsn.desc).length;
    }
}
