package org.sinytra.adapter.edit.param;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.edit.LVTSnapshot;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.Comparator;
import java.util.List;

import static org.sinytra.adapter.edit.param.ParamTransformationUtil.getLVTIndex;

// TODO Cleanup param transformers
public record InjectParameterTransform(int index, Type type) implements ParameterTransformer {
    @Override
    public InjectParameterTransform offset(int offset) {
        return new InjectParameterTransform(this.index + offset, this.type);
    }

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        boolean isNonStatic = !MethodAnalyzer.isStatic(methodNode);
        int index = this.index + offset;
        if (index >= parameters.size() + 1) {
            return PatchResult.PASS;
        }

        AnnotationHandle annotation = context.methodAnnotation();
        if (annotation.matchesDesc(MixinAnnotations.MODIFY_ARGS)) {
            return ModifyArgsOffsetUpgrader.upgradeAfterParamInsert(methodNode, index) ? PatchResult.APPLY : PatchResult.PASS;
        }

        int lvtIndex = getLVTIndex(parameters, isNonStatic, index);
        LocalVariableNode scope = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst()
            .or(() -> methodNode.localVariables.stream().min(Comparator.comparingInt(lvn -> lvn.index)))
            .orElse(null);
        if (scope == null) {
            offsetParameters(methodNode, parameters.size(), index);
            parameters.add(index, type);
            AdapterUtil.replaceLVT(methodNode, idx -> idx >= lvtIndex ? idx + type.getSize() : idx);
            return PatchResult.APPLY;
        }

        LVTSnapshot.with(methodNode, () -> {
            ParameterNode newParameter = new ParameterNode("adapter_injected_" + index, Opcodes.ACC_SYNTHETIC);
            offsetParameters(methodNode, parameters.size(), index);
            parameters.add(index, type);

            int position = Math.min(index + (isNonStatic ? 1 : 0), methodNode.localVariables.size());
            methodNode.localVariables.add(position, new LocalVariableNode(newParameter.name, type.getDescriptor(), null, scope.start, scope.end, lvtIndex));
        });

        return PatchResult.APPLY;
    }

    public static void offsetParameters(MethodNode methodNode, int paramCount, int paramIndex) {
        ParamTransformationUtil.updateParameterAnnotationArrays(methodNode, paramCount, annotations -> annotations.add(paramIndex, null));

        if (methodNode.invisibleTypeAnnotations != null) {
            List<TypeAnnotationNode> invisibleTypeAnnotations = methodNode.invisibleTypeAnnotations;
            for (int j = 0; j < invisibleTypeAnnotations.size(); j++) {
                TypeAnnotationNode typeAnnotation = invisibleTypeAnnotations.get(j);
                TypeReference ref = new TypeReference(typeAnnotation.typeRef);
                int typeIndex = ref.getFormalParameterIndex();
                if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && typeIndex >= paramIndex) {
                    invisibleTypeAnnotations.set(j, new TypeAnnotationNode(TypeReference.newFormalParameterReference(typeIndex + 1).getValue(), typeAnnotation.typePath, typeAnnotation.desc));
                }
            }
        }
    }
}
