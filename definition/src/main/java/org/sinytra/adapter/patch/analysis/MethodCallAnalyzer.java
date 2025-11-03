package org.sinytra.adapter.patch.analysis;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class MethodCallAnalyzer {
    public static final UnaryOperator<AbstractInsnNode> FORWARD = AbstractInsnNode::getNext;
    public static final UnaryOperator<AbstractInsnNode> BACKWARDS = AbstractInsnNode::getPrevious;
    public static final String LAMBDA_PREFIX = "lambda$";

    public static boolean isDirtyDeprecatedMethod(MethodNode clean, MethodNode dirty) {
        return !AdapterUtil.hasAnnotation(clean.visibleAnnotations, MixinConstants.DEPRECATED) && AdapterUtil.hasAnnotation(dirty.visibleAnnotations, MixinConstants.DEPRECATED);
    }

    @Nullable
    public static List<MethodNode> collectMethodInvocations(ClassNode cls, MethodNode mtd) {
        // Iterate over isns, leave out first and last elements
        // Collect method invocations
        // All labels must be finalized by a method invocation to pass
        List<MethodNode> invocations = new ArrayList<>();
        for (int i = 1; i < mtd.instructions.size() - 1; i++) {
            AbstractInsnNode insn = mtd.instructions.get(i);
            if (insn instanceof LabelNode) {
                AbstractInsnNode previous = insn.getPrevious();
                if (previous instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(cls.name)) {
                    MethodNode method = cls.methods.stream().filter(m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElseThrow();
                    invocations.add(method);
                } else if (previous == null || !OpcodeUtil.isReturnOpcode(previous.getOpcode())) {
                    return null;
                }
            }
        }
        return invocations;
    }

    public static List<String> findLambdasInMethod(ClassNode cls, MethodNode method, @Nullable Multimap<String, MethodNode> methods) {
        List<String> list = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length >= 3) {
                for (Object bsmArg : indy.bsmArgs) {
                    if (bsmArg instanceof Handle handle && handle.getOwner().equals(cls.name)) {
                        if (handle.getName().startsWith(LAMBDA_PREFIX)) {
                            String name = handle.getName();
                            list.add(name);
                            if (methods != null) {
                                MethodNode lambda = findUniqueMethod(methods, name);
                                list.addAll(findLambdasInMethod(cls, lambda, methods));
                            }
                            break;
                        }
                    }
                }
            }
        }
        return list;
    }

    public static Optional<MethodNode> findMethodByUniqueName(ClassNode cls, String name) {
        List<MethodNode> methods = cls.methods.stream().filter(m -> m.name.equals(name)).toList();
        if (methods.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(methods.getFirst());
    }

    public static Optional<MethodNode> findMethodByName(ClassNode cls, String name, @Nullable String desc) {
        return cls.methods.stream()
            .filter(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc)))
            .findFirst();
    }

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
        LabelNode previousLabel = findFirstInsn(insn, LabelNode.class, BACKWARDS);
        LabelNode nextLabel = findFirstInsn(insn, LabelNode.class, FORWARD);

        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, BACKWARDS);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, FORWARD);

        return new InstructionMatcher(insn, previousInsns, nextInsns);
    }

    public static InstructionMatcher findBackwardsInstructions(AbstractInsnNode insn, int range) {
        LabelNode previousLabel = findFirstInsn(insn, LabelNode.class, BACKWARDS);
        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, BACKWARDS);

        return new InstructionMatcher(insn, previousInsns, List.of());
    }

    public static InstructionMatcher findForwardInstructions(AbstractInsnNode insn, int range) {
        LabelNode nextLabel = findFirstInsn(insn, LabelNode.class, FORWARD);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, FORWARD);

        return new InstructionMatcher(insn, List.of(), nextInsns);
    }

    public static InstructionMatcher findForwardInstructionsDirect(AbstractInsnNode insn, int range) {
        List<AbstractInsnNode> nextInsns = getInsns(insn, range, FORWARD);

        return new InstructionMatcher(insn, List.of(), nextInsns);
    }

    private static List<AbstractInsnNode> getInsns(AbstractInsnNode root, int range, UnaryOperator<AbstractInsnNode> operator) {
        return Stream.iterate(root, Objects::nonNull, operator)
            .filter(insn -> !(insn instanceof FrameNode) && !(insn instanceof LineNumberNode))
            .limit(range)
            .toList();
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

    public static boolean containsMethodCall(MethodNode methodNode, MethodQualifier qualifier) {
        return !getInvocationInsns(methodNode, qualifier).isEmpty();
    }

    public static List<List<AbstractInsnNode>> getInvocationInsns(MethodNode methodNode, MethodQualifier qualifier) {
        return analyzeMethod(methodNode, (insn, values) -> qualifier.matches(insn) && values.size() > 1, (insn, values) -> {
            List<AbstractInsnNode> insns = new ArrayList<>();
            for (SourceValue value : values) {
                if (value.insns.size() == 1) {
                    insns.add(value.insns.iterator().next());
                }
            }
            insns.add(insn);
            return insns;
        });
    }

    public static int getMethodCallOrdinal(MethodNode method, MethodInsnNode insn) {
        List<MethodInsnNode> insns = new ArrayList<>();
        for (AbstractInsnNode i : method.instructions) {
            if (i instanceof MethodInsnNode m && InsnComparator.insnEqual(m, insn)) {
                insns.add(m);
            }
        }
        return insns.indexOf(insn);
    }

    public static int getArgIndex(String desc, Type type) {
        List<Type> args = Arrays.asList(Type.getArgumentTypes(desc));
        List<Type> found = args.stream().filter(type::equals).toList();
        if (found.size() != 1) {
            return -1;
        }
        return args.indexOf(found.getFirst());
    }

    @Nullable
    public static List<AbstractInsnNode> findMethodCallParamInsns(MethodNode methodNode, MethodInsnNode insn) {
        MethodCallInterpreter interpreter = MethodCallAnalyzer.analyzeInterpretMethod(methodNode, new MethodCallInterpreter(insn));
        return interpreter.getTargetArgs();
    }

    @Nullable
    public static List<AbstractInsnNode> findFullMethodCallParamInsns(MethodNode methodNode, MethodInsnNode minsn) {
        List<AbstractInsnNode> insns = findMethodCallParamInsns(methodNode, minsn);
        if (minsn != null) {
            List<AbstractInsnNode> fullInsns = new ArrayList<>();
            for (AbstractInsnNode i = insns.getFirst(); i != null; i = i.getNext()) {
                if (i == minsn) {
                    break;
                }
                fullInsns.add(i);
            }
            return fullInsns;
        }
        return null;
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

    public static MethodNode findUniqueMethod(Multimap<String, MethodNode> methods, String name) {
        Collection<MethodNode> values = methods.get(name);
        if (values != null && !values.isEmpty()) {
            if (values.size() > 1) {
                throw new IllegalStateException("Found multiple candidates for method " + name);
            }
            return values.iterator().next();
        }
        throw new NullPointerException("Method " + name + " not found");
    }

    private static class MethodCallInterpreter extends SourceInterpreter {
        private final MethodInsnNode targetInsn;
        private List<AbstractInsnNode> targetArgs;

        public MethodCallInterpreter(MethodInsnNode targetInsn) {
            super(Opcodes.ASM9);
            this.targetInsn = targetInsn;
        }

        @javax.annotation.Nullable
        public List<AbstractInsnNode> getTargetArgs() {
            return this.targetArgs;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn == this.targetInsn && this.targetArgs == null) {
                List<AbstractInsnNode> targetArgs = values.stream()
                    .map(v -> v.insns.size() == 1 ? v.insns.iterator().next() : null)
                    .toList();
                if (!targetArgs.contains(null)) {
                    this.targetArgs = targetArgs;
                }
            }
            return super.naryOperation(insn, values);
        }
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
