package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public class MixinTypeProcessor implements Processor {

    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (recipe.clean().getMixinType().equals(dirty.getMixinType())) 
            return TxResult.PASS;

        MethodNode methodNode = context.methodNode();
        AnnotationHandle annotation = context.methodAnnotation();

        for (int i = 0; i < methodNode.visibleAnnotations.size(); i++) {
            AnnotationNode methodAnn = methodNode.visibleAnnotations.get(i);
            if (methodAnn == annotation.unwrap()) {
                methodAnn.desc = dirty.getMixinType();
                context.recordCtxAudit("Modify type to %s", methodAnn.desc);
                return TxResult.SUCCESS;
            }
        }

        return TxResult.PASS;
    }
}
