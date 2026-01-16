package org.sinytra.adapter.next;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.pipeline.PipelineExecutor;
import org.sinytra.adapter.next.type.MixinType;
import org.sinytra.adapter.next.type.MixinTypes;
import org.sinytra.adapter.patch.api.*;
import org.slf4j.Logger;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

/**
 * Entrypoint hook for UTP in the old method transformer system
 */
public class PipelineLegacyMethodTransformer implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        if (methodContext.findCleanInjectionTarget() == null
            || !methodContext.failsDirtyInjectionCheck() && methodContext.hasValidSlice(methodContext.findDirtyInjectionTarget())
        ) {
            return Patch.Result.PASS;
        }

        PatchAuditTrail.Match previousMatch = context.environment().auditTrail().getMatch(methodContext);
        if (previousMatch != null && previousMatch != PatchAuditTrail.Match.NONE) {
            return Patch.Result.PASS;
        }

        String annotationInternalName = Type.getType(methodContext.methodAnnotation().getDesc()).getInternalName();
        MixinType<?> mixinType = MixinTypes.getMixinType(annotationInternalName);
        if (mixinType == null) {
            return Patch.Result.PASS;
        }
        ClassTarget classTarget = ClassTarget.parse(methodContext.rawClassAnnotation());
        if (classTarget == null) {
            return Patch.Result.PASS;
        }

        LOGGER.debug(MIXINPATCH, "Considering method {}.{}", classNode.name, methodNode.name);

        PatchAuditTrail auditTrail = context.environment().auditTrail();
        auditTrail.recordResult(methodContext, PatchAuditTrail.Match.NONE);

        MixinContext mixinContext = new MixinContext(classNode, methodNode, methodContext);
        PipelineExecutor pipeline = new PipelineExecutor(classTarget, mixinContext);
        Patch.Result result = pipeline.execute(mixinType);
        if (result != Patch.Result.PASS) {
            auditTrail.recordResult(methodContext, PatchAuditTrail.Match.FULL);
            return result;
        }

        return Patch.Result.PASS;
    }
}
