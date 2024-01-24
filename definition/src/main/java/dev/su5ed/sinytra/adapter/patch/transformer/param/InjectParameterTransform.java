package dev.su5ed.sinytra.adapter.patch.transformer.param;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyArgsOffsetTransformer;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

import static dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams.offsetParameters;

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

            offsetParameters(methodNode, index);

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
