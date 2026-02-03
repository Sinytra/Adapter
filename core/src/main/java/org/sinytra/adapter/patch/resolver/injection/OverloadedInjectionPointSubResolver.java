package org.sinytra.adapter.patch.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.resolver.SubResolver;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN;

public class OverloadedInjectionPointSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        AtData at = recipe.clean().getAtData();
        if (at == null || !at.getValue().equals(AT_VAL_INVOKE) && !at.getValue().equals(AT_VAL_INVOKE_ASSIGN))
            return null;
        
        TargetPair cleanPair = recipe.getCleanTarget();
        if (cleanPair == null) return null;

        TargetPair dirtyPair = recipe.getDirtyTarget();
        if (dirtyPair == null) return null;

        // Temporarily modify @At data to target INVOKE if originally using INVOKE_ASSIGN
        AtData tempAt = getInvokeAtData(at);
        AbstractInsnNode cleanInsn = context.methods().findInjectionTargetInsn(cleanPair, tempAt);
        if (!(cleanInsn instanceof MethodInsnNode cleanMinsn)) return null;
        MethodQualifier cleanQualifier = MethodQualifier.create(cleanMinsn);

        TargetPair cleanTarget = context.methods().findMethodPair(context.cleanLookup(), cleanQualifier);
        if (cleanTarget == null) return null;

        TargetPair newCleanTarget = context.methods().findMethodPair(context.dirtyLookup(), cleanQualifier);
        if (newCleanTarget == null) return null;

        // Check if called clean method was marked as deprecated
        if (AdapterUtil.isDeprecated(cleanTarget.methodNode()) || !AdapterUtil.isDeprecated(newCleanTarget.methodNode())) {
            return null;
        }

        // Check that the deprecated method is not called anymore
        List<MethodInsnNode> cleanCallsInDirty = MethodCallAnalyzer.getMethodCallMinsns(dirtyPair.methodNode(), cleanQualifier);
        if (!cleanCallsInDirty.isEmpty()) return null;

        // Find method calls to potential replacement method
        MethodQualifier qualifier = cleanQualifier.ignoreDesc();
        List<MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCallMinsns(dirtyPair.methodNode(), qualifier);
        if (dirtyCalls.isEmpty()) return null;

        // All calls target the same overloaded method
        boolean allEqual = dirtyCalls.stream().map(m -> m.desc).distinct().limit(2).count() <= 1;
        if (allEqual) {
            return MutableConfiguration.create()
                .setAtData(recipe.clean().getAtData().withTarget(dirtyCalls.getFirst()));
        }

        return null;
    }

    private AtData getInvokeAtData(AtData at) {
        if (at.getValue().equals(AT_VAL_INVOKE_ASSIGN)) {
            return at.withValue(AT_VAL_INVOKE);
        }
        return at;
    }
}
