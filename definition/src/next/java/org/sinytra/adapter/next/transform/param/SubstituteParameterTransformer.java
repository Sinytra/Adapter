package org.sinytra.adapter.next.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.patch.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.next.env.ctx.PatchResult;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.next.transform.param.ParamTransformationUtil.calculateLVTIndex;

public record SubstituteParameterTransformer(int target, int substitute) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        int paramIndex = this.target + offset;
        int substituteParamIndex = this.substitute + offset;
        boolean isNonStatic = !context.isStatic();
        int localIndex = calculateLVTIndex(parameters, isNonStatic, paramIndex);

        if (methodNode.parameters.size() <= paramIndex) {
            return PatchResult.PASS;
        }

        LVTSnapshot.with(methodNode, () -> {
            LOGGER.info("Substituting parameter {} for {} in {}.{}", paramIndex, substituteParamIndex, classNode.name, methodNode.name);
            parameters.remove(paramIndex);
            methodNode.parameters.remove(paramIndex);
            methodNode.localVariables.removeIf(lvn -> lvn.index == localIndex);

            int substituteIndex = calculateLVTIndex(parameters, isNonStatic, substituteParamIndex);
            AdapterUtil.replaceLVT(methodNode, idx -> idx == localIndex ? substituteIndex : idx);
        });

        return PatchResult.COMPUTE_FRAMES;
    }
}
