package org.sinytra.adapter.next.pipeline.processor;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.patch.api.LocalVariable;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.List;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_METHOD;

public class TargetMethodProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;

        context.methodAnnotation()
            .setOrAppendNonNull(AT_METHOD, List.of(dirty.getTargetMethod().asDescriptor()));

        upgradeCapturedLocals(context.methodNode(), context.legacy());

        return TxResult.SUCCESS;
    }

    // TODO Is there a better approach?
    private static void upgradeCapturedLocals(MethodNode methodNode, MethodContext methodContext) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(methodNode, methodContext);
        if (capturedLocals == null) {
            return;
        }

        List<LocalVariable> availableLocals = methodContext.getTargetMethodLocals(capturedLocals.target());
        // For now, only handle cases where all locals are part of the method's params, convenient when switching the target to a lambda
        if (availableLocals == null || !availableLocals.isEmpty()) {
            return;
        }

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(capturedLocals, methodNode);
        transform.remover().apply(methodContext);
    }
}
