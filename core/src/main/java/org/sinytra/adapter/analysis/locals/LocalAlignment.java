package org.sinytra.adapter.analysis.locals;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;

import java.util.function.UnaryOperator;

public final class LocalAlignment {
    private final MethodAlignment alignment;

    private LocalAlignment(MethodAlignment alignment) {
        this.alignment = alignment;
    }

    public static LocalAlignment of(MethodAlignment alignment) {
        return new LocalAlignment(alignment);
    }

    public int resolve(int cleanSlot, AbstractInsnNode cleanInsn) {
        int slot = findAlignedUse(cleanSlot, cleanInsn, AbstractInsnNode::getNext);
        return slot != -1 ? slot : findAlignedUse(cleanSlot, cleanInsn.getPrevious(), AbstractInsnNode::getPrevious);
    }

    private int findAlignedUse(int slot, @Nullable AbstractInsnNode start, UnaryOperator<AbstractInsnNode> direction) {
        for (AbstractInsnNode insn = start; insn != null; insn = direction.apply(insn)) {
            if (getSlot(insn) == slot) {
                AbstractInsnNode dirty = this.alignment.resolve(insn);
                if (dirty != null && dirty.getOpcode() == insn.getOpcode()) {
                    return getSlot(dirty);
                }
            }
        }
        return -1;
    }

    private static int getSlot(AbstractInsnNode insn) {
        return insn instanceof VarInsnNode var ? var.var : insn instanceof IincInsnNode iinc ? iinc.var : -1;
    }
}
