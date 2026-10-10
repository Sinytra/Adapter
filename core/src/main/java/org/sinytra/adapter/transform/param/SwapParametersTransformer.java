package org.sinytra.adapter.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.IntStream;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public record SwapParametersTransformer(int from, int to) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        int from = offset + this.from;
        int to = offset + this.to;
        if (from >= parameters.size() || to >= parameters.size()) {
            return PatchResult.PASS;
        }

        int[] order = IntStream.range(0, parameters.size()).toArray();
        order[from] = to;
        order[to] = from;
        ParamTransformationUtil.reorderParameters(methodNode, parameters, !context.isStatic(), order);

        LOGGER.info(MIXINPATCH, "Swapped parameters at positions {} and {} in {}.{}", from, to, classNode.name, methodNode.name);
        return PatchResult.COMPUTE_FRAMES;
    }
}
