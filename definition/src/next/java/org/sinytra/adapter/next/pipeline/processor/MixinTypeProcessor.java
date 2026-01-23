package org.sinytra.adapter.next.pipeline.processor;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

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
//                methodContext.recordAudit(this, "Modify type to %s", this.replacementDesc);
                return TxResult.SUCCESS;
            }
        }

        return TxResult.PASS;
    }
}
