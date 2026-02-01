package org.sinytra.adapter.analysis.method;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.selector.FrameUtil;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class MethodCallAnalyzer {
    public static List<MethodInsnNode> getMethodCallMinsns(MethodNode methodNode, MethodQualifier qualifier) {
        return StreamSupport.stream(methodNode.instructions.spliterator(), false)
            .filter(i -> i instanceof MethodInsnNode minsn && qualifier.matches(minsn))
            .map(MethodInsnNode.class::cast)
            .toList();
    }

    @Nullable
    public static List<AbstractInsnNode> getMethodCallInsns(MethodNode methodNode, MethodInsnNode minsn) {
        return Optional.ofNullable(getMethodCallSrcInsns(methodNode, minsn))
            .filter(insns -> !insns.isEmpty())
            .map(insns -> {
                AbstractInsnNode start = insns.getFirst();
                return AdapterUtil.subListInsnsExc(start, minsn);
            })
            .orElse(null);
    }

    // Uses true producer source values
    public static List<List<AbstractInsnNode>> getAllMethodCallSrcInsnsInclusive(MethodNode methodNode, MethodQualifier qualifier) {
        List<List<AbstractInsnNode>> list = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                List<AbstractInsnNode> insns = getMethodCallSrcInsns(methodNode, minsn, true);
                if (insns != null) {
                    insns.add(minsn);
                    list.add(insns);
                }
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
            List<MethodInsnNode> minsns = getMethodCallMinsns(methodNode, MethodQualifier.create(minsn));
            return minsns.size() == 1 ? getMethodCallSrcInsns(methodNode, minsns.getFirst(), stable) : null;
        }

        List<? extends SourceValue> sources = Objects.requireNonNull(getCallSourceValues(methodNode, minsn, stable));

        List<AbstractInsnNode> insns = new ArrayList<>();
        for (SourceValue src : sources) {
            AbstractInsnNode srcInsn = AdapterUtil.getSingleInsn(src);
            if (srcInsn == null) {
                return null;
            }
            insns.add(srcInsn);
        }
        return insns;
    }

    public static List<List<AbstractInsnNode>> getMethodCallArgInsns(MethodNode methodNode, MethodInsnNode minsn) {
        Frame<SourceValue>[] frames = FrameUtil.getFrames(methodNode);
        Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(minsn)];

        List<List<AbstractInsnNode>> args = new ArrayList<>();
        for (int i = 0; i < frame.getStackSize(); ++i) {
            List<AbstractInsnNode> insns = new ArrayList<>();
            SourceValue value = frame.getStack(i);
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

        Frame<SourceValue> frame = frames[index];
        int inputs = FrameUtil.getPopCount(insn);
        int stackSize = frame.getStackSize();
        int start = stackSize - inputs;

        for (int i = start; i < stackSize; ++i) {
            SourceValue value = frame.getStack(i);
            for (AbstractInsnNode sourceInsn : value.insns) {
                if (!out.contains(sourceInsn)) {
                    expandArgInitInsnsRecursive(out, insns, frames, sourceInsn);
                    out.add(sourceInsn);
                }
            }
        }
    }

    @Nullable
    public static List<? extends SourceValue> getCallSourceValues(MethodNode methodNode, MethodInsnNode minsn, boolean stable) {
        SourceValueInterpreter i = MethodAnalyzer.analyzeInterpretMethod(methodNode, new SourceValueInterpreter(minsn, stable));
        return i.getResults();
    }

    private static class SourceValueInterpreter extends AdvancedSourceInterpreter {
        private final AbstractInsnNode targetInsn;
        private List<? extends SourceValue> results;

        public SourceValueInterpreter(AbstractInsnNode targetInsn, boolean stable) {
            super(stable);
            this.targetInsn = targetInsn;
        }

        public List<? extends SourceValue> getResults() {
            return this.results;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn == this.targetInsn && this.results == null) {
                this.results = values;
            }
            return super.naryOperation(insn, values);
        }
    }
}
