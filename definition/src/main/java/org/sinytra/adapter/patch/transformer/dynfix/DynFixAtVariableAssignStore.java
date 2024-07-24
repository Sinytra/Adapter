package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.operation.ModifyInjectionPoint;
import org.sinytra.adapter.patch.transformer.operation.ModifyInjectionTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Find our new injection point with relation to variable assignments
 */
public class DynFixAtVariableAssignStore implements DynamicFixer<DynFixAtVariableAssignStore.Data> {
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.WRAP_OPERATION);

    public record Data(MethodNode cleanTargetMethod, MethodContext.TargetPair dirtyTarget, AbstractInsnNode cleanInjectionInsn) {}

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesAny(ACCEPTED_ANNOTATIONS) && methodContext.hasInjectionPointValue("INVOKE")) {
            MethodContext.TargetPair cleanInjectionTarget = methodContext.findCleanInjectionTarget();
            List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(cleanInjectionTarget);
            if (cleanInsns.size() != 1) {
                return null;
            }
            MethodContext.TargetPair dirtyInjectionTarget = methodContext.findDirtyInjectionTarget();
            if (dirtyInjectionTarget == null) {
                return null;
            }
            return new Data(cleanInjectionTarget.methodNode(), dirtyInjectionTarget, cleanInsns.getFirst());
        }
        return null;
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        MethodNode cleanTargetMethod = data.cleanTargetMethod();
        MethodNode dirtyTargetMethod = data.dirtyTarget().methodNode();
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();

        // Check that the following instruction is a store operation
        AbstractInsnNode next = findNextUsefulInsn(cleanInjectionInsn);
        if (!(next instanceof VarInsnNode varInsn) || !OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
            return null;
        }
        // Find matching local in dirty target method
        LocalVariableNode cleanLocal = methodContext.cleanLocalsTable().getByIndexOrNull(varInsn.var);
        if (cleanLocal == null) {
            return null;
        }
        List<LocalVariableNode> cleanLocals = methodContext.cleanLocalsTable().getForType(cleanLocal);
        List<LocalVariableNode> dirtyLocals = methodContext.dirtyLocalsTable().getForType(cleanLocal);
        if (cleanLocals.size() != dirtyLocals.size()) {
            return null;
        }
        LocalVariableNode dirtyLocal = dirtyLocals.get(cleanLocals.indexOf(cleanLocal));
        // Find store insns
        List<AbstractInsnNode> cleanStoreInsns = findStoreInsns(cleanTargetMethod.instructions, cleanLocal.index);
        int cleanStoreInsnIndex = cleanStoreInsns.indexOf(varInsn);
        if (cleanStoreInsnIndex == -1) {
            return null;
        }
        List<AbstractInsnNode> dirtyStoreInsns = findStoreInsns(dirtyTargetMethod.instructions, dirtyLocal.index);
        if (cleanStoreInsns.size() != dirtyStoreInsns.size()) {
            return null;
        }
        AbstractInsnNode dirtyStoreInsn = dirtyStoreInsns.get(cleanStoreInsnIndex);
        // Find first method call before dirty store
        MethodInsnNode previousMethodCall = (MethodInsnNode) AdapterUtil.iterateInsns(dirtyStoreInsn, AbstractInsnNode::getPrevious, i -> i instanceof MethodInsnNode);
        if (previousMethodCall == null) {
            return null;
        }

        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            return handleWrapAnnotation(methodContext, data, previousMethodCall);
        }

        // All checks have passed, proceed to patch method
        String newInjectionPoint = Type.getObjectType(previousMethodCall.owner).getDescriptor() + previousMethodCall.name + previousMethodCall.desc;
        return FixResult.of(new ModifyInjectionPoint((String) null, newInjectionPoint, true, true)
            .apply(methodContext), PatchAuditTrail.Match.FULL);
    }

    // In case the mixin is call-sensitive, we try to keep the orignal injection point if the method was moved
    @Nullable
    private static FixResult handleWrapAnnotation(MethodContext methodContext, Data data, MethodInsnNode previousMethodCall) {
        if (previousMethodCall.owner.equals(data.dirtyTarget().classNode().name)) {
            String newTarget = previousMethodCall.name + previousMethodCall.desc;
            return FixResult.of(new ModifyInjectionTarget(List.of(newTarget)).apply(methodContext), PatchAuditTrail.Match.FULL);
        }
        return null;
    }

    private static List<AbstractInsnNode> findStoreInsns(InsnList insns, int index) {
        List<AbstractInsnNode> list = new ArrayList<>();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == index && OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
                list.add(insn);
            }
        }
        return list;
    }

    private static AbstractInsnNode findNextUsefulInsn(AbstractInsnNode insn) {
        return AdapterUtil.iterateInsns(insn, AbstractInsnNode::getNext, i -> !(i instanceof TypeInsnNode || i instanceof FrameNode || i instanceof LineNumberNode || i instanceof LabelNode));
    }
}
