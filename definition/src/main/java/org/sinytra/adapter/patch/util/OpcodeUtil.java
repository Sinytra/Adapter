package org.sinytra.adapter.patch.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;

public class OpcodeUtil {
    public static final List<Integer> INSN_TYPE_OFFSETS = List.of(Type.LONG, Type.FLOAT, Type.DOUBLE, Type.OBJECT);
    public static final List<Integer> INSN_TYPE_OFFSETS_EXTENDED = List.of(Type.LONG, Type.FLOAT, Type.DOUBLE, Type.OBJECT, Type.VOID);
    public static final Map<Type, BoxedType> BOXED_TYPES = Map.of(
        Type.BYTE_TYPE, new BoxedType(byte.class, Byte.class),
        Type.CHAR_TYPE, new BoxedType(char.class, Character.class),
        Type.BOOLEAN_TYPE, new BoxedType(boolean.class, Boolean.class),
        Type.SHORT_TYPE, new BoxedType(short.class, Short.class),
        Type.INT_TYPE, new BoxedType(int.class, Integer.class),
        Type.LONG_TYPE, new BoxedType(long.class, Long.class),
        Type.FLOAT_TYPE, new BoxedType(float.class, Float.class),
        Type.DOUBLE_TYPE, new BoxedType(double.class, Double.class)
    );

    public record BoxedType(Class<?> primitiveClass, Class<?> boxedClass) {}

    public static boolean isStoreOpcode(int opcode) {
        return opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
    }

    public static boolean isLoadOpcode(int opcode) {
        return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
    }

    public static boolean isReturnOpcode(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
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

    public static int getAccessVisibility(int access) {
        return access & 0x7;
    }

    public static int setAccessVisibility(int access, int visibility) {
        return access & ~0x7 | visibility;
    }
}
