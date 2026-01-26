package org.sinytra.adapter.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.SingleValueHandle;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Consumer;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public record SwapParametersTransformer(int from, int to) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        int from = offset + this.from;
        int to = offset + this.to;
        boolean nonStatic = !context.isStatic();
        ParameterNode fromNode = methodNode.parameters.get(from);
        ParameterNode toNode = methodNode.parameters.get(to);

        int fromOldLVT = ParamTransformationUtil.calculateLVTIndex(parameters, nonStatic, from);
        int toOldLVT = ParamTransformationUtil.calculateLVTIndex(parameters, nonStatic, to);

        Type fromType = parameters.get(from);
        Type toType = parameters.get(to);
        parameters.set(from, toType);
        parameters.set(to, fromType);

        methodNode.parameters.set(from, toNode);
        methodNode.parameters.set(to, fromNode);

        LOGGER.info(MIXINPATCH, "Swapped parameters at positions {}({}) and {}({}) in {}.{}", from, fromNode.name, to, toNode.name, classNode.name, methodNode.name);

        int fromNewLVT = ParamTransformationUtil.calculateLVTIndex(parameters, nonStatic, from);
        int toNewLVT = ParamTransformationUtil.calculateLVTIndex(parameters, nonStatic, to);

        // Account for "big" LVT variables (like longs and doubles)
        // Uses of the old parameter need to be the new parameter and vice versa
        swapLVT(methodNode, fromOldLVT, toNewLVT)
            .andThen(swapLVT(methodNode, toOldLVT, fromNewLVT))
            .accept(null);

        return PatchResult.COMPUTE_FRAMES;
    }

    public static Consumer<Void> swapLVT(MethodNode methodNode, int from, int to) {
        Consumer<Void> r = v -> {
        };
        for (LocalVariableNode lvn : methodNode.localVariables) {
            if (lvn.index == from) {
                r = r.andThen(v -> lvn.index = to);
            }
        }

        for (AbstractInsnNode insn : methodNode.instructions) {
            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
            if (handle != null) {
                if (handle.get() == from) {
                    LOGGER.info("Swapping in LVT: {} to {}", from, to);
                    r = r.andThen(v -> handle.set(to));
                }
            }
        }

        return r;
    }
}
