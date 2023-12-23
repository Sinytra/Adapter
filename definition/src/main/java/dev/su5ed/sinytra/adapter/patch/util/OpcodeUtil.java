package dev.su5ed.sinytra.adapter.patch.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class OpcodeUtil {
    public static final List<Integer> INSN_TYPE_OFFSETS = List.of(Type.LONG, Type.FLOAT, Type.DOUBLE, Type.OBJECT);
    public static final List<Integer> INSN_TYPE_OFFSETS_EXTENDED = List.of(Type.LONG, Type.FLOAT, Type.DOUBLE, Type.OBJECT, Type.VOID);

    public static boolean isStoreOpcode(int opcode) {
        return opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
    }

    public static boolean isLoadOpcode(int opcode) {
        return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
    }

    public static int getLoadOpcode(int sort) {
        return Opcodes.ILOAD + INSN_TYPE_OFFSETS.indexOf(sort) + 1;
    }

    public static int getStoreOpcode(int sort) {
        return Opcodes.ISTORE + INSN_TYPE_OFFSETS.indexOf(sort) + 1;
    }

    public static int getReturnOpcode(MethodNode methodNode) {
        return getReturnOpcode(Type.getReturnType(methodNode.desc).getSort());
    }

    public static int getReturnOpcode(int sort) {
        return Opcodes.IRETURN + INSN_TYPE_OFFSETS_EXTENDED.indexOf(sort) + 1;
    }
}
