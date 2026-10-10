package org.sinytra.adapter.transform.param;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.mixin.MixinFlag;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.Comparator;
import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_INDEX;
import static org.sinytra.adapter.transform.param.ParamTransformationUtil.getLVTIndex;

// TODO Cleanup param transformers
public record InjectParameterTransform(int index, Type type) implements ParameterTransformer {
    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        boolean isNonStatic = !MethodHelper.isStatic(methodNode);
        int index = this.index + offset;
        if (index >= parameters.size() + 1) {
            return PatchResult.PASS;
        }

        AnnotationHandle annotation = context.methodAnnotation();
        if (context.hasFlag(MixinFlag.TARGETS_VARIABLE)) {
            if (index < 1) {
                return PatchResult.PASS;
            }
            int insertedLvtIndex = getLVTIndex(parameters.subList(1, parameters.size()), isNonStatic, index - 1);
            annotation.<Integer>getValue(PROPERTY_INDEX).ifPresent(indexHandle -> {
                int indexValue = indexHandle.get();
                if (indexValue >= insertedLvtIndex) {
                    indexHandle.set(indexValue + this.type.getSize());
                }
            });
            return PatchResult.APPLY;
        }
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
