package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public final class ReplacedCallLocator implements Locator {
    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Targets targets, AbstractInsnNode cleanInsn) {
        if (!(cleanInsn instanceof MethodInsnNode cleanCall)) {
            return List.of();
        }

        Type returnType = Type.getReturnType(cleanCall.desc);
        List<MethodInsnNode> calls = MethodAlignment.align(targets.clean().methodNode(), targets.dirty().methodNode()).findDirtyAround(cleanCall).stream()
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
        return calls.stream()
            .map(call -> new Candidate(targets.dirty(), call, MethodAlignment.Confidence.POSITIONAL))
            .toList();
    }

    @Nullable
    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        return InsnTraversal.find(insn, InsnTraversal.FORWARD, i -> i.getOpcode() >= 0);
    }
}
