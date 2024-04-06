package org.sinytra.adapter.patch.transformer.param;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.transformer.LVTSnapshot;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.transformer.param.ParamTransformationUtil.calculateLVTIndex;

public record SubstituteParameterTransformer(int target, int substitute) implements ParameterTransformer {
    static final Codec<SubstituteParameterTransformer> CODEC = RecordCodecBuilder.create(in -> in.group(
        Codec.intRange(0, 255).fieldOf("target").forGetter(SubstituteParameterTransformer::target),
        Codec.intRange(0, 255).fieldOf("substitute").forGetter(SubstituteParameterTransformer::substitute)
    ).apply(in, SubstituteParameterTransformer::new));

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.target + offset;
        final int substituteParamIndex = this.substitute + offset;
        final boolean isNonStatic = !methodContext.isStatic();
        final int localIndex = calculateLVTIndex(parameters, isNonStatic, paramIndex);

        if (methodNode.parameters.size() <= paramIndex) {
            return Patch.Result.PASS;
        }

        LVTSnapshot.with(methodNode, () -> {
            LOGGER.info("Substituting parameter {} for {} in {}.{}", paramIndex, substituteParamIndex, classNode.name, methodNode.name);
            parameters.remove(paramIndex);
            methodNode.parameters.remove(paramIndex);
            methodNode.localVariables.removeIf(lvn -> lvn.index == localIndex);

            final int substituteIndex = calculateLVTIndex(parameters, isNonStatic, substituteParamIndex);
            AdapterUtil.replaceLVT(methodNode, idx -> idx == localIndex ? substituteIndex : idx);
        });

        return Patch.Result.COMPUTE_FRAMES;
    }

    @Override
    public Codec<? extends ParameterTransformer> codec() {
        return CODEC;
    }
}
