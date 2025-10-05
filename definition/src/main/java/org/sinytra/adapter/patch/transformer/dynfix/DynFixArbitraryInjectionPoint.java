package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionPoint;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class DynFixArbitraryInjectionPoint implements DynamicFixer<DynFixArbitraryInjectionPoint.Data> {
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_ARG, MixinConstants.MODIFY_EXPR_VAL);

    public record Data(MethodContext.TargetPair dirtyTarget, AbstractInsnNode cleanInjectionInsn) {}

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesAny(ACCEPTED_ANNOTATIONS)) {
            MethodContext.TargetPair dirtyInjectionTarget = methodContext.findDirtyInjectionTarget();
            if (dirtyInjectionTarget == null) {
                return null;
            }
            MethodContext.TargetPair cleanInjectionTarget = methodContext.findCleanInjectionTarget();
            List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(cleanInjectionTarget);
            if (cleanInsns.size() == 1 && dirtyInjectionTarget != null && methodContext.failsDirtyInjectionCheck()) {
                AbstractInsnNode cleanInjectionInsn = cleanInsns.getFirst();
                return new Data(dirtyInjectionTarget, cleanInjectionInsn);
            }
        }
        return null;
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        MethodNode dirtyTargetMethod = data.dirtyTarget().methodNode();
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();

        AbstractInsnNode nextCallCandidate = findCandidates(MethodCallAnalyzer.findBackwardsInstructions(cleanInjectionInsn, 5).inverse(), dirtyTargetMethod, List::getLast);
        MethodInsnNode targetMethodCall;
        if (nextCallCandidate != null) {
            targetMethodCall = findReplacementInjectionPoint(nextCallCandidate, AbstractInsnNode::getNext, methodContext);
        } else {
            AbstractInsnNode previousCallCandidate = findCandidates(MethodCallAnalyzer.findForwardInstructions(cleanInjectionInsn, 5), dirtyTargetMethod, List::getFirst);
            if (previousCallCandidate != null) {
                targetMethodCall = findReplacementInjectionPoint(previousCallCandidate, AbstractInsnNode::getPrevious, methodContext);
            } else {
                return null;
            }
        }

        if (targetMethodCall != null) {
            // New method is in the same class? It's possible our target injection point was moved there
            if (targetMethodCall.owner.equals(data.dirtyTarget().classNode().name) && !methodContext.methodAnnotation().matchesDesc(MixinConstants.INJECT)) {
                return tryMoveTargetMethod(targetMethodCall, methodContext);
            }

            String newInjectionPoint = Type.getObjectType(targetMethodCall.owner).getDescriptor() + targetMethodCall.name + targetMethodCall.desc;
            return FixResult.of(new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false).apply(methodContext), PatchAuditTrail.Match.PARTIAL);
        }

        return null;
    }

    private static FixResult tryMoveTargetMethod(MethodInsnNode insn, MethodContext methodContext) {
        String newTarget = insn.name + insn.desc;
        return FixResult.of(new ModifyInjectionTarget(List.of(newTarget)).apply(methodContext), PatchAuditTrail.Match.PARTIAL);
    }

    private static AbstractInsnNode findCandidates(InstructionMatcher cleanMatcher, MethodNode dirtyTargetMethod, Function<List<AbstractInsnNode>, AbstractInsnNode> selector) {
        // Find an common instruction in the clean and dirty target methods
        if (cleanMatcher.after().isEmpty()) {
            return null;
        }
        int firstOpcode = cleanMatcher.after().getFirst().getOpcode();

        List<AbstractInsnNode> candidates = new ArrayList<>();

        for (int i = 0; i < dirtyTargetMethod.instructions.size(); i++) {
            AbstractInsnNode insn = dirtyTargetMethod.instructions.get(i);
            if (insn instanceof FrameNode || insn instanceof LineNumberNode || insn.getOpcode() != firstOpcode) {
                continue;
            }

            InstructionMatcher dirtyMatcher = MethodCallAnalyzer.findForwardInstructionsDirect(insn, 5);
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

    private static MethodInsnNode findReplacementInjectionPoint(AbstractInsnNode lastInsn, UnaryOperator<AbstractInsnNode> flow, MethodContext methodContext) {
        // Require matching return types for ModifyExpressionValue mixins
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
            Type desiredReturnType = Type.getReturnType(methodContext.getMixinMethod().desc);
            return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, flow, v -> v instanceof MethodInsnNode minsn && Type.getReturnType(minsn.desc).equals(desiredReturnType));
        } else {
            return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, flow, v -> v instanceof MethodInsnNode);
        }
    }
}
