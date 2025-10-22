package org.sinytra.adapter.patch.analysis;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.util.List;

public record InstructionMatcher(AbstractInsnNode insn, List<AbstractInsnNode> before, List<AbstractInsnNode> after) {
    public InstructionMatcher inverse() {
        return new InstructionMatcher(insn, after.reversed(), before.reversed());
    }

    public boolean test(InstructionMatcher other) {
        return test(other, 0);
    }

    public boolean test(InstructionMatcher other, int flags) {
        return testBefore(other, flags) && testAfter(other, flags);
    }

    public boolean testBefore(InstructionMatcher other) {
        return testBefore(other, 0);
    }

    public boolean testBefore(InstructionMatcher other, int flags) {
        return testInsns(this.before, other.before, flags);
    }
    
    public boolean testAfter(InstructionMatcher other) {
        return testAfter(other, 0);
    }
    
    public boolean testAfter(InstructionMatcher other, int flags) {
        return testInsns(this.after, other.after, flags);
    }

    private boolean testInsns(List<AbstractInsnNode> ours, List<AbstractInsnNode> theirs, int flags) {
        if (ours.size() == theirs.size()) {
            for (int i = 0; i < ours.size(); i++) {
                AbstractInsnNode insn = ours.get(i);
                AbstractInsnNode otherInsn = theirs.get(i);
                if (!InsnComparator.insnEqual(insn, otherInsn, flags)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean test(InsnList first, InsnList second) {
        return test(first, second, 0);
    }

    public static boolean test(InsnList first, InsnList second, int flags) {
        if (first.size() == second.size()) {
            for (int i = 0; i < first.size(); i++) {
                AbstractInsnNode insn = first.get(i);
                AbstractInsnNode otherInsn = second.get(i);
                if (!InsnComparator.insnEqual(insn, otherInsn, flags)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean test(List<AbstractInsnNode> first, List<AbstractInsnNode> second, int flags) {
        if (first.size() == second.size()) {
            for (int i = 0; i < first.size(); i++) {
                AbstractInsnNode insn = first.get(i);
                AbstractInsnNode otherInsn = second.get(i);
                if (!InsnComparator.insnEqual(insn, otherInsn, flags)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
