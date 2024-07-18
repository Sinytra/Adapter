package org.sinytra.adapter.patch.fixes;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.LocalVarAnalyzer;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.SimpleParamsDiffSnapshot;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.transformer.operation.param.ParamTransformTarget;
import org.sinytra.adapter.patch.transformer.operation.param.ParameterTransformer;
import org.sinytra.adapter.patch.transformer.operation.param.TransformParameters;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public final class MethodUpgrader {

    public static void upgradeMethod(MethodNode methodNode, MethodContext methodContext, String originalDesc, String modifiedDesc) {
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
            upgradeWrapOperation(methodNode, methodContext, cleanQualifier, dirtyQualifier);
        }
    }

    // TODO This should be an automatic upgrade tbh
    public static void adjustInjectorOrdinalForNewMethod(MethodInsnNode minsn, MethodContext methodContext) {
        AnnotationValueHandle<Integer> handle = methodContext.injectionPointAnnotationOrThrow().<Integer>getValue("ordinal").orElse(null);
        if (handle == null) {
            return;
        }
        int originalOrdinal = handle.get();
        // Temporarily adjust ordinal to account for previous calls that have not been moved to the new class
        if (handle != null) {
            handle.set(-1);
            List<AbstractInsnNode> insns = methodContext.computeInjectionTargetInsns(methodContext.findDirtyInjectionTarget());
            handle.set(originalOrdinal);
            int newOrdinal = originalOrdinal;
            for (AbstractInsnNode insn : methodContext.findDirtyInjectionTarget().methodNode().instructions) {
                if (insn == minsn) {
                    break;
                }
                if (insns.contains(insn)) {
                    newOrdinal--;
                }
            }
            if (newOrdinal >= 0) {
                handle.set(newOrdinal);
            }
        }
    }

    public static void upgradeCapturedLocals(MethodNode methodNode, MethodContext methodContext) {
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
        transform.remover().apply(methodContext);

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
            patch.apply(methodContext);
        }
    }

    private static void upgradeWrapOperation(MethodNode methodNode, MethodContext methodContext, MethodQualifier cleanQualifier, MethodQualifier dirtyQualifier) {
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
            patch.apply(methodContext);
        }
    }
}
