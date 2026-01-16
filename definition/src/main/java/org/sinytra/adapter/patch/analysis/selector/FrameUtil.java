package org.sinytra.adapter.patch.analysis.selector;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public class FrameUtil {
    public static Frame<SourceValue>[] getFrames(MethodNode methodNode) {
        try {
            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            return analyzer.analyze(Object.class.getName(), methodNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Helper to determine how many stack items an instruction consumes
    public static int getPopCount(AbstractInsnNode insn) {
        int op = insn.getOpcode();

        // Method Calls
        if (insn instanceof MethodInsnNode) {
            // Static: pops args
            // Virtual/Special/Interface: pops args + receiver (1)
            int args = org.objectweb.asm.Type.getArgumentsAndReturnSizes(((MethodInsnNode) insn).desc) >> 2;
            boolean isStatic = op == Opcodes.INVOKESTATIC;
            // INVOKEDYNAMIC is complex, but usually acts like static for the bootstrap
            if (op == org.objectweb.asm.Opcodes.INVOKEDYNAMIC) isStatic = true;

            return isStatic ? args : args + 1;
        }

        // Field Instructions
        if (insn instanceof FieldInsnNode) {
            // PUTFIELD pops [ref, value] (value size depends on type)
            // PUTSTATIC pops [value]
            // GETFIELD pops [ref] -> Returns 1
            // GETSTATIC pops [] -> Returns 0
            if (op == Opcodes.GETSTATIC) return 0;
            if (op == Opcodes.GETFIELD) return 1;

            // PUT logic requires checking field type size
            boolean isLongOrDouble = ((FieldInsnNode) insn).desc.matches("[JD]");
            int valSize = isLongOrDouble ? 2 : 1;
            if (op == Opcodes.PUTSTATIC) return valSize;
            if (op == Opcodes.PUTFIELD) return valSize + 1;
        }

        // Simple Opcodes (incomplete list, add others as needed)
        return switch (op) {
            case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV ->
                // ... (most binary ops)
                2;
            case Opcodes.INEG, Opcodes.I2L ->
                // ... (unary ops)
                1;
            case Opcodes.POP -> 1;
            case Opcodes.POP2 -> 2;
            case Opcodes.DUP -> 1; // Technically pops 1 to read it
            default -> 0;
        };
    }
}
