package org.sinytra.adapter.analysis.flow;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.tree.analysis.Value;

import java.util.ArrayList;
import java.util.List;

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

    public static List<SourceValue> getOperands(Frame<SourceValue> frame, AbstractInsnNode insn) {
        int stackSize = frame.getStackSize();
        int start = Math.max(0, stackSize - getPopCount(insn, frame));
        List<SourceValue> operands = new ArrayList<>(stackSize - start);
        for (int i = start; i < stackSize; i++) {
            operands.add(frame.getStack(i));
        }
        return operands;
    }

    public static int getPopCount(AbstractInsnNode insn) {
        return getPopCount(insn, null);
    }

    public static int getPopCount(AbstractInsnNode insn, @Nullable Frame<? extends Value> frame) {
        int op = insn.getOpcode();

        if (insn instanceof MethodInsnNode minsn) {
            int args = Type.getArgumentCount(minsn.desc);
            return op == Opcodes.INVOKESTATIC ? args : args + 1;
        }

        if (insn instanceof InvokeDynamicInsnNode indy) {
            return Type.getArgumentCount(indy.desc);
        }

        if (insn instanceof MultiANewArrayInsnNode multiANewArray) {
            return multiANewArray.dims;
        }

        return switch (op) {
            case Opcodes.POP2, Opcodes.DUP2 -> isWide(frame, 0) ? 1 : 2;
            case Opcodes.DUP_X2 -> isWide(frame, 1) ? 2 : 3;
            case Opcodes.DUP2_X1 -> isWide(frame, 0) ? 2 : 3;
            case Opcodes.DUP2_X2 -> {
                if (isWide(frame, 0)) {
                    yield isWide(frame, 1) ? 2 : 3;
                }
                yield isWide(frame, 2) ? 3 : 4;
            }

            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE,
                 Opcodes.SASTORE -> 3;

            case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB,
                 Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL, Opcodes.IDIV, Opcodes.LDIV,
                 Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM, Opcodes.ISHL,
                 Opcodes.LSHL, Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR, Opcodes.IAND, Opcodes.LAND,
                 Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR, Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG,
                 Opcodes.DCMPL, Opcodes.DCMPG,
                 Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD,
                 Opcodes.SALOAD,
                 Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                 Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE,
                 Opcodes.PUTFIELD, Opcodes.SWAP, Opcodes.DUP_X1 -> 2;

            case Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L,
                 Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.INEG,
                 Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.CHECKCAST, Opcodes.INSTANCEOF,
                 Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE, Opcodes.POP, Opcodes.DUP,
                 Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL,
                 Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH,
                 Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.ATHROW,
                 Opcodes.MONITORENTER, Opcodes.MONITOREXIT, Opcodes.ARRAYLENGTH, Opcodes.NEWARRAY, Opcodes.ANEWARRAY,
                 Opcodes.GETFIELD, Opcodes.PUTSTATIC -> 1;

            default -> 0;
        };
    }

    private static boolean isWide(@Nullable Frame<? extends Value> frame, int depth) {
        if (frame == null) {
            return false;
        }
        int index = frame.getStackSize() - 1 - depth;
        return index >= 0 && frame.getStack(index).getSize() == 2;
    }
}
