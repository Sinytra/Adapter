package org.sinytra.adapter.analysis.method;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdvancedSourceInterpreter extends SourceInterpreter {
    private final boolean stable;

    public AdvancedSourceInterpreter(boolean stable) {
        super(Opcodes.ASM9);
        this.stable = stable;
    }

    private static boolean isStackOp(int opcode) {
        return opcode == Opcodes.DUP
            || opcode == Opcodes.DUP_X1
            || opcode == Opcodes.DUP_X2
            || opcode == Opcodes.DUP2
            || opcode == Opcodes.DUP2_X1
            || opcode == Opcodes.DUP2_X2
            || opcode == Opcodes.SWAP;
    }

    private SourceValue copyWithoutStackOps(SourceValue v) {
        if (!this.stable || v.insns == null || v.insns.isEmpty()) {
            return v;
        }

        Set<AbstractInsnNode> filtered = new HashSet<>();
        for (AbstractInsnNode insn : v.insns) {
            if (!isStackOp(insn.getOpcode())) {
                filtered.add(insn);
            }
        }

        // If everything was filtered out, keep the original
        if (filtered.isEmpty()) {
            return v;
        }

        return new SourceValue(v.size, filtered);
    }

    // Stack operations: never introduce new producers
    @Override
    public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
        if (!this.stable || value.insns.isEmpty()) {
            return super.copyOperation(insn, value);
        }
        return copyWithoutStackOps(value);
    }

    @Override
    public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
        return super.unaryOperation(insn, copyWithoutStackOps(value));
    }

    @Override
    public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2) {
        return super.binaryOperation(
            insn,
            copyWithoutStackOps(v1),
            copyWithoutStackOps(v2)
        );
    }

    @Override
    public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2, SourceValue v3) {
        return super.ternaryOperation(
            insn,
            copyWithoutStackOps(v1),
            copyWithoutStackOps(v2),
            copyWithoutStackOps(v3)
        );
    }

    @Override
    public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
        List<SourceValue> cleaned = new ArrayList<>(values.size());
        for (SourceValue v : values) {
            cleaned.add(copyWithoutStackOps(v));
        }
        return super.naryOperation(insn, cleaned);
    }

    // Always prefer original producers
    @Override
    public SourceValue merge(SourceValue v1, SourceValue v2) {
        if (!this.stable) {
            return super.merge(v1, v2);
        }
        
        if (v1 == v2) {
            return v1;
        }

        SourceValue c1 = copyWithoutStackOps(v1);
        SourceValue c2 = copyWithoutStackOps(v2);

        // If one side lost all stack ops and the other didn't, prefer the cleaner one
        if (c1.insns != v1.insns && c2.insns == v2.insns) {
            return c1;
        }
        if (c2.insns != v2.insns && c1.insns == v1.insns) {
            return c2;
        }

        return super.merge(c1, c2);
    }
}

