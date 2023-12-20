package dev.su5ed.sinytra.adapter.patch.analysis;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.function.BiPredicate;
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
            clone.name = GlobalReferenceMapper.remapReference(clone.name);
            return clone;
        }
        if (insn instanceof FieldInsnNode finsn) {
            FieldInsnNode clone = (FieldInsnNode) finsn.clone(Map.of());
            clone.name = GlobalReferenceMapper.remapReference(clone.name);
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

    public static <T> List<T> analyzeMethod(MethodNode methodNode, NaryOperationHandler<T> handler) {
        return analyzeMethod(methodNode, (insn, values) -> true, handler);
    }

    public static <T> List<T> analyzeMethod(MethodNode methodNode, BiPredicate<MethodInsnNode, List<? extends SourceValue>> filter, NaryOperationHandler<T> handler) {
        AnalysingSourceInterpreter<T> i = new AnalysingSourceInterpreter<>(filter, handler);
        Analyzer<?> analyzer = new Analyzer<>(i);
        try {
            analyzer.analyze(methodNode.name, methodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
        return i.getResults();
    }

    public static <T extends Interpreter<V>, V extends Value> T analyzeInterpretMethod(MethodNode methodNode, T interpreter) {
        Analyzer<V> analyzer = new Analyzer<>(interpreter);
        try {
            analyzer.analyze(methodNode.name, methodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
        return interpreter;
    }

    public interface NaryOperationHandler<T> {
        T accept(MethodInsnNode insn, List<? extends SourceValue> values);
    }

    @Nullable
    public static AbstractInsnNode getSingleInsn(List<? extends SourceValue> values, int index) {
        SourceValue value = values.get(index);
        return value.insns.size() == 1 ? value.insns.iterator().next() : null;
    }

    private static class AnalysingSourceInterpreter<T> extends SourceInterpreter {
        private final BiPredicate<MethodInsnNode, List<? extends SourceValue>> filter;
        private final NaryOperationHandler<T> handler;
        private final List<T> results = new ArrayList<>();
        private final Collection<MethodInsnNode> seen = new HashSet<>();

        public AnalysingSourceInterpreter(BiPredicate<MethodInsnNode, List<? extends SourceValue>> filter, NaryOperationHandler<T> handler) {
            super(Opcodes.ASM9);

            this.filter = filter;
            this.handler = handler;
        }

        public List<T> getResults() {
            return this.results;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode minsn && this.filter.test(minsn, values) && !this.seen.contains(minsn)) {
                T result = this.handler.accept(minsn, values);
                if (result != null) {
                    this.results.add(result);
                    this.seen.add(minsn);
                }
            }
            return super.naryOperation(insn, values);
        }
    }
}
