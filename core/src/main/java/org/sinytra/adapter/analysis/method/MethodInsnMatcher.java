package org.sinytra.adapter.analysis.method;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;

import java.util.List;
import java.util.function.UnaryOperator;

public class MethodInsnMatcher {
    private static final UnaryOperator<AbstractInsnNode> FORWARD = InsnTraversal.FORWARD;
    private static final UnaryOperator<AbstractInsnNode> BACKWARDS = InsnTraversal.BACKWARD;

    public static InstructionMatcher findSurroundingInstructions(AbstractInsnNode insn, int range) {
        LabelNode previousLabel = findFirstLabelInsn(insn, BACKWARDS);
        LabelNode nextLabel = findFirstLabelInsn(insn, FORWARD);

        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, BACKWARDS);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, FORWARD);

        return new InstructionMatcher(insn, previousInsns, nextInsns);
    }

    @Nullable
    private static LabelNode findFirstLabelInsn(AbstractInsnNode insn, UnaryOperator<AbstractInsnNode> operator) {
        return InsnTraversal.walk(insn, operator)
            .filter(LabelNode.class::isInstance)
            .map(LabelNode.class::cast)
            .findFirst()
            .orElse(null);
    }

    private static List<AbstractInsnNode> getInsns(AbstractInsnNode root, int range, UnaryOperator<AbstractInsnNode> operator) {
        return InsnTraversal.walk(root, operator)
            .filter(insn -> !InsnTraversal.isDebugNode(insn))
            .limit(range)
            .toList();
    }
}
