package org.sinytra.adapter.patch.processor.wrapop;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record WrapOpOriginalCall(List<CallArg> args) {
    public static final MethodQualifier QUALIFIER = new MethodQualifier(TypeConstants.OPERATION_TYPE.getDescriptor(), "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    public static List<List<AbstractInsnNode>> findAll(MethodNode methodNode) {
        return MethodAnalyzer.getAllMethodCallSrcInsnsInclusive(methodNode, QUALIFIER);
    }

    public static List<AbstractInsnNode> findFirstCallInsns(MethodNode methodNode) {
        List<MethodInsnNode> calls = MethodAnalyzer.findCalls(methodNode, QUALIFIER);
        return calls.isEmpty() ? List.of() : Objects.requireNonNullElseGet(MethodAnalyzer.getMethodCallInsns(methodNode, calls.getFirst()), List::of);
    }

    public static List<AbstractInsnNode> findCallVarInsns(MethodNode methodNode) {
        return MethodAnalyzer.findCalls(methodNode, QUALIFIER).stream()
            .flatMap(call -> InsnTraversal.untilLabel(call, InsnTraversal.BACKWARD).stream())
            .filter(AdapterUtil::canHandleLocalVarInsnValue)
            .toList();
    }

    @Nullable
    public static WrapOpOriginalCall parse(MethodNode methodNode, List<AbstractInsnNode> callInsns) {
        if (callInsns.size() < 2) {
            return null;
        }
        AbstractInsnNode singleArg = callInsns.get(1);
        if (!(singleArg instanceof TypeInsnNode tinsn) || tinsn.getOpcode() != Opcodes.ANEWARRAY) {
            return null;
        }
        return parse(WrapOpAnalyzer.groupArrayInitializers(methodNode, singleArg));
    }

    public record CallArg(List<AbstractInsnNode> pre, List<AbstractInsnNode> load, List<AbstractInsnNode> post) {
        public static CallArg create(int index, List<AbstractInsnNode> load) {
            List<AbstractInsnNode> pre = List.of(
                new InsnNode(Opcodes.DUP),
                AdapterUtil.getIntConstInsn(index)
            );
            List<AbstractInsnNode> post = List.of(
                new InsnNode(Opcodes.AASTORE)
            );
            return new WrapOpOriginalCall.CallArg(pre, load, post);
        }

        @Nullable
        public static CallArg parse(List<AbstractInsnNode> insns) {
            if (insns.size() < 4 || insns.getFirst().getOpcode() != Opcodes.DUP)
                return null;
            AbstractInsnNode idxInsn = insns.get(1);
            if (AdapterUtil.getIntConstValue(idxInsn).isEmpty())
                return null;

            AbstractInsnNode aastore = insns.getLast();
            if (aastore.getOpcode() != Opcodes.AASTORE)
                return null;

            return new CallArg(
                insns.subList(0, 2),
                insns.subList(2, insns.size() - 1),
                List.of(insns.getLast())
            );
        }
    }

    public List<AbstractInsnNode> merge() {
        return this.args.stream()
            .flatMap(a -> Stream.of(a.pre, a.load, a.post).flatMap(Collection::stream))
            .toList();
    }

    @Nullable
    public static WrapOpOriginalCall parse(List<List<AbstractInsnNode>> argsInsns) {
        if (argsInsns.isEmpty())
            return null;

        List<CallArg> args = new ArrayList<>();
        for (List<AbstractInsnNode> insns : argsInsns) {
            CallArg arg = CallArg.parse(insns);
            if (arg == null) {
                return null;
            }
            args.add(arg);
        }

        return new WrapOpOriginalCall(args);
    }
}
