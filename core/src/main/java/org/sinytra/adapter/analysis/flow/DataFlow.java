package org.sinytra.adapter.analysis.flow;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.*;
import java.util.function.BiConsumer;

public final class DataFlow {
    public enum Producers {
        /// Instructions that loaded the value, including DUP and SWAP
        LOADER,
        /// Instructions that actually computed the value
        COMPUTER
    }

    public enum ImplicitCalls {
        /// No changes
        KEEP,
        /// Unwrap the inner arguments of boxing and unboxing calls
        UNWRAP
    }

    private static final Set<String> BOXED_TYPES = Set.of(
        "java/lang/Boolean", "java/lang/Byte", "java/lang/Character", "java/lang/Short",
        "java/lang/Integer", "java/lang/Float", "java/lang/Long", "java/lang/Double"
    );
    private static final Set<String> UNBOXING_METHODS = Set.of(
        "booleanValue", "byteValue", "charValue", "shortValue", "intValue", "floatValue", "longValue", "doubleValue"
    );

    private final MethodNode method;
    private final Producers producers;
    @Nullable
    private Frame<SourceValue>[] frames;

    private DataFlow(MethodNode method, Producers producers) {
        this.method = method;
        this.producers = producers;
    }

    public static DataFlow of(MethodNode method) {
        return of(method, Producers.LOADER);
    }

    public static DataFlow of(MethodNode method, Producers producers) {
        return new DataFlow(method, producers);
    }

    public MethodNode method() {
        return this.method;
    }

    public Producers producers() {
        return this.producers;
    }

    /// {@return the frame before the instruction}
    @Nullable
    public Frame<SourceValue> frame(AbstractInsnNode insn) {
        Frame<SourceValue>[] frames = frames();
        int index = this.method.instructions.indexOf(insn);
        return index >= 0 && index < frames.length ? frames[index] : null;
    }

    /// {@return the stack values the insn consumes or an empty list if unreachable}
    public List<SourceValue> operands(AbstractInsnNode insn) {
        Frame<SourceValue> frame = frame(insn);
        return frame != null ? operands(frame, insn) : List.of();
    }

    /// {@return a single producer of each argument of the call or null if an arg has more producers}
    @Nullable
    public List<AbstractInsnNode> resolveCallOperandProducers(MethodInsnNode call) {
        if (!this.method.instructions.contains(call)) {
            List<MethodInsnNode> calls = MethodAnalyzer.findCalls(this.method, MethodQualifier.create(call));
            return calls.size() == 1 ? resolveCallOperandProducers(calls.getFirst()) : null;
        }

        Frame<SourceValue> frame = frame(call);
        if (frame == null) {
            return null;
        }

        List<AbstractInsnNode> insns = new ArrayList<>();
        for (SourceValue src : operands(frame, call)) {
            AbstractInsnNode srcInsn = AdapterUtil.getSingleInsn(src);
            if (srcInsn == null) {
                return null;
            }
            insns.add(srcInsn);
        }

        return insns;
    }

    /// {@return instructions of operands excluding the call insn}
    @Nullable
    public List<AbstractInsnNode> resolveOperandProducers(MethodInsnNode call) {
        List<AbstractInsnNode> insns = resolveCallOperandProducers(call);
        return insns != null && !insns.isEmpty() ? AdapterUtil.subListInsnsExc(insns.getFirst(), call) : null;
    }

    /// {@return operands for each call matching the qualifier including the call insn}
    public List<List<AbstractInsnNode>> findCallsWithProducers(MethodQualifier qualifier) {
        List<List<AbstractInsnNode>> list = new ArrayList<>();
        for (MethodInsnNode call : MethodAnalyzer.findCalls(this.method, qualifier)) {
            List<AbstractInsnNode> insns = resolveCallOperandProducers(call);
            if (insns != null) {
                insns.add(call);
                list.add(insns);
            }
        }
        return list;
    }

    /// {@return for each operand of the call, all instructions computing it in order, or an empty list}
    public List<List<AbstractInsnNode>> findOperandExpressions(MethodInsnNode call) {
        List<List<AbstractInsnNode>> args = new ArrayList<>();
        if (!this.method.instructions.contains(call)) {
            return args;
        }

        Frame<SourceValue> frame = frame(call);
        if (frame == null) {
            return args;
        }

        for (SourceValue value : operands(frame, call)) {
            List<AbstractInsnNode> insns = new ArrayList<>();
            for (AbstractInsnNode insn : value.insns) {
                expandExpression(insns, insn);
                insns.add(insn);
            }
            args.add(insns);
        }
        return args;
    }

    /// Call the action for each reachable call with its operands
    public void forEachCall(BiConsumer<MethodInsnNode, List<SourceValue>> action) {
        for (AbstractInsnNode insn : this.method.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                Frame<SourceValue> frame = frame(minsn);
                if (frame != null) { // Dead code otherwise
                    action.accept(minsn, operands(frame, minsn));
                }
            }
        }
    }

    /// Call the action for each reachable instance call with the producers of its receiver
    public void forEachReceiver(BiConsumer<MethodInsnNode, Set<AbstractInsnNode>> action) {
        forEachCall((call, operands) -> {
            if (call.getOpcode() != Opcodes.INVOKESTATIC && operands.size() == Type.getArgumentCount(call.desc) + 1) {
                action.accept(call, operands.getFirst().insns);
            }
        });
    }

    /// {@return calls whose result is not used as an operand of another call in order}
    public List<MethodInsnNode> findTopLevelCalls(ImplicitCalls implicitCalls) {
        InsnList instructions = this.method.instructions;

        Set<MethodInsnNode> consumedCalls = new HashSet<>();
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof MethodInsnNode call) {
                traceInputs(call, consumedCalls, new HashSet<>());
            }
        }

        List<MethodInsnNode> topLevelCalls = new ArrayList<>();
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof MethodInsnNode call && !consumedCalls.contains(call)) {
                if (implicitCalls == ImplicitCalls.UNWRAP && isImplicitCall(call)) {
                    unwrapImplicitCall(call, topLevelCalls, new HashSet<>());
                } else if (!topLevelCalls.contains(call)) {
                    topLevelCalls.add(call);
                }
            }
        }
        topLevelCalls.sort(Comparator.comparingInt(instructions::indexOf));

        return topLevelCalls;
    }

    /// {@return methods of the owner class that are called at the top level}
    public List<MethodNode> findTopLevelOwnCalls(ClassNode owner) {
        List<MethodNode> ownCalls = new ArrayList<>();
        for (MethodInsnNode insn : findTopLevelCalls(ImplicitCalls.KEEP)) {
            MethodNode method = AdapterUtil.findMethod(owner, insn.name, insn.desc);
            if (method != null) {
                ownCalls.add(method);
            }
        }
        return ownCalls;
    }

    /// {@return instructions initializing each element of the array, including the array store insn}
    public List<List<AbstractInsnNode>> findArrayElements(AbstractInsnNode newArrayInsn) {
        Frame<SourceValue>[] frames = frames();

        Map<Integer, AbstractInsnNode> indexToStoreNode = new HashMap<>();
        InsnList instructions = this.method.instructions;
        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            if (insn.getOpcode() == Opcodes.AASTORE) {
                Frame<SourceValue> frame = frames[i];
                if (frame == null) continue;

                if (frame.getStackSize() < 3) continue;
                SourceValue arrayRefSource = frame.getStack(frame.getStackSize() - 3);

                if (arrayRefSource.insns.contains(newArrayInsn)) {
                    SourceValue indexSource = frame.getStack(frame.getStackSize() - 2);
                    Integer constantIndex = resolveConstantIndex(indexSource);

                    if (constantIndex != null) {
                        indexToStoreNode.put(constantIndex, insn);
                    }
                }
            }
        }

        List<List<AbstractInsnNode>> groups = new ArrayList<>();
        AbstractInsnNode currentStart = newArrayInsn.getNext();
        int maxIndex = indexToStoreNode.keySet().stream().max(Integer::compare).orElse(-1);

        for (int i = 0; i <= maxIndex; i++) {
            AbstractInsnNode endNode = indexToStoreNode.get(i);
            List<AbstractInsnNode> currentGroup = new ArrayList<>();

            if (endNode != null) {
                AbstractInsnNode ptr = currentStart;
                while (ptr != null) {
                    currentGroup.add(ptr);
                    if (ptr == endNode) break;
                    ptr = ptr.getNext();
                }
                currentStart = endNode.getNext();
            }

            groups.add(currentGroup);
        }

        return groups;
    }

    private Frame<SourceValue>[] frames() {
        if (this.frames == null) {
            Interpreter<SourceValue> interpreter = this.producers == Producers.COMPUTER ? new AdvancedSourceInterpreter(true) : new SourceInterpreter();
            try {
                this.frames = new Analyzer<>(interpreter).analyze(Object.class.getName(), this.method);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return this.frames;
    }

    private void expandExpression(List<AbstractInsnNode> out, AbstractInsnNode insn) {
        Frame<SourceValue> frame = frame(insn);
        if (frame == null) return;

        for (SourceValue value : operands(frame, insn)) {
            for (AbstractInsnNode sourceInsn : value.insns) {
                if (!out.contains(sourceInsn)) {
                    expandExpression(out, sourceInsn);
                    out.add(sourceInsn);
                }
            }
        }
    }

    private void unwrapImplicitCall(MethodInsnNode call, List<MethodInsnNode> out, Set<MethodInsnNode> seen) {
        if (!seen.add(call)) return;

        Set<MethodInsnNode> wrapped = new HashSet<>();
        traceInputs(call, wrapped, new HashSet<>());
        if (wrapped.isEmpty()) {
            if (!out.contains(call)) {
                out.add(call);
            }
            return;
        }
        for (MethodInsnNode inner : wrapped) {
            if (isImplicitCall(inner)) {
                unwrapImplicitCall(inner, out, seen);
            } else if (!out.contains(inner)) {
                out.add(inner);
            }
        }
    }

    private void collectProducerCalls(SourceValue value, Set<MethodInsnNode> consumedCalls, Set<AbstractInsnNode> visited) {
        for (AbstractInsnNode producer : value.insns) {
            if (!visited.add(producer)) continue;

            if (producer instanceof MethodInsnNode producerCall) {
                consumedCalls.add(producerCall);
                continue;
            }

            traceInputs(producer, consumedCalls, visited);
        }
    }

    private void traceInputs(AbstractInsnNode insn, Set<MethodInsnNode> consumedCalls, Set<AbstractInsnNode> visited) {
        Frame<SourceValue> frame = frame(insn);
        if (frame == null) return;

        for (SourceValue input : operands(frame, insn)) {
            collectProducerCalls(input, consumedCalls, visited);
        }
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

    private static Integer resolveConstantIndex(SourceValue source) {
        for (AbstractInsnNode insn : source.insns) {
            int index = AdapterUtil.getIntConstValue(insn).orElse(-1);
            if (index != -1) {
                return index;
            }
        }
        return null;
    }

    private static List<SourceValue> operands(Frame<SourceValue> frame, AbstractInsnNode insn) {
        int stackSize = frame.getStackSize();
        int start = Math.max(0, stackSize - getPopCount(insn, frame));
        List<SourceValue> operands = new ArrayList<>(stackSize - start);
        for (int i = start; i < stackSize; i++) {
            operands.add(frame.getStack(i));
        }
        return operands;
    }

    private static int getPopCount(AbstractInsnNode insn, @Nullable Frame<? extends Value> frame) {
        int op = insn.getOpcode();

        if (insn instanceof MethodInsnNode minsn) {
            int args = Type.getArgumentCount(minsn.desc);
            return op == Opcodes.INVOKESTATIC ? args : args + 1;
        }

        if (insn instanceof InvokeDynamicInsnNode indy) {
            return Type.getArgumentCount(indy.desc);
        }

        if (insn instanceof MultiANewArrayInsnNode multiANewArray) {
            return multiANewArray.dims;
        }

        return switch (op) {
            case Opcodes.POP2, Opcodes.DUP2 -> isWide(frame, 0) ? 1 : 2;
            case Opcodes.DUP_X2 -> isWide(frame, 1) ? 2 : 3;
            case Opcodes.DUP2_X1 -> isWide(frame, 0) ? 2 : 3;
            case Opcodes.DUP2_X2 -> {
                if (isWide(frame, 0)) {
                    yield isWide(frame, 1) ? 2 : 3;
                }
                yield isWide(frame, 2) ? 3 : 4;
            }

            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE,
                 Opcodes.SASTORE -> 3;

            case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB,
                 Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL, Opcodes.IDIV, Opcodes.LDIV,
                 Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM, Opcodes.ISHL,
                 Opcodes.LSHL, Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR, Opcodes.IAND, Opcodes.LAND,
                 Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR, Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG,
                 Opcodes.DCMPL, Opcodes.DCMPG,
                 Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD,
                 Opcodes.SALOAD,
                 Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                 Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE,
                 Opcodes.PUTFIELD, Opcodes.SWAP, Opcodes.DUP_X1 -> 2;

            case Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L,
                 Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.INEG,
                 Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.CHECKCAST, Opcodes.INSTANCEOF,
                 Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE, Opcodes.POP, Opcodes.DUP,
                 Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL,
                 Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH,
                 Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.ATHROW,
                 Opcodes.MONITORENTER, Opcodes.MONITOREXIT, Opcodes.ARRAYLENGTH, Opcodes.NEWARRAY, Opcodes.ANEWARRAY,
                 Opcodes.GETFIELD, Opcodes.PUTSTATIC -> 1;

            default -> 0;
        };
    }

    private static boolean isWide(@Nullable Frame<? extends Value> frame, int depth) {
        if (frame == null) {
            return false;
        }
        int index = frame.getStackSize() - 1 - depth;
        return index >= 0 && frame.getStack(index).getSize() == 2;
    }
}
