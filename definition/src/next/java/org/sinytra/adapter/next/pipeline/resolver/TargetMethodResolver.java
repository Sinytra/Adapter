package org.sinytra.adapter.next.pipeline.resolver;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public class TargetMethodResolver implements Resolver {
    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, MutableConfiguration dirty, Recipe recipe) {
        // Conditions
        if (dirty.getTargetMethod() != null) return TxResult.PASS;
        
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();
        if (cleanQualifier == null) throw new IllegalStateException("Missing clean target method");

        // Reuse attempt
        MethodContext.TargetPair target = context.methods().findMethod(context.dirtyLookup(), cleanQualifier);
        if (target != null) {
            dirty.setTargetMethod(cleanQualifier);
            return TxResult.SUCCESS;
        }

        if (resolveChangedMethodParams(context, cleanQualifier, dirty))
            return TxResult.SUCCESS;

        return TxResult.FAIL;
    }

    public boolean resolveChangedMethodParams(MixinContext context, MethodQualifier cleanQualifier, MutableConfiguration dirty) {
        Pair<ClassNode, List<MethodNode>> candidates = context.methods().findMethodsIgnoringDesc(context.dirtyLookup(), cleanQualifier);
        if (candidates != null && !candidates.getSecond().isEmpty()) {
            // Only apply single candidate change when the target desc has changed
            if (candidates.getSecond().size() == 1) {
                MethodNode node = candidates.getSecond().getFirst();
                MethodContext.TargetPair cleanTarget = context.methods().findMethod(context.cleanLookup(), cleanQualifier);
                if (cleanTarget == null || node.desc.equals(cleanTarget.methodNode().desc)) {
                    return false;
                }

                dirty.setTargetMethod(MethodQualifier.create(candidates.getFirst(), node));
                return true;
            }
        }
        return false;
    }
}
