package org.sinytra.adapter.analysis.method;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.sinytra.adapter.analysis.InstructionMatcher;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class MethodInsnMatcher {
    public static final UnaryOperator<AbstractInsnNode> FORWARD = AbstractInsnNode::getNext;
    public static final UnaryOperator<AbstractInsnNode> BACKWARDS = AbstractInsnNode::getPrevious;
    private static final int INSN_RANGE = 5;

    public static InstructionMatcher findSurroundingInstructions(AbstractInsnNode insn) {
        return findSurroundingInstructions(insn, INSN_RANGE);
    }

    public static InstructionMatcher findSurroundingInstructions(AbstractInsnNode insn, int range) {
        LabelNode previousLabel = findFirstLabelInsn(insn, BACKWARDS);
        LabelNode nextLabel = findFirstLabelInsn(insn, FORWARD);

        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, BACKWARDS);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, FORWARD);

        return new InstructionMatcher(insn, previousInsns, nextInsns);
    }

    public static InstructionMatcher findBackwardsInstructions(AbstractInsnNode insn) {
        return findBackwardsInstructions(insn, INSN_RANGE);
    }

    public static InstructionMatcher findBackwardsInstructions(AbstractInsnNode insn, int range) {
        LabelNode previousLabel = findFirstLabelInsn(insn, BACKWARDS);
        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, BACKWARDS);

        return new InstructionMatcher(insn, previousInsns, List.of());
    }

    public static InstructionMatcher findForwardInstructions(AbstractInsnNode insn) {
        return findForwardInstructions(insn, INSN_RANGE);
    }

    public static InstructionMatcher findForwardInstructions(AbstractInsnNode insn, int range) {
        LabelNode nextLabel = findFirstLabelInsn(insn, FORWARD);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, FORWARD);

        return new InstructionMatcher(insn, List.of(), nextInsns);
    }

    public static InstructionMatcher findForwardInstructionsDirect(AbstractInsnNode insn) {
        return findForwardInstructionsDirect(insn, INSN_RANGE);
    }

    public static InstructionMatcher findForwardInstructionsDirect(AbstractInsnNode insn, int range) {
        List<AbstractInsnNode> nextInsns = getInsns(insn, range, FORWARD);

        return new InstructionMatcher(insn, List.of(), nextInsns);
    }

    @Nullable
    private static LabelNode findFirstLabelInsn(AbstractInsnNode insn, UnaryOperator<AbstractInsnNode> operator) {
        return Stream.iterate(insn, Objects::nonNull, operator)
            .filter(LabelNode.class::isInstance)
            .map(LabelNode.class::cast)
            .findFirst()
            .orElse(null);
    }

    private static List<AbstractInsnNode> getInsns(AbstractInsnNode root, int range, UnaryOperator<AbstractInsnNode> operator) {
        return Stream.iterate(root, Objects::nonNull, operator)
            .filter(insn -> !(insn instanceof FrameNode) && !(insn instanceof LineNumberNode))
            .limit(range)
            .toList();
    }
}
