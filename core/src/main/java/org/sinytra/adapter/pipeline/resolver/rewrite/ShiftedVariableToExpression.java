package org.sinytra.adapter.pipeline.resolver.rewrite;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configurations;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.OpcodeUtil;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN;

public final class ShiftedVariableToExpression implements RewriteRule {
    @Nullable
    @Override
    public Rewrite apply(MixinContext context, Recipe recipe) {
        AtData at = recipe.clean().getAtData();
        if (at == null || at.getTarget().isEmpty() || !AT_VAL_INVOKE.equals(at.getValue()) && !AT_VAL_INVOKE_ASSIGN.equals(at.getValue())
            || at.getProperty(AtData.Keys.SHIFT).orElse(null) != At.Shift.BY || at.getProperty(AtData.Keys.BY).orElse(0) != 2
        ) {
            return null;
        }

        List<AbstractInsnNode> calls = context.injections().find(recipe.getCleanTarget());
        if (calls.isEmpty() || !calls.stream().allMatch(ShiftedVariableToExpression::isStored)) {
            return null;
        }

        AtData expression = AtData.builder(AT_VAL_INVOKE)
            .target(at.getTargetOrThrow())
            .ordinal(at.getOrdinal().orElse(null))
            .build();

        return new Rewrite.Replace(Configurations.changeMixinType(recipe.dirty(), MixinAnnotations.MODIFY_EXPR_VAL, expression));
    }

    private static boolean isStored(AbstractInsnNode insn) {
        AbstractInsnNode next = InsnTraversal.find(insn, InsnTraversal.FORWARD, i -> i.getOpcode() >= 0);
        return OpcodeUtil.isStoreOpcode(insn.getOpcode()) || next != null && OpcodeUtil.isStoreOpcode(next.getOpcode());
    }
}
