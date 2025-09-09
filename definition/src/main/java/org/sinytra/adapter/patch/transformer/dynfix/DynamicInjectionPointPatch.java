package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

@SuppressWarnings({"rawtypes", "unchecked"})
public class DynamicInjectionPointPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<DynamicFixer<?>> PASSIVE = List.of(
        new DynFixLocalCaptureUpgrade()
    );
    private static final List<DynamicFixer<?>> PREPATCH = List.of(
        new DynFixResolveAmbiguousTarget()
    );
    private static final List<DynamicFixer<?>> FIXES = List.of(
        new DynFixSliceBoundary(),
        new DynFixAtVariableAssignStore(),
        new DynFixSplitMethod(),
        new DynFixParameterTypeAdapter(),
        new DynFixMethodComparison(),
        new DynFixSyntheticInstanceof(),
        // Have this one always come last
        new DynFixArbitraryInjectionPoint()
    );

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        if (methodContext.findCleanInjectionTarget() == null) {
            return Patch.Result.PASS;
        }

        PatchAuditTrail auditTrail = context.environment().auditTrail();
        Patch.Result result = Patch.Result.PASS;

        for (DynamicFixer fix : PASSIVE) {
            Object data = fix.prepare(methodContext);
            if (data != null) {
                auditTrail.recordResult(methodContext, PatchAuditTrail.Match.NONE);
                DynamicFixer.FixResult fixResult = fix.apply(classNode, methodNode, methodContext, auditTrail, data);
                if (fixResult != null) {
                    auditTrail.recordResult(methodContext, fixResult.match());
                    result = result.or(fixResult.result());
                }
            }
        }

        if (methodContext.failsDirtyInjectionCheck()) {
            LOGGER.debug(MIXINPATCH, "Considering method {}.{}", classNode.name, methodNode.name);

            auditTrail.recordResult(methodContext, PatchAuditTrail.Match.NONE);

            for (DynamicFixer fix : PREPATCH) {
                Object data = fix.prepare(methodContext);
                if (data != null) {
                    DynamicFixer.FixResult fixResult = fix.apply(classNode, methodNode, methodContext, auditTrail, data);
                    if (fixResult != null) {
                        auditTrail.recordResult(methodContext, fixResult.match());
                        result = result.or(fixResult.result());
                    }
                }
            }
            for (DynamicFixer fix : FIXES) {
                Object data = fix.prepare(methodContext);
                if (data != null) {
                    DynamicFixer.FixResult fixResult = fix.apply(classNode, methodNode, methodContext, auditTrail, data);
                    if (fixResult != null) {
                        auditTrail.recordResult(methodContext, fixResult.match());
                        return result.or(fixResult.result());
                    }
                }
            }
        }

        return result;
    }
}
