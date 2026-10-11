package org.sinytra.adapter.analysis.method;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.sinytra.adapter.analysis.flow.FrameUtil;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.*;
import java.util.function.BiConsumer;

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

    public static boolean isDeprecated(MethodNode methodNode) {
        return AdapterUtil.hasAnnotation(methodNode.visibleAnnotations, TypeConstants.DEPRECATED);
    }

    public static boolean isDirtyDeprecatedMethod(MethodNode clean, MethodNode dirty) {
        return !isDeprecated(clean) && isDeprecated(dirty);
    }

    // Return outer method calls where receiver = this
    public static List<MethodNode> getSelfTopTierMethodCalls(TargetPair targetPair) {
        ClassNode classNode = targetPair.classNode();

        List<MethodInsnNode> topTierCalls = getTopTierMethodCalls(targetPair);
        List<MethodNode> ownCalls = new ArrayList<>();

        for (MethodInsnNode insn : topTierCalls) {
            MethodNode method = AdapterUtil.findMethod(classNode, insn.name, insn.desc);
            if (method != null) {
                ownCalls.add(method);
            }
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

    public record IndyHandle(InvokeDynamicInsnNode indy, int argIndex, Handle handle, MethodNode method) {
    }

    public static List<IndyHandle> findOwnIndyHandles(ClassNode cls, MethodNode method) {
        List<IndyHandle> handles = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy) {
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (indy.bsmArgs[i] instanceof Handle handle && handle.getOwner().equals(cls.name)) {
                        MethodNode target = AdapterUtil.findMethod(cls, handle.getName(), handle.getDesc());
                        if (target != null) {
                            handles.add(new IndyHandle(indy, i, handle, target));
                        }
                    }
                }
            }
        }
        return handles;
    }

    public static List<AbstractInsnNode> findReturnInsns(MethodNode methodNode) {
        List<AbstractInsnNode> insns = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (OpcodeUtil.isReturnOpcode(insn.getOpcode())) {
                insns.add(insn);
            }
        }
        return insns;
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

    public static List<MethodInsnNode> findDirtyOnlyCalls(MethodNode clean, MethodNode dirty, boolean onlyOnce) {
        Multimap<String, MethodInsnNode> cleanCalls = getMethodCalls(clean);
        List<MethodInsnNode> dirtyOnly = new ArrayList<>();
        getMethodCalls(dirty).asMap().forEach((qualifier, calls) -> {
            if (!cleanCalls.containsKey(qualifier) && (!onlyOnce || calls.size() == 1)) {
                dirtyOnly.addAll(calls);
            }
        });
        return dirtyOnly;
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
        Frame<SourceValue> frame = frames[indices.indexOf(insn)];
        if (frame == null) return;

        for (SourceValue input : FrameUtil.getOperands(frame, insn)) {
            collectProducerCalls(input, frames, indices, consumedCalls, visited);
        }
    }

    public static List<MethodInsnNode> findCalls(MethodNode methodNode, MethodQualifier qualifier) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                calls.add(minsn);
            }
        }
        return calls;
    }

    public static void forEachCall(MethodNode methodNode, BiConsumer<MethodInsnNode, List<SourceValue>> action) {
        Frame<SourceValue>[] frames = FrameUtil.getFrames(methodNode);
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(insn)];
                if (frame != null) { // Dead code otherwise
                    action.accept(minsn, FrameUtil.getOperands(frame, minsn));
                }
            }
        }
    }

    public static void forEachReceiver(MethodNode methodNode, BiConsumer<MethodInsnNode, Set<AbstractInsnNode>> action) {
        forEachCall(methodNode, (call, operands) -> {
            if (call.getOpcode() != Opcodes.INVOKESTATIC && operands.size() == Type.getArgumentCount(call.desc) + 1) {
                action.accept(call, operands.getFirst().insns);
            }
        });
    }

    @Nullable
    public static List<AbstractInsnNode> getMethodCallInsns(MethodNode methodNode, MethodInsnNode minsn) {
        List<AbstractInsnNode> insns = getMethodCallSrcInsns(methodNode, minsn);
        return insns != null && !insns.isEmpty() ? AdapterUtil.subListInsnsExc(insns.getFirst(), minsn) : null;
    }

    public static List<List<AbstractInsnNode>> getAllMethodCallSrcInsnsInclusive(MethodNode methodNode, MethodQualifier qualifier) {
        Frame<SourceValue>[] frames = FrameUtil.getStableFrames(methodNode);
        List<List<AbstractInsnNode>> list = new ArrayList<>();
        for (MethodInsnNode minsn : findCalls(methodNode, qualifier)) {
            List<AbstractInsnNode> insns = getMethodCallSrcInsns(frames, methodNode, minsn);
            if (insns != null) {
                insns.add(minsn);
                list.add(insns);
            }
        }
        return list;
    }

    @Nullable
    public static List<AbstractInsnNode> getMethodCallSrcInsns(MethodNode methodNode, MethodInsnNode minsn) {
        return getMethodCallSrcInsns(methodNode, minsn, false);
    }

    @Nullable
    public static List<AbstractInsnNode> getMethodCallSrcInsns(MethodNode methodNode, MethodInsnNode minsn, boolean stable) {
        // Handle cases where the minsn comes from another method
        if (!methodNode.instructions.contains(minsn)) {
            List<MethodInsnNode> minsns = findCalls(methodNode, MethodQualifier.create(minsn));
            return minsns.size() == 1 ? getMethodCallSrcInsns(methodNode, minsns.getFirst(), stable) : null;
        }
        Frame<SourceValue>[] frames = stable ? FrameUtil.getStableFrames(methodNode) : FrameUtil.getFrames(methodNode);
        return getMethodCallSrcInsns(frames, methodNode, minsn);
    }

    @Nullable
    private static List<AbstractInsnNode> getMethodCallSrcInsns(Frame<SourceValue>[] frames, MethodNode methodNode, MethodInsnNode minsn) {
        Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(minsn)];
        if (frame == null) {
            return null;
        }

        List<AbstractInsnNode> insns = new ArrayList<>();
        for (SourceValue src : FrameUtil.getOperands(frame, minsn)) {
            AbstractInsnNode srcInsn = AdapterUtil.getSingleInsn(src);
            if (srcInsn == null) {
                return null;
            }
            insns.add(srcInsn);
        }
        return insns;
    }

    public static List<List<AbstractInsnNode>> getMethodCallArgInsns(MethodNode methodNode, MethodInsnNode minsn) {
        List<List<AbstractInsnNode>> args = new ArrayList<>();
        if (!methodNode.instructions.contains(minsn)) {
            return args;
        }

        Frame<SourceValue>[] frames = FrameUtil.getFrames(methodNode);
        Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(minsn)];
        if (frame == null) {
            return args;
        }

        for (SourceValue value : FrameUtil.getOperands(frame, minsn)) {
            List<AbstractInsnNode> insns = new ArrayList<>();
            for (AbstractInsnNode insn : value.insns) {
                expandArgInitInsnsRecursive(insns, methodNode.instructions, frames, insn);
                insns.add(insn);
            }
            args.add(insns);
        }
        return args;
    }

    private static void expandArgInitInsnsRecursive(List<AbstractInsnNode> out, InsnList insns, Frame<SourceValue>[] frames, AbstractInsnNode insn) {
        int index = insns.indexOf(insn);
        if (index < 0 || index >= frames.length || frames[index] == null) return;

        for (SourceValue value : FrameUtil.getOperands(frames[index], insn)) {
            for (AbstractInsnNode sourceInsn : value.insns) {
                if (!out.contains(sourceInsn)) {
                    expandArgInitInsnsRecursive(out, insns, frames, sourceInsn);
                    out.add(sourceInsn);
                }
            }
        }
    }
}
