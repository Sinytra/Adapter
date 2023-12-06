package dev.su5ed.sinytra.adapter.patch.analysis;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class MethodCallAnalyzer {
    public static final UnaryOperator<AbstractInsnNode> FORWARD = AbstractInsnNode::getNext;
    public static final UnaryOperator<AbstractInsnNode> BACKWARDS = AbstractInsnNode::getPrevious;

    public static Multimap<String, MethodInsnNode> getMethodCalls(MethodNode node, List<String> callOrder) {
        ImmutableMultimap.Builder<String, MethodInsnNode> calls = ImmutableMultimap.builder();
        for (AbstractInsnNode insn : node.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                String qualifier = getCallQualifier(minsn);
                calls.put(qualifier, minsn);
                callOrder.add(qualifier);
            }
        }
        return calls.build();
    }

    public static InstructionMatcher findSurroundingInstructions(AbstractInsnNode insn, int range) {
        return findSurroundingInstructions(insn, range, false);
    }

    public static InstructionMatcher findSurroundingInstructions(AbstractInsnNode insn, int range, boolean remapCalls) {
        LabelNode previousLabel = findFirstInsn(insn, LabelNode.class, BACKWARDS);
        LabelNode nextLabel = findFirstInsn(insn, LabelNode.class, FORWARD);

        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, remapCalls, BACKWARDS);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, remapCalls, FORWARD);

        return new InstructionMatcher(insn, previousInsns, nextInsns);
    }

    private static List<AbstractInsnNode> getInsns(AbstractInsnNode root, int range, boolean remapCalls, UnaryOperator<AbstractInsnNode> operator) {
        return Stream.iterate(root, Objects::nonNull, operator)
            .filter(insn -> !(insn instanceof FrameNode) && !(insn instanceof LineNumberNode))
            .limit(range)
            .map(insn -> remapCalls ? remapInsn(insn) : insn)
            .toList();
    }

    private static AbstractInsnNode remapInsn(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode minsn) {
            MethodInsnNode clone = (MethodInsnNode) minsn.clone(Map.of());
            clone.name = PatchEnvironment.remapReference(clone.name);
            return clone;
        }
        if (insn instanceof FieldInsnNode finsn) {
            FieldInsnNode clone = (FieldInsnNode) finsn.clone(Map.of());
            clone.name = PatchEnvironment.remapReference(clone.name);
            return clone;
        }
        return insn;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends AbstractInsnNode> T findFirstInsn(AbstractInsnNode insn, Class<T> type, UnaryOperator<AbstractInsnNode> operator) {
        return (T) Stream.iterate(insn, Objects::nonNull, operator)
            .filter(type::isInstance)
            .findFirst()
            .orElse(null);
    }

    public static String getCallQualifier(MethodInsnNode insn) {
        return Type.getObjectType(insn.owner).getDescriptor() + insn.name + insn.desc;
    }
}
