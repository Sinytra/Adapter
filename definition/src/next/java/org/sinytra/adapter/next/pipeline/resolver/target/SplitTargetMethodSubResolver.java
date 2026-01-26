package org.sinytra.adapter.next.pipeline.resolver.target;

import com.google.common.collect.Multimap;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.patch.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.next.env.ctx.TargetPair;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handle cases where a single method is split into multiple smaller pieces.
 * For an example, see <code>net.minecraft.client.gui.Gui#renderPlayerHealth</code>
 */
public class SplitTargetMethodSubResolver implements SubResolver {
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), recipe.clean().getTargetMethod());
        if (cleanTarget == null || dirtyTarget == null) return null;

        List<CandidateMethod> candidates = disambiguate(locateCandidates(context, cleanTarget, dirtyTarget), context, cleanTarget);

        if (candidates.size() == 1) {
            MethodNode method = candidates.getFirst().method();
//            methodContext.recordAudit(this, "Adjusting split method target to %s", newTarget);

            // TODO Move to processor
            if (recipe.clean().isCancellable()) {
                SplitMethodCancellationHelper.handle(context, recipe, method);
            }

            return MutableConfiguration.create()
                .setTargetMethod(method);
        }

        return null;
    }

    private static List<CandidateMethod> locateCandidates(MixinContext context, TargetPair cleanTarget, TargetPair dirtyTarget) {
        MethodNode cleanTargetMethod = cleanTarget.methodNode();
        ClassNode dirtyTargetClass = dirtyTarget.classNode();
        MethodNode dirtyTargetMethod = dirtyTarget.methodNode();

        // Check that a Deprecated annotation was added to the dirty method 
        if (!MethodAnalyzer.isDirtyDeprecatedMethod(cleanTargetMethod, dirtyTargetMethod)) {
            return tryFindPartialCandidates(cleanTargetMethod, dirtyTargetClass, dirtyTargetMethod, context);
        }

        List<MethodNode> invocations = MethodAnalyzer.collectMethodInvocations(dirtyTargetClass, dirtyTargetMethod);
        if (invocations == null) {
            return List.of();
        }

        List<CandidateMethod> candidates = findInsnsCalls(invocations.stream()
            .map(m -> new TargetPair(dirtyTargetClass, m)).toList(), context);

        // Attempt to find matching insns in lambdas
        if (candidates.isEmpty()) {
            List<TargetPair> nestedLambdas = invocations.stream()
                .flatMap(m -> MethodAnalyzer.findLambdasInMethod(dirtyTarget.classNode(), m, null).stream())
                .flatMap(s -> MethodQualifier.parse(s).stream())
                .map(s -> context.methods().findOwnMethodPair(context.dirtyLookup(), s))
                .filter(Objects::nonNull)
                .toList();
            return findInsnsCalls(nestedLambdas, context);
        }

        return candidates;
    }

    // Handle cases where only part of the method is moved away
    private static List<CandidateMethod> tryFindPartialCandidates(MethodNode cleanTargetMethod, ClassNode dirtyTargetClass, MethodNode dirtyTargetMethod, MixinContext context) {
        Multimap<String, MethodInsnNode> cleanMethodCalls = MethodAnalyzer.getMethodCalls(cleanTargetMethod, new ArrayList<>());
        Multimap<String, MethodInsnNode> dirtyMethodCalls = MethodAnalyzer.getMethodCalls(dirtyTargetMethod, new ArrayList<>());

        List<TargetPair> dirtyOnlyCalls = dirtyMethodCalls.entries().stream()
            .filter(e -> !cleanMethodCalls.containsKey(e.getKey()) && e.getValue().owner.equals(dirtyTargetClass.name))
            .map(Map.Entry::getValue)
            .map(i -> context.methods().findOwnMethodPair(context.dirtyLookup(), MethodQualifier.create(i)))
            .filter(Objects::nonNull)
            .toList();

        return findInsnsCalls(dirtyOnlyCalls, context);
    }

    // If multiple candidates have been found, try comparing the surrounding method instructions to find one match
    private static List<CandidateMethod> disambiguate(List<CandidateMethod> candidates, MixinContext context, TargetPair cleanTarget) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        List<AbstractInsnNode> cleanInsns = context.methods().findInjectionTargetInsns(cleanTarget);
        if (cleanInsns.size() != 1) {
            return candidates;
        }

        InstructionMatcher cleanMatcher = MethodInsnMatcher.findSurroundingInstructions(cleanInsns.getFirst(), 5);

        List<CandidateMethod> matchingCandidates = candidates.stream()
            .filter(method -> method.insns().size() == 1)
            .filter(method -> {
                InstructionMatcher matcher = MethodInsnMatcher.findSurroundingInstructions(method.insns().getFirst(), 5);
                return cleanMatcher.test(matcher, InsnComparator.IGNORE_VAR_INDEX);
            })
            .toList();

        if (matchingCandidates.size() == 1) {
            return matchingCandidates;
        }

        return candidates;
    }

    private static List<CandidateMethod> findInsnsCalls(List<TargetPair> methods, MixinContext context) {
        return methods.stream()
            .map(pair -> {
                List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(pair);
                return !insns.isEmpty() ? new CandidateMethod(pair.methodNode(), insns) : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private record CandidateMethod(MethodNode method, List<AbstractInsnNode> insns) {
    }
}
