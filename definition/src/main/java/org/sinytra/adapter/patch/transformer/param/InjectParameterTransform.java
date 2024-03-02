package org.sinytra.adapter.patch.transformer.param;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.transformer.ModifyArgsOffsetTransformer;
import org.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.List;

public record InjectParameterTransform(int index, Type type) implements ParameterTransformer {
    static final Codec<InjectParameterTransform> CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.intRange(0, 255).fieldOf("index").forGetter(InjectParameterTransform::index),
            AdapterUtil.TYPE_CODEC.fieldOf("parameterType").forGetter(InjectParameterTransform::type)
    ).apply(in, InjectParameterTransform::new));

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        boolean isNonStatic = (methodNode.access & Opcodes.ACC_STATIC) == 0;
        final int index = this.index + offset;
        if (index >= parameters.size() + 1) {
            return Patch.Result.PASS;
        }

        AnnotationHandle annotation = methodContext.methodAnnotation();

        if (annotation.matchesDesc(MixinConstants.MODIFY_VAR)) {
            annotation.<Integer>getValue("index").ifPresent(indexHandle -> {
                int indexValue = indexHandle.get();
                if (indexValue >= index) {
                    indexHandle.set(indexValue + 1);
                }
            });
            return Patch.Result.APPLY;
        }

        if (annotation.matchesDesc(MixinConstants.MODIFY_ARGS)) {
            ModifyArgsOffsetTransformer.modify(methodNode, List.of(Pair.of(this.index, this.type)));
            return Patch.Result.APPLY;
        }

        LocalVariableNode self = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();

        int lvtIndex = ParameterTransformer.calculateLVTIndex(parameters, isNonStatic, index);

        withLVTSnapshot(methodNode, () -> {
            ParameterNode newParameter = new ParameterNode("adapter_injected_" + index, Opcodes.ACC_SYNTHETIC);
            parameters.add(index, type);
            methodNode.parameters.add(index, newParameter);

            ModifyMethodParams.offsetParameters(methodNode, index);

            methodNode.localVariables.add(index + (isNonStatic ? 1 : 0), new LocalVariableNode(newParameter.name, type.getDescriptor(), null, self.start, self.end, lvtIndex));
        });

        extractWrapOperation(methodContext, methodNode, parameters, wrapOpModification -> wrapOpModification
                .insertParameter(index, nodes -> nodes.add(new VarInsnNode(Opcodes.ALOAD, lvtIndex))));

        return Patch.Result.APPLY;
    }

    @Override
    public Codec<? extends ParameterTransformer> codec() {
        return CODEC;
    }
}
