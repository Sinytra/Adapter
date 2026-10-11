package org.sinytra.adapter.patch.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.mixin.MixinFlag;
import org.sinytra.adapter.patch.resolver.SubResolver;
import org.sinytra.adapter.util.OpcodeUtil;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.env.ctx.Targets;

import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;

/**
 * Find our new injection point with relation to variable assignments
 */
public class AtVariableAssignStoreSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        if (!recipe.hasInjectionPointValue(AT_VAL_INVOKE)) return null;

        Targets targets = recipe.targets();
        if (targets == null) return null;
        AbstractInsnNode cleanInsn = recipe.getCleanInjectionInsn();
        if (cleanInsn == null) return null;

        // Check that the following instruction is a store operation
        AbstractInsnNode next = findNextUsefulInsn(cleanInsn);
        if (!(next instanceof VarInsnNode varInsn) || !OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
            return null;
        }
        // Find matching local in dirty target method
        LocalVariableLookup cleanTable = recipe.cleanLocalsTable();
        LocalVariableLookup dirtyTable = recipe.dirtyLocalsTable();
        if (cleanTable == null || dirtyTable == null) {
            return null;
        }
        LocalVariableNode cleanLocal = cleanTable.getByIndexOrNull(varInsn.var);
        if (cleanLocal == null) {
            return null;
        }
        List<LocalVariableNode> cleanLocals = cleanTable.getForType(cleanLocal);
        List<LocalVariableNode> dirtyLocals = dirtyTable.getForType(cleanLocal);
        if (cleanLocals.size() != dirtyLocals.size()) {
            return null;
        }
        LocalVariableNode dirtyLocal = dirtyLocals.get(cleanLocals.indexOf(cleanLocal));
        // Find store insns
        List<VarInsnNode> cleanStoreInsns = LocalVarAnalyzer.findStoreInsns(targets.clean().methodNode(), cleanLocal.index);
        int cleanStoreInsnIndex = cleanStoreInsns.indexOf(varInsn);
        if (cleanStoreInsnIndex == -1) {
            return null;
        }
        List<VarInsnNode> dirtyStoreInsns = LocalVarAnalyzer.findStoreInsns(targets.dirty().methodNode(), dirtyLocal.index);
        if (cleanStoreInsns.size() != dirtyStoreInsns.size()) {
            return null;
        }
        AbstractInsnNode dirtyStoreInsn = dirtyStoreInsns.get(cleanStoreInsnIndex);
        // Find first method call before dirty store
        MethodInsnNode previousMethodCall = (MethodInsnNode) InsnTraversal.find(dirtyStoreInsn, InsnTraversal.BACKWARD, i -> i instanceof MethodInsnNode);
        if (previousMethodCall == null) {
            return null;
        }

        // In case the mixin is call-sensitive, we try to keep the orignal injection point if the method was moved
        if (context.hasFlag(MixinFlag.AT_TARGET_SENSITIVE) && !previousMethodCall.owner.equals(targets.dirty().classNode().name)) {
            return null;
        }

        // All checks have passed, proceed to patch method
        return MutableConfiguration.create()
            .setAtData(recipe.clean().getAtData().withTarget(previousMethodCall));
    }

    private static AbstractInsnNode findNextUsefulInsn(AbstractInsnNode insn) {
        return InsnTraversal.find(insn, InsnTraversal.FORWARD, i -> !(i instanceof TypeInsnNode || i instanceof LabelNode || InsnTraversal.isDebugNode(i)));
    }
}
