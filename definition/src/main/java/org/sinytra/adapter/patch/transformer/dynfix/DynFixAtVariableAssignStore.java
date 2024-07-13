package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import org.sinytra.adapter.patch.transformer.ModifyInjectionTarget;
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
            if (cleanInsns.size() == 1) {
                MethodContext.TargetPair dirtyInjectionTarget = methodContext.findDirtyInjectionTarget();
                return new Data(cleanInjectionTarget.methodNode(), dirtyInjectionTarget, cleanInsns.getFirst());
            }
        }
        return null;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data) {
        MethodNode cleanTargetMethod = data.cleanTargetMethod();
        MethodNode dirtyTargetMethod = data.dirtyTarget().methodNode();
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();

        // Check that the following instruction is a store operation
        AbstractInsnNode next = findNextUsefulInsn(cleanInjectionInsn);
        if (!(next instanceof VarInsnNode varInsn) || !OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
            return Patch.Result.PASS;
        }
        // Find matching local in dirty target method
        LocalVariableNode cleanLocal = methodContext.cleanLocalsTable().getByIndexOrNull(varInsn.var);
        if (cleanLocal == null) {
            return Patch.Result.PASS;
        }
        List<LocalVariableNode> cleanLocals = methodContext.cleanLocalsTable().getForType(cleanLocal);
        List<LocalVariableNode> dirtyLocals = methodContext.dirtyLocalsTable().getForType(cleanLocal);
        if (cleanLocals.size() != dirtyLocals.size()) {
            return Patch.Result.PASS;
        }
        LocalVariableNode dirtyLocal = dirtyLocals.get(cleanLocals.indexOf(cleanLocal));
        // Find store insns
        List<AbstractInsnNode> cleanStoreInsns = findStoreInsns(cleanTargetMethod.instructions, cleanLocal.index);
        int cleanStoreInsnIndex = cleanStoreInsns.indexOf(varInsn);
        if (cleanStoreInsnIndex == -1) {
            return Patch.Result.PASS;
        }
        List<AbstractInsnNode> dirtyStoreInsns = findStoreInsns(dirtyTargetMethod.instructions, dirtyLocal.index);
        if (cleanStoreInsns.size() != dirtyStoreInsns.size()) {
            return Patch.Result.PASS;
        }
        AbstractInsnNode dirtyStoreInsn = dirtyStoreInsns.get(cleanStoreInsnIndex);
        // Find first method call before dirty store
        MethodInsnNode previousMethodCall = (MethodInsnNode) AdapterUtil.iterateInsns(dirtyStoreInsn, AbstractInsnNode::getPrevious, i -> i instanceof MethodInsnNode);
        if (previousMethodCall == null) {
            return Patch.Result.PASS;
        }
        
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            return handleWrapAnnotation(classNode, methodNode, methodContext, data, previousMethodCall);
        }
        
        // All checks have passed, proceed to patch method
        String newInjectionPoint = Type.getObjectType(previousMethodCall.owner).getDescriptor() + previousMethodCall.name + previousMethodCall.desc;
        return new ModifyInjectionPoint((String) null, newInjectionPoint, true, true)
            .apply(classNode, methodNode, methodContext);
    }

    // In case the mixin is call-sensitive, we try to keep the orignal injection point if the method was moved
    private static Patch.Result handleWrapAnnotation(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data, MethodInsnNode previousMethodCall) {
        if (previousMethodCall.owner.equals(data.dirtyTarget().classNode().name)) {
            String newTarget = previousMethodCall.name + previousMethodCall.desc;
            return new ModifyInjectionTarget(List.of(newTarget)).apply(classNode, methodNode, methodContext);
        }
        return Patch.Result.PASS;
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
