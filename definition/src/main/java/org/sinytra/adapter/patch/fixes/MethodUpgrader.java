package org.sinytra.adapter.patch.fixes;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.ParametersDiff;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.transformer.ModifyArgsOffsetTransformer;
import org.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public final class MethodUpgrader {

    public static void upgradeMethod(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, String originalDesc, String modifiedDesc) {
        MethodQualifier cleanQualifier = MethodQualifier.create(originalDesc).orElse(null);
        if (cleanQualifier == null) {
            return;
        }
        MethodQualifier dirtyQualifier = MethodQualifier.create(modifiedDesc).orElse(null);
        if (dirtyQualifier == null) {
            return;
        }
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_ARGS)) {
            ModifyArgsOffsetTransformer.handleModifiedDesc(methodNode, cleanQualifier.desc(), dirtyQualifier.desc());
        } else if (methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            upgradeWrapOperation(classNode, methodNode, methodContext, cleanQualifier, dirtyQualifier);
        }
    }

    private static void upgradeWrapOperation(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, MethodQualifier cleanQualifier, MethodQualifier dirtyQualifier) {
        if (dirtyQualifier.owner() == null || cleanQualifier.desc() == null) {
            return;
        }
        List<Type> originalTargetDesc = List.of(Type.getArgumentTypes(cleanQualifier.desc()));
        List<Type> modifiedTargetDesc = List.of(Type.getArgumentTypes(dirtyQualifier.desc()));
        List<Type> originalDesc = List.of(Type.getArgumentTypes(methodNode.desc));
        List<Type> modifiedDesc = ImmutableList.<Type>builder()
            // Add instance parameter
            .add(Type.getType(dirtyQualifier.owner()))
            // Add target parameters
            .addAll(modifiedTargetDesc)
            // Add everything after the original owner and target args (such as captured locals)
            .addAll(originalDesc.subList(1 + originalTargetDesc.size(), originalDesc.size()))
            .build();
        // Create diff
        ParametersDiff diff = EnhancedParamsDiff.create(originalDesc, modifiedDesc);
        if (!diff.isEmpty()) {
            ModifyMethodParams patch = ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.ALL);
            patch.apply(classNode, methodNode, methodContext, methodContext.patchContext());
        }
    }
}
