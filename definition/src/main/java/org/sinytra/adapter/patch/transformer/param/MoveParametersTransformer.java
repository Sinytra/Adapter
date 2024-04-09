package org.sinytra.adapter.patch.transformer.param;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.transformer.LVTSnapshot;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record MoveParametersTransformer(int from, int to) implements ParameterTransformer {
    static final Codec<MoveParametersTransformer> CODEC = RecordCodecBuilder.create(in -> in.group(
        Codec.intRange(0, 255).fieldOf("from").forGetter(MoveParametersTransformer::from),
        Codec.intRange(0, 255).fieldOf("to").forGetter(MoveParametersTransformer::to)
    ).apply(in, MoveParametersTransformer::new));

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.from + offset;

        LOGGER.info(MIXINPATCH, "Moving parameter from index {} to {} in method {}.{}", this.from, this.to, classNode.name, methodNode.name);

        LocalVariableLookup lookup = new LocalVariableLookup(methodNode);
        LocalVariableNode localVar = lookup.getByParameterOrdinal(paramIndex);

        if (paramIndex < methodNode.parameters.size()) {
            ParameterNode parameter = methodNode.parameters.remove(paramIndex);
            methodNode.parameters.add(this.to > paramIndex ? this.to - 1 : this.to, parameter);
        }

        int tempIndex = -999;
        AdapterUtil.replaceLVT(methodNode, idx -> idx == localVar.index ? tempIndex : idx);

        LVTSnapshot.with(methodNode, () -> methodNode.localVariables.remove(localVar));
        parameters.remove(paramIndex);

        Type type = Type.getType(localVar.desc);
        int destination = this.to;
        localVar.index = lookup.getByParameterOrdinal(destination).index + offset;

        LVTSnapshot.with(methodNode, () -> methodNode.localVariables.add(localVar.index, localVar));
        AdapterUtil.replaceLVT(methodNode, idx -> idx == tempIndex ? localVar.index : idx);

        parameters.add(this.to > paramIndex ? this.to - 1 : this.to, type);

        return Patch.Result.COMPUTE_FRAMES;
    }

    @Override
    public Codec<? extends ParameterTransformer> codec() {
        return CODEC;
    }
}
