package org.sinytra.adapter.patch.processor.redirect;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.util.MethodQualifier;

public class ParameterUsageProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        String cleanTarget = recipe.clean().getAtData().getTarget().orElse(null);
        if (cleanTarget == null) return TxResult.PASS;
        String dirtyTarget = dirty.getAtData().getTarget().orElse(null);
        if (dirtyTarget == null) return TxResult.PASS;

        MethodQualifier cleanTargetQual = MethodQualifier.parse(cleanTarget).orElseThrow();
        MethodQualifier dirtyTargetQual = MethodQualifier.parse(dirtyTarget).orElseThrow();
        for (AbstractInsnNode insn : context.methodNode().instructions) {
            if (insn instanceof MethodInsnNode minsn && cleanTargetQual.matches(minsn)) {
                if (dirtyTargetQual.owner() != null) {
                    minsn.owner = dirtyTargetQual.internalOwnerName();
                }
                minsn.name = dirtyTargetQual.name();
                minsn.desc = dirtyTargetQual.desc();
            }
        }

        return TxResult.SUCCESS;
    }
}
