package org.sinytra.adapter.patch.fixes;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.LocalVarAnalyzer;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.SimpleParamsDiffSnapshot;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.transformer.ModifyArgsOffsetTransformer;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.transformer.param.ParameterTransformer;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;
import org.sinytra.adapter.patch.util.AdapterUtil;
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

    public static void upgradeCapturedLocals(ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(methodNode, methodContext);
        if (capturedLocals == null) {
            return;
        }

        List<MethodContext.LocalVariable> availableLocals = methodContext.getTargetMethodLocals(capturedLocals.target());
        // For now, only handle cases where all locals are part of the method's params, convenient when switching the target to a lambda
        if (availableLocals == null || !availableLocals.isEmpty()) {
            return;
        }

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(capturedLocals, methodNode);
        transform.remover().apply(classNode, methodNode, methodContext, methodContext.patchContext());

        List<Type> expected = List.of(Type.getArgumentTypes(methodNode.desc));
        List<Type> required = ImmutableList.<Type>builder()
            .add(Type.getArgumentTypes(capturedLocals.target().methodNode().desc))
            .add(AdapterUtil.getMixinCallableReturnType(capturedLocals.target().methodNode()))
            .build();
        LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(expected, required);
        if (!diff.isEmpty()) {
            List<ParameterTransformer> transformers = diff.modifications().stream()
                .map(LayeredParamsDiffSnapshot.ParamModification::asParameterTransformer)
                .toList();
            MethodTransform patch = TransformParameters.builder().transform(transformers).withOffset().targetType(ParamTransformTarget.METHOD).build();
            patch.apply(classNode, methodNode, methodContext, methodContext.patchContext());
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
        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(originalDesc, modifiedDesc);
        if (!diff.isEmpty()) {
            MethodTransform patch = diff.asParameterTransformer(ParamTransformTarget.ALL, false, false);
            patch.apply(classNode, methodNode, methodContext, methodContext.patchContext());
        }
    }
}
