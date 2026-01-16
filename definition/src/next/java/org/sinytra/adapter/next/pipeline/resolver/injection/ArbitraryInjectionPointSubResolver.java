package org.sinytra.adapter.next.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;

public class ArbitraryInjectionPointSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinData mixin, MixinContext context, Recipe recipe) {
        String injectionPointTarget = recipe.clean().getAtData().getTarget().orElseThrow();
        MethodContext.TargetPair cleanTarget = recipe.getCleanTarget();
        MethodContext.TargetPair dirtyTarget = recipe.getDirtyTarget();
        AbstractInsnNode cleanInsn = context.methods().findInjectionTargetInsn(cleanTarget);
        if (cleanInsn == null) {
            return null;
        }

        MethodInsnNode targetMethodCall = resolveTargetCallInsn(dirtyTarget.methodNode(), cleanInsn, context, injectionPointTarget);
        if (targetMethodCall == null) return null;

        if (targetMethodCall.owner.equals(dirtyTarget.classNode().name)) {
            MethodContext.TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), MethodQualifier.create(targetMethodCall));
            if (target == null) return null;

            // New method is in the same class? It's possible our target injection point was moved there
            if (context.methods().hasInjectionTargetInsns(target)) {
                return MutableConfiguration.create().setTargetMethod(targetMethodCall);
            }
        }

        return MutableConfiguration.create().setAtData(new AtData(AT_VAL_INVOKE, targetMethodCall));
    }

    private MethodInsnNode resolveTargetCallInsn(MethodNode dirtyTargetMethod, AbstractInsnNode cleanInjectionInsn, MixinContext context, String injectionPointTarget) {
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
            if (insn instanceof FrameNode || insn instanceof LineNumberNode || insn.getOpcode() != firstOpcode) {
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

    private static MethodInsnNode findReplacementInjectionPoint(AbstractInsnNode lastInsn, UnaryOperator<AbstractInsnNode> flow, MixinContext context, String injectionPointTarget) {
        // Require matching return types for ModifyExpressionValue mixins
        // TODO Eliminate use of matchesDesc
        if (context.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
            Type desiredReturnType = Type.getReturnType(injectionPointTarget);
            return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, flow,
                v -> v instanceof MethodInsnNode minsn && Type.getReturnType(minsn.desc).equals(desiredReturnType));
        }
        return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, flow, v -> v instanceof MethodInsnNode);
    }
}
