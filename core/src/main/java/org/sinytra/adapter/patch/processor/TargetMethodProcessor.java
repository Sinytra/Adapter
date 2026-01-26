package org.sinytra.adapter.patch.processor;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.env.ctx.LocalVariable;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_METHOD;

public class TargetMethodProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;

        context.methodAnnotation()
            .setOrAppendNonNull(AT_METHOD, List.of(dirty.getTargetMethod().asDescriptor()));

        upgradeCapturedLocals(context.methodNode(), context, recipe);

        return TxResult.SUCCESS;
    }

    // TODO Is there a better approach?
    private static void upgradeCapturedLocals(MethodNode methodNode, MixinContext context, Recipe recipe) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(context, recipe);
        if (capturedLocals == null) return;

        List<LocalVariable> availableLocals = context.methods().getTargetMethodLocals(capturedLocals.target());
        // For now, only handle cases where all locals are part of the method's params, convenient when switching the target to a lambda
        if (availableLocals == null || !availableLocals.isEmpty()) return;

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(capturedLocals, methodNode);
        transform.remover().apply(context);
    }
}
