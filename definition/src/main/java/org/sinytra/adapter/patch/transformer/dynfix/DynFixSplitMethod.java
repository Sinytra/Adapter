package org.sinytra.adapter.patch.transformer.dynfix;

import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.operation.ModifyInjectionTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handle cases where a single method is split into multiple smaller pieces.
 * For an example, see <code>net.minecraft.client.gui.Gui#renderPlayerHealth</code>
 */
public class DynFixSplitMethod implements DynamicFixer<DynFixSplitMethod.Data> {
    private static final String DEPRECATED = "Ljava/lang/Deprecated;";

    public record Data() {
    }

    @Nullable
    @Override
    public DynFixSplitMethod.Data prepare(MethodContext methodContext) {
        if (methodContext.hasInjectionPointValue("INVOKE") && methodContext.findCleanInjectionTarget() != null && methodContext.findDirtyInjectionTarget() != null) {
            return new Data();
        }
        return null;
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        List<CandidateMethod> candidates = disambiguate(locateCandidates(methodContext), methodContext);

        if (candidates.size() == 1) {
            MethodNode method = candidates.getFirst().method();
            String newTarget = method.name + method.desc;
            methodContext.recordAudit(this, "Adjusting split method target to %s", newTarget);
            if (methodContext.isCancellable()) {
                SplitMethodCancellationHelper.handle(this, methodContext, method);
            }
            return FixResult.of(new ModifyInjectionTarget(List.of(newTarget)).apply(methodContext), PatchAuditTrail.Match.FULL);
        }

        return null;
    }
    
    public static boolean isDirtyDeprecatedMethod(MethodNode clean, MethodNode dirty) {
        return !AdapterUtil.hasAnnotation(clean.visibleAnnotations, DEPRECATED) && AdapterUtil.hasAnnotation(dirty.visibleAnnotations, DEPRECATED);
    }

    @Nullable
    public static List<MethodNode> collectMethodInvocations(ClassNode cls, MethodNode mtd) {
        // Iterate over isns, leave out first and last elements
        // Collect method invocations
        // All labels must be finalized by a method invocation to pass
        List<MethodNode> invocations = new ArrayList<>();
        for (int i = 1; i < mtd.instructions.size() - 1; i++) {
            AbstractInsnNode insn = mtd.instructions.get(i);
            if (insn instanceof LabelNode) {
                AbstractInsnNode previous = insn.getPrevious();
                if (previous instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(cls.name)) {
                    MethodNode method = cls.methods.stream().filter(m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElseThrow();
                    invocations.add(method);
                } else if (previous == null || !OpcodeUtil.isReturnOpcode(previous.getOpcode())) {
                    return null;
                }
            }
        }
        return invocations;
    }
    
    private static List<CandidateMethod> locateCandidates(MethodContext methodContext) {
        MethodNode cleanTargetMethod = methodContext.findCleanInjectionTarget().methodNode();
        ClassNode dirtyTargetClass = methodContext.findDirtyInjectionTarget().classNode();
        MethodNode dirtyTargetMethod = methodContext.findDirtyInjectionTarget().methodNode();

        // Check that a Deprecated annotation was added to the dirty method 
        if (!isDirtyDeprecatedMethod(cleanTargetMethod, dirtyTargetMethod)) {
            return tryFindPartialCandidates(cleanTargetMethod, dirtyTargetClass, dirtyTargetMethod, methodContext);
        }

        List<MethodNode> invocations = collectMethodInvocations(dirtyTargetClass, dirtyTargetMethod);
        if (invocations == null) {
            return null;
        }

        List<CandidateMethod> candidates = findInsnsCalls(invocations, methodContext);

        // Attempt to find matching insns in lambdas
        if (candidates.isEmpty()) {
            List<MethodNode> nestedLambdas = invocations.stream()
                .flatMap(m -> MethodCallAnalyzer.findLambdasInMethod(dirtyTargetClass, m, null).stream())
                .flatMap(s -> MethodCallAnalyzer.findMethodByUniqueName(dirtyTargetClass, s).stream())
                .toList();
            return findInsnsCalls(nestedLambdas, methodContext);
        }

        return candidates;
    }

    // Handle cases where only part of the method is moved away
    private static List<CandidateMethod> tryFindPartialCandidates(MethodNode cleanTargetMethod, ClassNode dirtyTargetClass, MethodNode dirtyTargetMethod, MethodContext methodContext) {
        Multimap<String, MethodInsnNode> cleanMethodCalls = MethodCallAnalyzer.getMethodCalls(cleanTargetMethod, new ArrayList<>());
        Multimap<String, MethodInsnNode> dirtyMethodCalls = MethodCallAnalyzer.getMethodCalls(dirtyTargetMethod, new ArrayList<>());

        List<MethodNode> dirtyOnlyCalls = dirtyMethodCalls.entries().stream()
            .filter(e -> !cleanMethodCalls.containsKey(e.getKey()) && e.getValue().owner.equals(dirtyTargetClass.name))
            .map(Map.Entry::getValue)
            .flatMap(i -> MethodCallAnalyzer.findMethodByNameOrThrow(dirtyTargetClass, i.name, i.desc).stream())
            .toList();

        return findInsnsCalls(dirtyOnlyCalls, methodContext);
    }

    // If multiple candidates have been found, try comparing the surrounding method instructions to find one match
    private static List<CandidateMethod> disambiguate(List<CandidateMethod> candidates, MethodContext methodContext) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(methodContext.findCleanInjectionTarget());
        if (cleanInsns.size() != 1) {
            return candidates;
        }

        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findSurroundingInstructions(cleanInsns.getFirst(), 5);

        List<CandidateMethod> matchingCandidates = candidates.stream()
            .filter(method -> method.insns().size() == 1)
            .filter(method -> {
                InstructionMatcher matcher = MethodCallAnalyzer.findSurroundingInstructions(method.insns().getFirst(), 5);
                return cleanMatcher.test(matcher, InsnComparator.IGNORE_VAR_INDEX);
            })
            .toList();

        if (matchingCandidates.size() == 1) {
            return matchingCandidates;
        }

        return candidates;
    }

    private static List<CandidateMethod> findInsnsCalls(List<MethodNode> methods, MethodContext methodContext) {
        ClassNode dirtyTargetClass = methodContext.findDirtyInjectionTarget().classNode();
        return methods.stream()
            .map(method -> {
                List<AbstractInsnNode> insns = methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(dirtyTargetClass, method));
                return !insns.isEmpty() ? new CandidateMethod(method, insns) : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private record CandidateMethod(MethodNode method, List<AbstractInsnNode> insns) {}
}
