package org.sinytra.adapter.analysis;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public final class InsnTraversal {
    public static final UnaryOperator<AbstractInsnNode> FORWARD = AbstractInsnNode::getNext;
    public static final UnaryOperator<AbstractInsnNode> BACKWARD = AbstractInsnNode::getPrevious;

    public static boolean isDebugNode(AbstractInsnNode insn) {
        return insn instanceof FrameNode || insn instanceof LineNumberNode;
    }

    public static Stream<AbstractInsnNode> walk(@Nullable AbstractInsnNode start, UnaryOperator<AbstractInsnNode> direction) {
        return Stream.iterate(start, Objects::nonNull, direction);
    }

    @Nullable
    public static AbstractInsnNode find(AbstractInsnNode insn, UnaryOperator<AbstractInsnNode> direction, Predicate<AbstractInsnNode> filter) {
        return walk(direction.apply(insn), direction).filter(filter).findFirst().orElse(null);
    }

    public static List<AbstractInsnNode> untilLabel(AbstractInsnNode insn, UnaryOperator<AbstractInsnNode> direction) {
        return untilLabel(insn, direction, false);
    }

    public static List<AbstractInsnNode> untilLabel(AbstractInsnNode insn, UnaryOperator<AbstractInsnNode> direction, boolean inclusive) {
        return walk(inclusive ? insn : direction.apply(insn), direction)
            .takeWhile(i -> !(i instanceof LabelNode))
            .filter(i -> !isDebugNode(i))
            .toList();
    }

    private InsnTraversal() {
    }
}
