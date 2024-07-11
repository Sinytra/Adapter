package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.List;

public class DynFixArbitraryInjectionPoint implements DynamicFixer<DynFixArbitraryInjectionPoint.Data> {
    public record Data(MethodContext.TargetPair dirtyTarget, AbstractInsnNode cleanInjectionInsn) {
    }

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.INJECT)) {
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
        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findBackwardsInstructions(cleanInjectionInsn, 5, false).inverse();
        if (cleanMatcher.after().isEmpty()) {
            return Patch.Result.PASS;
        }
        int firstOpcode = cleanMatcher.after().getFirst().getOpcode();

        for (int i = 0; i < dirtyTargetMethod.instructions.size(); i++) {
            AbstractInsnNode insn = dirtyTargetMethod.instructions.get(i);
            if (insn.getOpcode() != firstOpcode) {
                continue;
            }

            InstructionMatcher dirtyMatcher = MethodCallAnalyzer.findForwardInstructions(insn, 5, false);
            if (cleanMatcher.test(dirtyMatcher, InsnComparator.IGNORE_VAR_INDEX)) {
                // Find first method call past matched instruction
                AbstractInsnNode lastInsn = dirtyMatcher.after().getLast();
                if (lastInsn != null) {
                    MethodInsnNode nextMethodCall = (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, AbstractInsnNode::getNext, v -> v instanceof MethodInsnNode);
                    String newInjectionPoint = Type.getObjectType(nextMethodCall.owner).getDescriptor() + nextMethodCall.name + nextMethodCall.desc;
                    return new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false).apply(classNode, methodNode, methodContext);
                }
            }
        }

        return Patch.Result.PASS;
    }
}
