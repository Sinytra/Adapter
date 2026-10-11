package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.InsnComparator;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;

public class ArbitraryInjectionPointSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        String injectionPointTarget = recipe.clean().getAtData().getTarget().orElse(null);
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        AbstractInsnNode cleanInsn = recipe.getCleanInjectionInsn();
        if (dirtyTarget == null || cleanInsn == null) return null;

        MethodInsnNode targetMethodCall = resolveTargetCallInsn(dirtyTarget.methodNode(), cleanInsn, context, injectionPointTarget);
        if (targetMethodCall == null) return null;

        if (targetMethodCall.owner.equals(dirtyTarget.classNode().name)) {
            TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), MethodQualifier.create(targetMethodCall));
            if (target == null) return null;

            // New method is in the same class? It's possible our target injection point was moved there
            if (context.methods().hasInjectionTargetInsns(target)) {
                return MutableConfiguration.create().setTargetMethod(targetMethodCall);
            }
        }

        return MutableConfiguration.create().setAtData(AtData.create(AT_VAL_INVOKE, targetMethodCall));
    }

    private MethodInsnNode resolveTargetCallInsn(MethodNode dirtyTargetMethod, AbstractInsnNode cleanInjectionInsn, MixinContext context, @Nullable String injectionPointTarget) {
        AbstractInsnNode nextCallCandidate = findCandidates(MethodInsnMatcher.findBackwardsInstructions(cleanInjectionInsn).inverse(), dirtyTargetMethod, List::getLast);

        if (nextCallCandidate != null) {
            return findReplacementInjectionPoint(nextCallCandidate, AbstractInsnNode::getNext, context, injectionPointTarget);
        } else {
            AbstractInsnNode previousCallCandidate = findCandidates(MethodInsnMatcher.findForwardInstructions(cleanInjectionInsn), dirtyTargetMethod, List::getFirst);
            if (previousCallCandidate != null) {
                return findReplacementInjectionPoint(previousCallCandidate, AbstractInsnNode::getPrevious, context, injectionPointTarget);
            }
        }

        return null;
    }

    private static AbstractInsnNode findCandidates(InstructionMatcher cleanMatcher, MethodNode dirtyTargetMethod, Function<List<AbstractInsnNode>, AbstractInsnNode> selector) {
        // Find an common instruction in the clean and dirty target methods
        if (cleanMatcher.after().isEmpty()) return null;
        int firstOpcode = cleanMatcher.after().getFirst().getOpcode();
        List<AbstractInsnNode> candidates = new ArrayList<>();

        for (AbstractInsnNode insn : dirtyTargetMethod.instructions) {
            if (InsnTraversal.isDebugNode(insn) || insn.getOpcode() != firstOpcode) {
                continue;
            }

            InstructionMatcher dirtyMatcher = MethodInsnMatcher.findForwardInstructionsDirect(insn);
            if (cleanMatcher.test(dirtyMatcher, InsnComparator.IGNORE_VAR_INDEX)) {
                // Find first method call past matched instruction
                AbstractInsnNode lastInsn = selector.apply(dirtyMatcher.after());
                if (lastInsn != null) {
                    candidates.add(lastInsn);
                }
            }
        }

        return !candidates.isEmpty() ? candidates.getFirst() : null;
    }

    private static MethodInsnNode findReplacementInjectionPoint(AbstractInsnNode lastInsn, UnaryOperator<AbstractInsnNode> flow, MixinContext context, @Nullable String injectionPointTarget) {
        // Require matching return types for ModifyExpressionValue mixins
        if (context.hasFlag(MixinFlag.RETURN_TYPE_SENSITIVE) && injectionPointTarget != null) {
            Type desiredReturnType = Type.getReturnType(injectionPointTarget);
            return (MethodInsnNode) InsnTraversal.find(lastInsn, flow,
                v -> v instanceof MethodInsnNode minsn && Type.getReturnType(minsn.desc).equals(desiredReturnType));
        }
        return (MethodInsnNode) InsnTraversal.find(lastInsn, flow, v -> v instanceof MethodInsnNode);
    }
}
