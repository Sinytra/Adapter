package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DynFixArbitraryInjectionPoint implements DynamicFixer<DynFixArbitraryInjectionPoint.Data> {
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_EXPR_VAL);

    public record Data(MethodContext.TargetPair dirtyTarget, AbstractInsnNode cleanInjectionInsn) {
    }

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesAny(ACCEPTED_ANNOTATIONS)) {
            MethodContext.TargetPair cleanInjectionTarget = methodContext.findCleanInjectionTarget();
            List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(cleanInjectionTarget);
            if (cleanInsns.size() == 1 && methodContext.failsDirtyInjectionCheck()) {
                MethodContext.TargetPair dirtyInjectionTarget = methodContext.findDirtyInjectionTarget();
                return new Data(dirtyInjectionTarget, cleanInsns.getFirst());
            }
        }
        return null;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data) {
        MethodNode dirtyTargetMethod = data.dirtyTarget().methodNode();
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();

        // Find an common instruction in the clean and dirty target methods
        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findBackwardsInstructions(cleanInjectionInsn, 5).inverse();
        if (cleanMatcher.after().isEmpty()) {
            return Patch.Result.PASS;
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
                AbstractInsnNode lastInsn = dirtyMatcher.after().getLast();
                if (lastInsn != null) {
                    candidates.add(lastInsn);
                }
            }
        }

        if (candidates.size() == 1) {
            AbstractInsnNode lastInsn = candidates.getFirst();
            MethodInsnNode nextMethodCall = findReplacementInjectionPoint(lastInsn, methodContext);
            if (nextMethodCall != null) {
                String newInjectionPoint = Type.getObjectType(nextMethodCall.owner).getDescriptor() + nextMethodCall.name + nextMethodCall.desc;
                return new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false).apply(classNode, methodNode, methodContext);   
            }
        }

        return Patch.Result.PASS;
    }

    private static MethodInsnNode findReplacementInjectionPoint(AbstractInsnNode lastInsn, MethodContext methodContext) {
        // Require matching return types for ModifyExpressionValue mixins
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
            Type desiredReturnType = Type.getReturnType(methodContext.getInjectionPointMethodQualifier().desc());
            return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, AbstractInsnNode::getNext, v -> v instanceof MethodInsnNode minsn && Type.getReturnType(minsn.desc).equals(desiredReturnType));
        } else {
            return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, AbstractInsnNode::getNext, v -> v instanceof MethodInsnNode);
        }
    }
}
