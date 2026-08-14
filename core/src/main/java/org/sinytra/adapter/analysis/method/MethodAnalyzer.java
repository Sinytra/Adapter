package org.sinytra.adapter.analysis.method;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.sinytra.adapter.analysis.selector.FrameUtil;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.*;
import java.util.function.BiPredicate;

public class MethodAnalyzer {
    public static final String LAMBDA_PREFIX = "lambda$";

    private static final Set<String> BOXED_TYPES = Set.of(
        "java/lang/Boolean", "java/lang/Byte", "java/lang/Character", "java/lang/Short",
        "java/lang/Integer", "java/lang/Float", "java/lang/Long", "java/lang/Double"
    );
    private static final Set<String> UNBOXING_METHODS = Set.of(
        "booleanValue", "byteValue", "charValue", "shortValue", "intValue", "floatValue", "longValue", "doubleValue"
    );

    public static boolean isLambda(MethodNode methodNode) {
        return methodNode.name.startsWith(LAMBDA_PREFIX);
    }

    public static boolean isLambda(MethodQualifier qualifier) {
        return qualifier.name() != null && qualifier.name().startsWith(LAMBDA_PREFIX);
    }

    public static boolean isDirtyDeprecatedMethod(MethodNode clean, MethodNode dirty) {
        return !AdapterUtil.hasAnnotation(clean.visibleAnnotations, TypeConstants.DEPRECATED)
            && AdapterUtil.hasAnnotation(dirty.visibleAnnotations, TypeConstants.DEPRECATED);
    }

    // Return outer method calls where receiver = this
    public static List<MethodNode> getSelfTopTierMethodCalls(TargetPair targetPair) {
        ClassNode classNode = targetPair.classNode();

        List<MethodInsnNode> topTierCalls = getTopTierMethodCalls(targetPair);
        List<MethodNode> ownCalls = new ArrayList<>();

        for (MethodInsnNode insn : topTierCalls) {
            classNode.methods.stream()
                .filter(m -> m.name.equals(insn.name) && m.desc.equals(insn.desc))
                .findFirst()
                .ifPresent(ownCalls::add);
        }

        return ownCalls;
    }

    // Return outer method calls
    public static List<MethodInsnNode> getTopTierMethodCalls(TargetPair targetPair) {
        return getTopTierMethodCalls(targetPair, false);
    }

    public static List<MethodInsnNode> getTopTierMethodCalls(TargetPair targetPair, boolean skipImplicitCalls) {
        MethodNode methodNode = targetPair.methodNode();
        InsnList instructions = methodNode.instructions;
        Frame<SourceValue>[] frames = FrameUtil.getFrames(methodNode);

        Set<MethodInsnNode> consumedCalls = new HashSet<>();
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof MethodInsnNode call) {
                traceInputs(call, frames, instructions, consumedCalls, new HashSet<>());
            }
        }

        List<MethodInsnNode> topTierCalls = new ArrayList<>();
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof MethodInsnNode call && !consumedCalls.contains(call)) {
                if (skipImplicitCalls && isImplicitCall(call)) {
                    unwrapImplicitCall(call, frames, instructions, topTierCalls, new HashSet<>());
                } else if (!topTierCalls.contains(call)) {
                    topTierCalls.add(call);
                }
            }
        }
        topTierCalls.sort(Comparator.comparingInt(instructions::indexOf));

        return topTierCalls;
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
                                if (lambda != null) {
                                    list.addAll(findLambdasInMethod(cls, lambda, methods));
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        return list;
    }

    public static Multimap<String, MethodInsnNode> getMethodCalls(MethodNode node) {
        ImmutableMultimap.Builder<String, MethodInsnNode> calls = ImmutableMultimap.builder();
        for (AbstractInsnNode insn : node.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                String qualifier = MethodQualifier.create(minsn).asDescriptor();
                calls.put(qualifier, minsn);
            }
        }
        return calls.build();
    }

    public static boolean containsMethodCall(MethodNode methodNode, MethodQualifier qualifier) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImplicitCall(MethodInsnNode minsn) {
        if (!BOXED_TYPES.contains(minsn.owner)) {
            return false;
        }
        if (minsn.getOpcode() == Opcodes.INVOKESTATIC && minsn.name.equals("valueOf")) {
            Type[] args = Type.getArgumentTypes(minsn.desc);
            return args.length == 1 && args[0].getSort() <= Type.DOUBLE;
        }
        return minsn.getOpcode() == Opcodes.INVOKEVIRTUAL && UNBOXING_METHODS.contains(minsn.name) && Type.getArgumentCount(minsn.desc) == 0;
    }

    private static void unwrapImplicitCall(MethodInsnNode call, Frame<SourceValue>[] frames, InsnList instructions, List<MethodInsnNode> out, Set<MethodInsnNode> seen) {
        if (!seen.add(call)) return;

        Set<MethodInsnNode> wrapped = new LinkedHashSet<>();
        traceInputs(call, frames, instructions, wrapped, new HashSet<>());
        if (wrapped.isEmpty()) {
            if (!out.contains(call)) {
                out.add(call);
            }
            return;
        }
        for (MethodInsnNode inner : wrapped) {
            if (isImplicitCall(inner)) {
                unwrapImplicitCall(inner, frames, instructions, out, seen);
            } else if (!out.contains(inner)) {
                out.add(inner);
            }
        }
    }

    public static <T> List<T> analyzeMethod(MethodNode methodNode, BiPredicate<MethodInsnNode, List<? extends SourceValue>> filter, NaryOperationHandler<T> handler) {
        AnalysingSourceInterpreter<T> i = analyzeInterpretMethod(methodNode, new AnalysingSourceInterpreter<>(filter, handler));
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

    @Nullable
    private static MethodNode findUniqueMethod(Multimap<String, MethodNode> methods, String name) {
        Collection<MethodNode> values = methods.get(name);
        if (values != null && !values.isEmpty()) {
            if (values.size() > 1) {
                return null;
            }
            return values.iterator().next();
        }
        return null;
    }

    private static void collectProducerCalls(SourceValue value, Frame<SourceValue>[] frames, InsnList indices, Set<MethodInsnNode> consumedCalls, Set<AbstractInsnNode> visited) {
        for (AbstractInsnNode producer : value.insns) {
            if (!visited.add(producer)) continue;

            if (producer instanceof MethodInsnNode producerCall) {
                consumedCalls.add(producerCall);
                continue;
            }

            traceInputs(producer, frames, indices, consumedCalls, visited);
        }
    }

    private static void traceInputs(AbstractInsnNode insn, Frame<SourceValue>[] frames, InsnList indices, Set<MethodInsnNode> consumedCalls, Set<AbstractInsnNode> visited) {
        int index = indices.indexOf(insn);
        Frame<SourceValue> frame = frames[index];
        if (frame == null) return;

        int popCount = FrameUtil.getPopCount(insn);
        int stackTop = frame.getStackSize();
        for (int j = 0; j < popCount; j++) {
            int slot = stackTop - 1 - j;
            if (slot < 0) break;

            SourceValue input = frame.getStack(slot);
            collectProducerCalls(input, frames, indices, consumedCalls, visited);
        }
    }

    public interface NaryOperationHandler<T> {
        T accept(MethodInsnNode insn, List<? extends SourceValue> values);
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
