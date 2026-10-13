package org.sinytra.adapter.model.param;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.List;

public record RedirectedInsn(List<Type> operands, Type result) {
    @Nullable
    public static RedirectedInsn of(AbstractInsnNode insn) {
        return switch (insn) {
            case MethodInsnNode call -> new RedirectedInsn(
                Parameters.getCallOperandTypes(call.getOpcode() == Opcodes.INVOKESTATIC ? null : Type.getObjectType(call.owner), call.desc),
                Type.getReturnType(call.desc)
            );
            case FieldInsnNode field -> {
                Type owner = Type.getObjectType(field.owner);
                Type type = Type.getType(field.desc);
                yield switch (field.getOpcode()) {
                    case Opcodes.GETFIELD -> new RedirectedInsn(List.of(owner), type);
                    case Opcodes.GETSTATIC -> new RedirectedInsn(List.of(), type);
                    case Opcodes.PUTFIELD -> new RedirectedInsn(List.of(owner, type), Type.VOID_TYPE);
                    case Opcodes.PUTSTATIC -> new RedirectedInsn(List.of(type), Type.VOID_TYPE);
                    default -> null;
                };
            }
            case TypeInsnNode creation when creation.getOpcode() == Opcodes.NEW -> {
                MethodInsnNode constructor = findConstructorCall(creation);
                yield constructor != null ? new RedirectedInsn(Parameters.getParameterTypes(constructor.desc), Type.getObjectType(creation.desc)) : null;
            }
            default -> null;
        };
    }

    @Nullable
    public static MethodInsnNode findConstructorCall(TypeInsnNode creation) {
        int depth = 0;
        for (AbstractInsnNode insn = creation.getNext(); insn != null; insn = insn.getNext()) {
            if (insn instanceof TypeInsnNode nested && nested.getOpcode() == Opcodes.NEW && nested.desc.equals(creation.desc)) {
                depth++;
            } else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>")
                && call.owner.equals(creation.desc)) {
                if (depth == 0) {
                    return call;
                }
                depth--;
            }
        }
        return null;
    }
}
