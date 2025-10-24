package org.sinytra.adapter.next.pipeline.resolver.target;

import com.google.common.collect.Multimap;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.transformer.dynfix.SplitMethodCancellationHelper;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.*;

/**
 * Handle cases where a single method is split into multiple smaller pieces.
 * For an example, see <code>net.minecraft.client.gui.Gui#renderPlayerHealth</code>
 */
public class SplitTargetMethodSubResolver implements Resolver {
    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        MethodContext.TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), clean.getTargetMethod());
        MethodContext.TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), clean.getTargetMethod());
        if (dirtyTarget == null) {
            return TxResult.PASS;
        }

        List<CandidateMethod> candidates = disambiguate(locateCandidates(context, dirtyTarget), context, cleanTarget);

        if (candidates.size() == 1) {
            MethodNode method = candidates.getFirst().method();
//            methodContext.recordAudit(this, "Adjusting split method target to %s", newTarget);

            // TODO
            if (context.legacy().isCancellable()) {
                SplitMethodCancellationHelper.handle(this, context.legacy(), method);
            }

            dirty.setTargetMethod(method);
            return TxResult.SUCCESS;
        }

        return TxResult.PASS;
    }

    private static List<CandidateMethod> locateCandidates(MixinContext context, MethodContext.TargetPair dirtyTarget) {
        MethodContext methodContext = context.legacy();

        MethodNode cleanTargetMethod = methodContext.findCleanInjectionTarget().methodNode();
        ClassNode dirtyTargetClass = methodContext.findDirtyInjectionTarget().classNode();
        MethodNode dirtyTargetMethod = methodContext.findDirtyInjectionTarget().methodNode();

        // Check that a Deprecated annotation was added to the dirty method 
        if (!MethodCallAnalyzer.isDirtyDeprecatedMethod(cleanTargetMethod, dirtyTargetMethod)) {
            return tryFindPartialCandidates(cleanTargetMethod, dirtyTargetClass, dirtyTargetMethod, methodContext);
        }

        List<MethodNode> invocations = MethodCallAnalyzer.collectMethodInvocations(dirtyTargetClass, dirtyTargetMethod);
        if (invocations == null) {
            return List.of();
        }

        List<CandidateMethod> candidates = findInsnsCalls(invocations, methodContext);

        // Attempt to find matching insns in lambdas
        if (candidates.isEmpty()) {
            List<MethodNode> nestedLambdas = invocations.stream()
                .flatMap(m -> MethodCallAnalyzer.findLambdasInMethod(dirtyTarget.classNode(), m, null).stream())
                .map(s -> MethodQualifier.create(s).orElseThrow())
                .flatMap(s -> Optional.ofNullable(context.methods().findOwnMethodPair(context.dirtyLookup(), s))
                    .map(MethodContext.TargetPair::methodNode)
                    .stream())
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
            .flatMap(i -> MethodCallAnalyzer.findMethodByName(dirtyTargetClass, i.name, i.desc).stream())
            .toList();

        return findInsnsCalls(dirtyOnlyCalls, methodContext);
    }

    // If multiple candidates have been found, try comparing the surrounding method instructions to find one match
    private static List<CandidateMethod> disambiguate(List<CandidateMethod> candidates, MixinContext context, MethodContext.TargetPair cleanTarget) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        List<AbstractInsnNode> cleanInsns = context.methods().findInjectionTargetInsns(cleanTarget);
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

    private record CandidateMethod(MethodNode method, List<AbstractInsnNode> insns) {
    }
}
