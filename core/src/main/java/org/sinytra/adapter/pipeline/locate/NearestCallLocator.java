package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.List;

public final class NearestCallLocator implements Locator {
    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Scope scope, AbstractInsnNode cleanInsn) {
        MethodAlignment alignment = scope.alignment();
        List<AbstractInsnNode> around = alignment.findDirtyAround(cleanInsn);
        AbstractInsnNode start = !around.isEmpty() ? around.getFirst() : findAlignedAfter(alignment, cleanInsn);

        for (AbstractInsnNode insn = start; insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call) {
                return List.of(scope.candidate(call, MethodAlignment.Confidence.POSITIONAL));
            }
            if (insn instanceof JumpInsnNode || OpcodeUtil.isReturnOpcode(insn.getOpcode())) {
                break;
            }
        }
        return List.of();
    }

    @Nullable
    private static AbstractInsnNode findAlignedAfter(MethodAlignment alignment, AbstractInsnNode cleanInsn) {
        for (AbstractInsnNode insn = cleanInsn.getNext(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) {
                AbstractInsnNode dirty = alignment.resolve(insn);
                if (dirty != null) {
                    return dirty;
                }
            }
        }
        return null;
    }
}
