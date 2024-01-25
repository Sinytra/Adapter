package dev.su5ed.sinytra.adapter.patch.transformer.param;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.slf4j.Logger;

import java.util.List;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record SwapParametersTransformer(int from, int to) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final Codec<SwapParametersTransformer> CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.intRange(0, 255).fieldOf("from").forGetter(SwapParametersTransformer::from),
            Codec.intRange(0, 255).fieldOf("to").forGetter(SwapParametersTransformer::to)
    ).apply(in, SwapParametersTransformer::new));

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        int from = offset + this.from;
        int to = offset + this.to;
        boolean nonStatic = !methodContext.isStatic(methodNode);
        ParameterNode fromNode = methodNode.parameters.get(from);
        ParameterNode toNode = methodNode.parameters.get(to);

        int fromOldLVT = ParameterTransformer.calculateLVTIndex(parameters, nonStatic, from);
        int toOldLVT = ParameterTransformer.calculateLVTIndex(parameters, nonStatic, to);

        Type fromType = parameters.get(from);
        Type toType = parameters.get(to);
        parameters.set(from, toType);
        parameters.set(to, fromType);

        methodNode.parameters.set(from, toNode);
        methodNode.parameters.set(to, fromNode);

        LOGGER.info(MIXINPATCH, "Swapped parameters at positions {}({}) and {}({}) in {}.{}", from, fromNode.name, to, toNode.name, classNode.name, methodNode.name);

        int fromNewLVT = ParameterTransformer.calculateLVTIndex(parameters, nonStatic, from);
        int toNewLVT = ParameterTransformer.calculateLVTIndex(parameters, nonStatic, to);

        // Account for "big" LVT variables (like longs and doubles)
        // Uses of the old parameter need to be the new parameter and vice versa
        ModifyMethodParams.swapLVT(methodNode, fromOldLVT, toNewLVT)
                .andThen(ModifyMethodParams.swapLVT(methodNode, toOldLVT, fromNewLVT))
                .accept(null);

        return Patch.Result.COMPUTE_FRAMES;
    }
}
