package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.model.ann.ExpressionData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.ArrayList;

public class ExpressionProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        ExpressionData expression = dirty.getProperty(IntentKeys.EXPRESSION).orElse(null);
        if (expression == null) {
            return TxResult.PASS;
        }

        MethodNode handler = context.methodNode();
        if (handler.invisibleAnnotations == null) {
            handler.invisibleAnnotations = new ArrayList<>();
        }
        handler.invisibleAnnotations.removeIf(ExpressionData::isExpressionAnnotation);
        handler.invisibleAnnotations.addAll(expression.toAnnotationNodes());
        context.recordCtxAudit("Set expression to: %s", expression.expression());
        return TxResult.SUCCESS;
    }
}
