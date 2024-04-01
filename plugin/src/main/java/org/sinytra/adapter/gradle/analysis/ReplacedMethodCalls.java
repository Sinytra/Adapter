package org.sinytra.adapter.gradle.analysis;

import com.google.common.collect.Multimap;
import org.sinytra.adapter.gradle.util.MatchResult;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.params.ParametersDiff;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ReplacedMethodCalls {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplacedMethodCalls");
    private static final String[] NEAREST_REPLACEMENT_TARGET_TYPES = { MixinConstants.INJECT, MixinConstants.MODIFY_VAR };
    private static final String[] NEAREST_REPLACEMENT_TARGET_TYPES_EXTRA = ArrayUtils.add(NEAREST_REPLACEMENT_TARGET_TYPES, MixinConstants.MODIFY_EXPR_VAL);

    public static void findReplacedMethodCalls(AnalysisContext context, ClassNode dirtyNode, Map<MethodNode, MethodNode> cleanToDirty) {
        cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            int callAnalysisLimit = 2;
            int insnRange = 5;
            List<String> cleanCallOrder = new ArrayList<>();
            List<String> dirtyCallOrder = new ArrayList<>();
            Multimap<String, MethodInsnNode> cleanCalls = MethodCallAnalyzer.getMethodCalls(cleanMethod, cleanCallOrder);
            Multimap<String, MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCalls(dirtyMethod, dirtyCallOrder);

            dirtyCalls.asMap().forEach((qualifier, dirtyList) -> {
                Collection<MethodInsnNode> cleanList = cleanCalls.get(qualifier);
                if (cleanList.isEmpty() && dirtyList.size() == 1) {
                    // Try finding overloaded method call
                    if (!findOverloadedReplacement(context, dirtyMethod, qualifier, cleanCallOrder, dirtyCallOrder)) {
                        // Find nearest call as a simple replacement
                        findNearestMethodCall(context, dirtyNode, dirtyMethod, qualifier, cleanCallOrder, dirtyCallOrder);
                    }
                } else if (cleanList.size() != dirtyList.size() && cleanList.size() <= callAnalysisLimit) {
                    List<InstructionMatcher> cleanMatchers = cleanList.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();
                    List<InstructionMatcher> dirtyMatchers = dirtyList.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();

                    List<InstructionMatcher> missing = identifyMissingCalls(cleanMatchers, dirtyMatchers);

                    for (InstructionMatcher matcher : missing) {
                        MethodInsnNode mInsn = (MethodInsnNode) matcher.insn(); 
                        String original = MethodCallAnalyzer.getCallQualifier(mInsn);
                        String replacement = matcher.findReplacement(cleanCallOrder, dirtyCallOrder);
                        if (replacement != null && !replacement.equals(original)) {
                            context.getTrace().logHeader();
                            LOGGER.info("Replacing method call in {} to {}.{} with {}", dirtyMethod.name, mInsn.owner, mInsn.name, replacement);
                            boolean resetValues = MethodQualifier.create(replacement)
                                .filter(MethodQualifier::isFull)
                                .map(q -> OverloadedMethods.checkParameters(Type.getArgumentTypes(mInsn.desc), Type.getArgumentTypes(q.desc())) == MatchResult.NONE)
                                .orElse(false);
                            Patch patch = Patch.builder()
                                .targetClass(dirtyNode.name)
                                .targetMethod(dirtyMethod.name + dirtyMethod.desc)
                                .targetInjectionPoint(original)
                                .modifyInjectionPoint(null, replacement, resetValues)
                                .targetMixinType(MixinConstants.INJECT)
                                .build();
                            context.addPatch(patch);
                        }
                    }
                }
            });
        });
    }

    private static void findNearestMethodCall(AnalysisContext context, ClassNode cls, MethodNode method, String qualifier, List<String> cleanCallOrder, List<String> dirtyCallOrder) {
        int index = dirtyCallOrder.indexOf(qualifier);
        if (index <= 0 || index + 1 >= cleanCallOrder.size() || index + 1 >= dirtyCallOrder.size()) {
            return;
        }
        String previous = dirtyCallOrder.get(index - 1);
        if (!previous.equals(cleanCallOrder.get(index - 1))) {
            return;
        }
        String next = dirtyCallOrder.get(index + 1);
        if (next.equals(cleanCallOrder.get(index)) && index + 2 < dirtyCallOrder.size() && dirtyCallOrder.get(index + 2).equals(cleanCallOrder.get(index + 1))) {
            return;
        }
        for (int i = index; i < cleanCallOrder.size(); i++) {
            String candidate = cleanCallOrder.get(i);
            if (candidate.equals(previous)) {
                break;
            }
            if (candidate.equals(next)) {
                List<String> injectionPoints = new ArrayList<>(cleanCallOrder.subList(index, i));
                if (injectionPoints.isEmpty()) {
                    injectionPoints.add(candidate);
                }
                Type returnType = MethodQualifier.create(qualifier).map(q -> Type.getReturnType(q.desc())).orElseThrow();
                List<String> byReturnType = injectionPoints.stream()
                    .filter(s -> MethodQualifier.create(s)
                        .map(q -> Type.getReturnType(q.desc()).equals(returnType))
                        .orElse(false))
                    .toList();
                if (!byReturnType.isEmpty()) {
                    injectionPoints.removeAll(byReturnType);
                    generateNearestReplacementPatch(context, cls, method, byReturnType, qualifier, NEAREST_REPLACEMENT_TARGET_TYPES_EXTRA);
                }
                if (!injectionPoints.isEmpty()) {
                    generateNearestReplacementPatch(context, cls, method, injectionPoints, qualifier, NEAREST_REPLACEMENT_TARGET_TYPES);
                }
                break;
            }
        }
    }

    private static void generateNearestReplacementPatch(AnalysisContext context, ClassNode cls, MethodNode method, List<String> targets, String replacement, String[] mixinTypes) {
        context.getTrace().logHeader();
        LOGGER.info("REPLACE NEAREST");
        LOGGER.info("   {}", targets);
        LOGGER.info("\\> {}", replacement);
        LOGGER.info("? {}", (Object) mixinTypes);
        LOGGER.info("===");
        Patch patch = Patch.builder()
            .targetClass(cls.name)
            .targetMethod(method.name + method.desc)
            .targetMixinType(mixinTypes)
            .chain(b -> targets.forEach(b::targetInjectionPoint))
            .modifyInjectionPoint(replacement)
            .build();
        context.addPatch(patch);
    }

    private static boolean findOverloadedReplacement(AnalysisContext context, MethodNode dirtyMethod, String qualifier, List<String> cleanCallOrder, List<String> dirtyCallOrder) {
        int index = dirtyCallOrder.indexOf(qualifier);
        if (index == -1 || index >= cleanCallOrder.size()) {
            return false;
        }
        String cleanCall = cleanCallOrder.get(index);
        MethodQualifier cleanQualifier = MethodQualifier.create(cleanCall).filter(MethodQualifier::isFull).orElse(null);
        if (cleanQualifier == null) {
            return false;
        }
        MethodQualifier dirtyQualifier = MethodQualifier.create(qualifier).filter(MethodQualifier::isFull).orElse(null);
        if (dirtyQualifier == null) {
            return false;
        }
        // Same owner, same name, different desc => expanded method
        String owner = cleanQualifier.internalOwnerName();
        if (cleanQualifier.owner().equals(dirtyQualifier.owner()) && context.remapMethod(owner, cleanQualifier.name(), cleanQualifier.desc()).equals(dirtyQualifier.name()) && !cleanQualifier.desc().equals(dirtyQualifier.desc())) {
            ParametersDiff diff = ParametersDiff.compareTypeParameters(Type.getArgumentTypes(cleanQualifier.desc()), Type.getArgumentTypes(dirtyQualifier.desc()));
            if (!diff.insertions().isEmpty() && diff.replacements().isEmpty() && diff.removals().isEmpty()) {
                context.getTrace().logHeader();
                LOGGER.info("Replacing expanded method call in {} to {} with {}", dirtyMethod.name, cleanCall, qualifier);
                Patch patch = Patch.builder()
                    .targetClass(context.getDirtyNode().name)
                    .targetMethod(dirtyMethod.name + dirtyMethod.desc)
                    .targetInjectionPoint(cleanCall)
                    // Avoid automatic method upgrades when a parameter transformation is being applied
                    .modifyInjectionPoint(null, qualifier, false, true)
                    .transformMethods(diff.createTransforms(ParamTransformTarget.INJECTION_POINT))
                    .build();
                context.addPatch(patch);
                return true;
            }
        }
        return false;
    }

    private static List<InstructionMatcher> identifyMissingCalls(List<InstructionMatcher> cleanCalls, List<InstructionMatcher> dirtyCalls) {
        List<InstructionMatcher> activeCleanCalls = new ArrayList<>(cleanCalls);
        List<InstructionMatcher> activeDirtyCalls = new ArrayList<>(dirtyCalls);

        outer:
        for (Iterator<InstructionMatcher> iterator = activeCleanCalls.iterator(); iterator.hasNext(); ) {
            InstructionMatcher cleanMatcher = iterator.next();
            for (InstructionMatcher dirtyMatcher : activeDirtyCalls) {
                if (cleanMatcher.test(dirtyMatcher)) {
                    iterator.remove();
                    activeDirtyCalls.remove(dirtyMatcher);
                    continue outer;
                }
            }
        }

        return activeCleanCalls;
    }
}
