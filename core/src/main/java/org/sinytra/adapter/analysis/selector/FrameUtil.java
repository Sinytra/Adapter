package org.sinytra.adapter.analysis.selector;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.method.AdvancedSourceInterpreter;

public class FrameUtil {
    public static Frame<SourceValue>[] getFrames(MethodNode methodNode) {
        try {
            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            return analyzer.analyze(Object.class.getName(), methodNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Frame<SourceValue>[] getStableFrames(MethodNode methodNode) {
        try {
            Analyzer<SourceValue> analyzer = new Analyzer<>(new AdvancedSourceInterpreter(true));
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
            int args = Type.getArgumentsAndReturnSizes(((MethodInsnNode) insn).desc) >> 2;
            boolean isStatic = op == Opcodes.INVOKESTATIC;
            // INVOKEDYNAMIC is complex, but usually acts like static for the bootstrap
            if (op == Opcodes.INVOKEDYNAMIC) isStatic = true;

            return isStatic ? args - 1 : args;
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
            case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB,
                 Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL, Opcodes.IDIV, Opcodes.LDIV,
                 Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM, Opcodes.ISHL,
                 Opcodes.LSHL, Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR, Opcodes.IAND, Opcodes.LAND,
                 Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR, Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG,
                 Opcodes.DCMPL, Opcodes.DCMPG -> 2;

            case Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L,
                 Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.INEG,
                 Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.CHECKCAST -> 1;

            default -> 0;
        };
    }
}
