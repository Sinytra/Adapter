package org.sinytra.adapter.transform.param;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ctx.PatchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.sinytra.adapter.transform.param.ParamTransformationUtil.calculateLVTIndex;

// TODO Cleanup param transformers
public record InjectParameterTransform(int index, Type type) implements ParameterTransformer {
    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        boolean isNonStatic = (methodNode.access & Opcodes.ACC_STATIC) == 0;
        final int index = this.index + offset;
        if (index >= parameters.size() + 1) {
            return PatchResult.PASS;
        }

        AnnotationHandle annotation = context.methodAnnotation();

        if (annotation.matchesDesc(MixinAnnotations.MODIFY_VAR)) {
            annotation.<Integer>getValue("index").ifPresent(indexHandle -> {
                int indexValue = indexHandle.get();
                if (indexValue >= index) {
                    indexHandle.set(indexValue + 1);
                }
            });
            return PatchResult.APPLY;
        }

        if (annotation.matchesDesc(MixinAnnotations.MODIFY_ARGS)) {
            ModifyArgsOffsetUpgrader.upgradeAfterParamInsert(methodNode, this.index);
            return PatchResult.APPLY;
        }

        LocalVariableNode self = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();

        int lvtIndex = calculateLVTIndex(parameters, isNonStatic, index);

        LVTSnapshot.with(methodNode, () -> {
            ParameterNode newParameter = new ParameterNode("adapter_injected_" + index, Opcodes.ACC_SYNTHETIC);
            parameters.add(index, type);
            methodNode.parameters.add(index, newParameter);

            offsetParameters(methodNode, index);

            methodNode.localVariables.add(index + (isNonStatic ? 1 : 0), new LocalVariableNode(newParameter.name, type.getDescriptor(), null, self.start, self.end, lvtIndex));
        });

        return PatchResult.APPLY;
    }

    public static void offsetParameters(MethodNode methodNode, int paramIndex) {
        if (methodNode.invisibleParameterAnnotations != null) {
            List<List<AnnotationNode>> annotations = new ArrayList<>(Arrays.asList(methodNode.invisibleParameterAnnotations));
            if (paramIndex < annotations.size()) {
                annotations.add(paramIndex, null);
                methodNode.invisibleParameterAnnotations = (List<AnnotationNode>[]) annotations.toArray(List[]::new);
                methodNode.invisibleAnnotableParameterCount = annotations.size();
            }
        }
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
