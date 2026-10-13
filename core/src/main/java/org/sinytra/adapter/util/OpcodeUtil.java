package org.sinytra.adapter.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;


public class OpcodeUtil {
    public static boolean isStoreOpcode(int opcode) {
        return opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
    }

    public static boolean isLoadOpcode(int opcode) {
        return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
    }

    public static boolean isReturnOpcode(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
    }

    public static int getLoadOpcode(Type type) {
        return type.getOpcode(Opcodes.ILOAD);
    }

    public static int getReturnOpcode(MethodNode methodNode) {
        return getReturnOpcode(Type.getReturnType(methodNode.desc));
    }

    public static int getReturnOpcode(Type type) {
        return type.getOpcode(Opcodes.IRETURN);
    }

    public static int getAccessVisibility(int access) {
        return access & 0x7;
    }

    public static int setAccessVisibility(int access, int visibility) {
        return access & ~0x7 | visibility;
    }
}
