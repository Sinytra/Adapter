package org.sinytra.adapter.pipeline.resolver.target;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.compare.InsnComparator;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodAnalyzer.IndyHandle;
import org.sinytra.adapter.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.SpecialKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.List;
import java.util.Objects;

/**
 * Handle cases where a single method is split into multiple smaller pieces.
 * For an example, see <code>net.minecraft.client.gui.Gui#renderPlayerHealth</code>
 */
public class SplitTargetMethodSubResolver implements SubResolver {
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getNewCleanTarget();
        if (cleanTarget == null || dirtyTarget == null) return null;

        List<CandidateMethod> candidates = disambiguate(locateCandidates(context, cleanTarget, dirtyTarget), context, cleanTarget);

        if (candidates.size() == 1) {
            MethodNode method = candidates.getFirst().method();
            context.recordCtxAudit("Adjusting split method target to %s", MethodQualifier.create(method).asDescriptor());

            MutableConfiguration config = MutableConfiguration.create()
                .setTargetMethod(method);
            if (recipe.clean().isCancellable()) {
                config.setProperty(SpecialKeys.SPLIT_CANCELLATION, true);
            }
            return config;
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

        List<MethodNode> invocations = context.dataFlow(dirtyTarget).findTopLevelOwnCalls();
        if (invocations == null) {
            return List.of();
        }

        List<CandidateMethod> candidates = findInsnsCalls(invocations.stream()
            .map(m -> new TargetPair(dirtyTargetClass, m)).toList(), context);

        // Attempt to find matching insns in lambdas
        if (candidates.isEmpty()) {
            List<TargetPair> nestedLambdas = invocations.stream()
                .flatMap(m -> MethodAnalyzer.findOwnIndyHandles(dirtyTargetClass, m).stream())
                .map(IndyHandle::method)
                .filter(MethodAnalyzer::isLambda)
                .map(m -> new TargetPair(dirtyTargetClass, m))
                .toList();
            return findInsnsCalls(nestedLambdas, context);
        }

        return candidates;
    }

    // Handle cases where only part of the method is moved away
    private static List<CandidateMethod> tryFindPartialCandidates(MethodNode cleanTargetMethod, ClassNode dirtyTargetClass, MethodNode dirtyTargetMethod, MixinContext context) {
        List<TargetPair> dirtyOnlyCalls = MethodAnalyzer.findDirtyOnlyCalls(cleanTargetMethod, dirtyTargetMethod, false).stream()
            .filter(i -> i.owner.equals(dirtyTargetClass.name))
            .map(i -> context.dirtyMethods().resolve(MethodQualifier.create(i)))
            .filter(Objects::nonNull)
            .toList();

        return findInsnsCalls(dirtyOnlyCalls, context);
    }

    // If multiple candidates have been found, try comparing the surrounding method instructions to find one match
    private static List<CandidateMethod> disambiguate(List<CandidateMethod> candidates, MixinContext context, TargetPair cleanTarget) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        List<AbstractInsnNode> cleanInsns = context.injections().find(cleanTarget);
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
                List<AbstractInsnNode> insns = context.injections().find(pair);
                return !insns.isEmpty() ? new CandidateMethod(pair.methodNode(), insns) : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private record CandidateMethod(MethodNode method, List<AbstractInsnNode> insns) {
    }
}
