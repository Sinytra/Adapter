package org.sinytra.adapter.analysis.selector;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
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

    public static int getPopCount(AbstractInsnNode insn) {
        int op = insn.getOpcode();

        if (insn instanceof MethodInsnNode minsn) {
            int args = Type.getArgumentCount(minsn.desc);
            boolean isStatic = op == Opcodes.INVOKESTATIC;
            if (op == Opcodes.INVOKEDYNAMIC) isStatic = true;

            return isStatic ? args : args + 1;
        }

        if (insn instanceof InvokeDynamicInsnNode indy) {
            return Type.getArgumentCount(indy.desc);
        }

        if (insn instanceof FieldInsnNode) {
            if (op == Opcodes.GETSTATIC) return 0;
            if (op == Opcodes.GETFIELD) return 1;
            if (op == Opcodes.PUTSTATIC) return 1;
            if (op == Opcodes.PUTFIELD) return 2;
        }

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
