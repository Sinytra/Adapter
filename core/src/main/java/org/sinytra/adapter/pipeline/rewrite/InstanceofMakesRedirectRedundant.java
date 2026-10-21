package org.sinytra.adapter.pipeline.rewrite;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.sinytra.adapter.analysis.compare.InsnComparator;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;

public final class InstanceofMakesRedirectRedundant implements RewriteRule {
    @Nullable
    @Override
    public Rewrite apply(MixinContext context, Recipe recipe) {
        TypeInsnNode instanceOf = InstanceofReplacement.find(context, recipe);
        if (instanceOf == null) {
            return null;
        }

        List<AbstractInsnNode> handler = new ArrayList<>();
        for (AbstractInsnNode insn : context.methodNode().instructions) {
            if (insn.getOpcode() >= 0) {
                handler.add(insn);
            }
        }
        if (handler.isEmpty() || !OpcodeUtil.isReturnOpcode(handler.getLast().getOpcode())) {
            return null;
        }
        handler.removeLast();

        if (InstructionMatcher.test(InstanceofReplacement.findCondition(instanceOf), handler, InsnComparator.IGNORE_VAR_INDEX)) {
            return new Rewrite.Delete("the handler repeats the dirty instanceof check");
        }

        return null;
    }
}
