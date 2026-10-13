package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariable;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;
import java.util.Objects;

public class TargetMethodProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;

        if (Objects.equals(recipe.clean().getTargetClass(), dirty.getTargetClass())) {
            upgradeCapturedLocals(context.methodNode(), context, recipe);
        }

        return TxResult.SUCCESS;
    }

    // TODO Is there a better approach?
    private static void upgradeCapturedLocals(MethodNode methodNode, MixinContext context, Recipe recipe) {
        LocalsAnalyzer.CapturedLocals capturedLocals = context.locals().resolveCapturedLocals(recipe.getDirtyTarget());
        if (capturedLocals == null) return;

        List<LocalVariable> availableLocals = context.locals().resolveAvailableLocals(capturedLocals.target());
        // For now, only handle cases where all locals are part of the method's params, convenient when switching the target to a lambda
        if (availableLocals == null || !availableLocals.isEmpty()) return;

        LocalsAnalyzer.CapturedLocalsTransform transform = LocalsAnalyzer.analyzeCapturedLocals(capturedLocals, methodNode);
        transform.remover().apply(context);
    }
}
