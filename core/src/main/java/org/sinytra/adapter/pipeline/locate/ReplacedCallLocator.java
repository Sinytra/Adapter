package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.List;

public final class ReplacedCallLocator implements Locator {
    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Targets targets, AbstractInsnNode cleanInsn) {
        if (!(cleanInsn instanceof MethodInsnNode cleanCall)) {
            return List.of();
        }

        Type returnType = Type.getReturnType(cleanCall.desc);
        MethodAlignment alignment = MethodAlignment.align(targets.clean().methodNode(), targets.dirty().methodNode());
        List<MethodInsnNode> calls = alignment.findDirtyAround(cleanCall).stream()
            .filter(insn -> insn instanceof MethodInsnNode call && Type.getReturnType(call.desc).equals(returnType))
            .map(MethodInsnNode.class::cast)
            .toList();
        if (calls.size() > 1) {
            AbstractInsnNode cleanConsumer = nextReal(cleanCall);
            List<MethodInsnNode> usedAlike = calls.stream()
                .filter(call -> nextReal(call) != null && cleanConsumer != null && nextReal(call).getOpcode() == cleanConsumer.getOpcode())
                .toList();
            if (!usedAlike.isEmpty()) {
                calls = usedAlike;
            }
        }
        if (calls.isEmpty() && !context.hasFlag(MixinFlag.RETURN_TYPE_SENSITIVE) && !context.hasFlag(MixinFlag.AT_TARGET_SENSITIVE)) {
            MethodInsnNode storing = findStoringCall(context, targets, alignment, cleanCall);
            calls = storing != null ? List.of(storing) : List.of();
        }
        return calls.stream()
            .map(call -> new Candidate(targets.dirty(), call, MethodAlignment.Confidence.POSITIONAL))
            .toList();
    }

    /// {@return the dirty call whose value goes into the local the clean call's result was stored into, or null}
    @Nullable
    private static MethodInsnNode findStoringCall(MixinContext context, Targets targets, MethodAlignment alignment, MethodInsnNode cleanCall) {
        AbstractInsnNode cleanStore = skipCasts(nextReal(cleanCall));
        if (cleanStore == null || !OpcodeUtil.isStoreOpcode(cleanStore.getOpcode())
            || !(alignment.resolve(cleanStore) instanceof VarInsnNode dirtyStore)
        ) {
            return null;
        }

        DataFlow flow = context.dataFlow(targets.dirty());
        AbstractInsnNode producer = dirtyStore;
        do {
            List<SourceValue> operands = flow.operands(producer);
            producer = operands.size() == 1 && operands.getFirst().insns.size() == 1 ? operands.getFirst().insns.iterator().next() : null;
        } while (producer != null && producer.getOpcode() == Opcodes.CHECKCAST);
        return producer instanceof MethodInsnNode call ? call : null;
    }

    @Nullable
    private static AbstractInsnNode skipCasts(@Nullable AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() == Opcodes.CHECKCAST) {
            insn = nextReal(insn);
        }
        return insn;
    }

    @Nullable
    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        return InsnTraversal.find(insn, InsnTraversal.FORWARD, i -> i.getOpcode() >= 0);
    }
}
