package org.sinytra.adapter.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public record MoveParametersTransformer(int from, int to) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public MoveParametersTransformer offset(int offset) {
        return new MoveParametersTransformer(this.from + offset, this.to + offset);
    }

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        int from = this.from + offset;
        int to = this.to + offset;
        if (from >= parameters.size() || to >= parameters.size()) {
            LOGGER.info(MIXINPATCH, "Cannot move parameter from index {} to {} in method {}.{}, out of bounds", from, to, classNode.name, methodNode.name);
            return PatchResult.PASS;
        }

        List<Integer> order = IntStream.range(0, parameters.size()).boxed().collect(Collectors.toCollection(ArrayList::new));
        order.add(to, order.remove(from));
        ParamTransformationUtil.reorderParameters(methodNode, parameters, !context.isStatic(), order.stream().mapToInt(Integer::intValue).toArray());

        LOGGER.info(MIXINPATCH, "Moved parameter from index {} to {} in method {}.{}", from, to, classNode.name, methodNode.name);
        return PatchResult.COMPUTE_FRAMES;
    }
}
