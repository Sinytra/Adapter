package org.sinytra.adapter.patch.analysis;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;

public record InstructionMatcher(AbstractInsnNode insn, List<AbstractInsnNode> before, List<AbstractInsnNode> after) {
    @Nullable
    public String findReplacement(List<String> cleanCallOrder, List<String> dirtyCallOrder) {
        MethodInsnNode previousMethodCall = MethodCallAnalyzer.findFirstInsn(this.before.get(0), MethodInsnNode.class, MethodCallAnalyzer.BACKWARDS);
        if (previousMethodCall == null) {
            return null;
        }
        String previousCallQualifier = MethodCallAnalyzer.getCallQualifier(previousMethodCall);
        int previousCallIndex = dirtyCallOrder.indexOf(previousCallQualifier);
        if (previousCallIndex == -1) {
            return null;
        }
        int previousDirtyCount = count(dirtyCallOrder, previousCallQualifier);
        if (previousDirtyCount < 1 || previousDirtyCount != count(cleanCallOrder, previousCallQualifier)) {
            return null;
        }

        MethodInsnNode nextMethodCall = MethodCallAnalyzer.findFirstInsn(this.after.get(0), MethodInsnNode.class, MethodCallAnalyzer.FORWARD);
        if (nextMethodCall == null) {
            return null;
        }
        String nextCallQualifier = MethodCallAnalyzer.getCallQualifier(nextMethodCall);
        int nextCallIndex = dirtyCallOrder.indexOf(nextCallQualifier);
        if (nextCallIndex == -1) {
            return null;
        }
        int nextDirtyCount = count(dirtyCallOrder, nextCallQualifier);
        if (nextDirtyCount < 1 || nextDirtyCount != count(cleanCallOrder, nextCallQualifier)) {
            return null;
        }

        int diff = nextCallIndex - previousCallIndex;
        if (diff == 2) {
            return dirtyCallOrder.get(previousCallIndex + 1);
        }
        return null;
    }

    public boolean test(InstructionMatcher other) {
        return test(other, 0);
    }

    public boolean test(InstructionMatcher other, int flags) {
        if (this.before.size() == other.before.size() && this.after.size() == other.after.size()) {
            for (int i = 0; i < this.before.size(); i++) {
                AbstractInsnNode insn = this.before.get(i);
                AbstractInsnNode otherInsn = other.before.get(i);
                if (!InsnComparator.instructionsEqual(insn, otherInsn, flags)) {
                    return false;
                }
            }
            for (int i = 0; i < this.after.size(); i++) {
                AbstractInsnNode insn = this.after.get(i);
                AbstractInsnNode otherInsn = other.after.get(i);
                if (!InsnComparator.instructionsEqual(insn, otherInsn, flags)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean test(InsnList first, InsnList second) {
        if (first.size() == second.size()) {
            for (int i = 0; i < first.size(); i++) {
                AbstractInsnNode insn = first.get(i);
                AbstractInsnNode otherInsn = second.get(i);
                if (!InsnComparator.instructionsEqual(insn, otherInsn)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static <T> int count(List<T> list, T item) {
        return (int) list.stream().filter(item::equals).count();
    }
}
