package org.sinytra.adapter.patch.transformer.operation.unit;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ModifyMethodAccess(List<AccessChange> changes) implements MethodTransform {
    public record AccessChange(boolean add, int modifier) {}

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Patch.Result result = Patch.Result.PASS;
        for (AccessChange change : this.changes) {
            if (change.add) {
                if ((methodNode.access & change.modifier) == 0) {
                    methodContext.recordAudit(this, "Adding access modifier %s", change.modifier);
                    methodNode.access |= change.modifier;
                    result = Patch.Result.APPLY;
                    if (change.modifier == Opcodes.ACC_STATIC && methodContext.methodAnnotation().matchesDesc(MixinConstants.INJECT)) {
                        List<Type> types = methodContext.targetTypes();
                        if (types.size() == 1) {
                            Type[] params = Type.getArgumentTypes(methodNode.desc);
                            List<Type> newParams = new ArrayList<>(Arrays.asList(params));
                            newParams.addFirst(types.getFirst());

                            methodContext.updateDescription(this, newParams);
                        } else {
                            throw new IllegalStateException("Cannot automatically determine target instance type for mixin " + classNode.name);
                        }
                    }
                }
            } else {
                if ((methodNode.access & change.modifier) != 0) {
                    methodContext.recordAudit(this, "Removing access modifier %s", change.modifier);
                    methodNode.access &= ~change.modifier;
                    if (change.modifier == Opcodes.ACC_STATIC) {
                        LocalVariableNode firstParam = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();
                        // Offset everything by 1
                        for (LocalVariableNode lvn : methodNode.localVariables) {
                            lvn.index++;
                        }
                        for (AbstractInsnNode insn : methodNode.instructions) {
                            if (insn instanceof VarInsnNode varInsn) {
                                varInsn.var++;
                            }
                        }
                        // Insert instance local variable
                        methodNode.localVariables.add(new LocalVariableNode("this", Type.getObjectType(classNode.name).getDescriptor(), null, firstParam.start, firstParam.end, 0));
                        result = Patch.Result.COMPUTE_FRAMES;
                    } else {
                        result = Patch.Result.APPLY;
                    }
                }
            }
        }
        return result;
    }
}
