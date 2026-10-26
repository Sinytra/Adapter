package org.sinytra.adapter.pipeline.locate;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodAnalyzer.IndyHandle;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.*;

public final class PassedLocals {
    private static final int MAX_DEPTH = 3;

    private record Step(TargetPair method, int slot) {
    }

    public static int resolve(MixinContext context, TargetPair caller, int slot, TargetPair callee) {
        Set<MethodNode> visited = new HashSet<>();
        visited.add(caller.methodNode());
        List<Step> frontier = List.of(new Step(caller, slot));
        for (int depth = 0; depth < MAX_DEPTH && !frontier.isEmpty(); depth++) {
            List<Step> next = new ArrayList<>();
            for (Step step : frontier) {
                for (Step passed : findPassed(context, step)) {
                    if (passed.method().isSameMethod(callee)) {
                        return passed.slot();
                    }
                    if (visited.add(passed.method().methodNode())) {
                        next.add(passed);
                    }
                }
            }
            frontier = next;
        }
        return -1;
    }

    public static Set<Integer> findPassedSlots(DataFlow flow, AbstractInsnNode insn, boolean hasReceiver, String calleeDesc, Set<Integer> tracked) {
        if (tracked.isEmpty()) {
            return Set.of();
        }
        List<SourceValue> operands = flow.operands(insn);
        Type[] args = Type.getArgumentTypes(calleeDesc);
        Set<Integer> passed = new HashSet<>();
        int slot = 0;
        for (int i = 0; i < operands.size(); i++) {
            int argIndex = hasReceiver ? i - 1 : i;
            if (argIndex >= args.length) {
                break;
            }
            if (isTrackedLoad(operands.get(i), tracked)) {
                passed.add(slot);
            }
            slot += argIndex < 0 ? 1 : args[argIndex].getSize();
        }
        return passed;
    }

    private static List<Step> findPassed(MixinContext context, Step step) {
        TargetPair from = step.method();
        DataFlow flow = context.dataFlow(from);
        Set<Integer> tracked = Set.of(step.slot());
        List<Step> passed = new ArrayList<>();
        for (AbstractInsnNode insn : from.methodNode().instructions) {
            if (insn instanceof MethodInsnNode call) {
                TargetPair method = context.dirtyMethods().resolveInherited(MethodQualifier.create(call));
                if (method != null) {
                    findPassedSlots(flow, call, call.getOpcode() != Opcodes.INVOKESTATIC, call.desc, tracked)
                        .forEach(slot -> passed.add(new Step(method, slot)));
                }
            }
        }
        for (IndyHandle lambda : MethodAnalyzer.findOwnIndyHandles(from.classNode(), from.methodNode())) {
            Handle handle = lambda.handle();
            TargetPair method = new TargetPair(from.classNode(), lambda.method());
            findPassedSlots(flow, lambda.indy(), handle.getTag() != Opcodes.H_INVOKESTATIC, handle.getDesc(), tracked)
                .forEach(slot -> passed.add(new Step(method, slot)));
        }
        return passed;
    }

    private static boolean isTrackedLoad(SourceValue value, Set<Integer> tracked) {
        return value.insns.size() == 1 && value.insns.iterator().next() instanceof VarInsnNode load
            && OpcodeUtil.isLoadOpcode(load.getOpcode()) && tracked.contains(load.var);
    }


    private PassedLocals() {
    }
}
