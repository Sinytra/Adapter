package org.sinytra.adapter.next.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;

/**
 * Find our new injection point with relation to variable assignments
 */
public class AtVariableAssignStoreSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinData mixin, MixinContext context, Recipe recipe) {
        if (!recipe.clean().getAtData().getValue().equals(AT_VAL_INVOKE)) return null;

        MethodContext.TargetPair cleanPair = recipe.getCleanTarget();
        AbstractInsnNode cleanInsn = context.methods().findInjectionTargetInsn(cleanPair);
        if (cleanInsn == null) return null;

        MethodContext.TargetPair dirtyPair = recipe.getDirtyTarget();
        if (dirtyPair == null) return null;

        // Check that the following instruction is a store operation
        AbstractInsnNode next = findNextUsefulInsn(cleanInsn);
        if (!(next instanceof VarInsnNode varInsn) || !OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
            return null;
        }
        // Find matching local in dirty target method
        LocalVariableNode cleanLocal = context.legacy().cleanLocalsTable().getByIndexOrNull(varInsn.var);
        if (cleanLocal == null) {
            return null;
        }
        List<LocalVariableNode> cleanLocals = context.legacy().cleanLocalsTable().getForType(cleanLocal);
        List<LocalVariableNode> dirtyLocals = context.legacy().dirtyLocalsTable().getForType(cleanLocal);
        if (cleanLocals.size() != dirtyLocals.size()) {
            return null;
        }
        LocalVariableNode dirtyLocal = dirtyLocals.get(cleanLocals.indexOf(cleanLocal));
        // Find store insns
        List<AbstractInsnNode> cleanStoreInsns = findStoreInsns(cleanPair.methodNode().instructions, cleanLocal.index);
        int cleanStoreInsnIndex = cleanStoreInsns.indexOf(varInsn);
        if (cleanStoreInsnIndex == -1) {
            return null;
        }
        List<AbstractInsnNode> dirtyStoreInsns = findStoreInsns(dirtyPair.methodNode().instructions, dirtyLocal.index);
        if (cleanStoreInsns.size() != dirtyStoreInsns.size()) {
            return null;
        }
        AbstractInsnNode dirtyStoreInsn = dirtyStoreInsns.get(cleanStoreInsnIndex);
        // Find first method call before dirty store
        MethodInsnNode previousMethodCall = (MethodInsnNode) AdapterUtil.iterateInsns(dirtyStoreInsn, AbstractInsnNode::getPrevious, i -> i instanceof MethodInsnNode);
        if (previousMethodCall == null) {
            return null;
        }

        if (context.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            // In case the mixin is call-sensitive, we try to keep the orignal injection point if the method was moved
            if (!previousMethodCall.owner.equals(dirtyPair.classNode().name)) {
                return null;
            }
        }

        // All checks have passed, proceed to patch method
        return MutableConfiguration.create()
            .setAtData(recipe.clean().getAtData().withTarget(previousMethodCall));
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
