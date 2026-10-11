package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.analysis.locals.LocalVariable;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public class TargetMethodProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;

        upgradeCapturedLocals(context.methodNode(), context, recipe);

        return TxResult.SUCCESS;
    }

    // TODO Is there a better approach?
    private static void upgradeCapturedLocals(MethodNode methodNode, MixinContext context, Recipe recipe) {
        LocalVarAnalyzer.CapturedLocals capturedLocals = LocalVarAnalyzer.findCapturedLocals(context, recipe);
        if (capturedLocals == null) return;

        List<LocalVariable> availableLocals = context.methods().getTargetMethodLocals(capturedLocals.target());
        // For now, only handle cases where all locals are part of the method's params, convenient when switching the target to a lambda
        if (availableLocals == null || !availableLocals.isEmpty()) return;

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(capturedLocals, methodNode);
        transform.remover().apply(context);
    }
}
