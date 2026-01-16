package org.sinytra.adapter.patch.analysis.method;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiPredicate;

public class MethodAnalyzer {
    public static final String LAMBDA_PREFIX = "lambda$";

    public static boolean isDirtyDeprecatedMethod(MethodNode clean, MethodNode dirty) {
        return !AdapterUtil.hasAnnotation(clean.visibleAnnotations, MixinConstants.DEPRECATED) && AdapterUtil.hasAnnotation(dirty.visibleAnnotations, MixinConstants.DEPRECATED);
    }

    public static List<MethodInsnNode> getMethodInvocations(MethodNode method, MethodQualifier qualifier) {
        List<MethodInsnNode> list = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                list.add(minsn);
            }
        }
        return list;
    }

    @Nullable
    public static List<MethodNode> collectMethodInvocations(ClassNode cls, MethodNode mtd) {
        // Iterate over isns, leave out first and last elements
        // Collect method invocations
        // All labels must be finalized by a method invocation to pass
        List<MethodNode> invocations = new ArrayList<>();
        for (int i = 1; i < mtd.instructions.size(); i++) {
            AbstractInsnNode insn = mtd.instructions.get(i);
            if (insn instanceof LabelNode) {
                AbstractInsnNode previous = insn.getPrevious();
                AbstractInsnNode effectivePrevious = previous;
                if (OpcodeUtil.isReturnOpcode(previous.getOpcode())) {
                    effectivePrevious = previous.getPrevious();
                }

                if (effectivePrevious instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(cls.name)) {
                    cls.methods.stream()
                        .filter(m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc))
                        .findFirst()
                        .ifPresent(invocations::add);
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

    public static Multimap<String, MethodInsnNode> getMethodCalls(MethodNode node, List<String> callOrder) {
        ImmutableMultimap.Builder<String, MethodInsnNode> calls = ImmutableMultimap.builder();
        for (AbstractInsnNode insn : node.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                String qualifier = MethodQualifier.create(minsn).asDescriptor();
                calls.put(qualifier, minsn);
                callOrder.add(qualifier);
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
