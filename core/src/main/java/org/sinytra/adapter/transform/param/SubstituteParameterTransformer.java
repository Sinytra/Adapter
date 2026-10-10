package org.sinytra.adapter.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.List;

public record SubstituteParameterTransformer(int target, int substitute) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        int paramIndex = this.target + offset;
        int substituteParamIndex = this.substitute + offset;
        boolean isNonStatic = !context.isStatic();
        if (paramIndex >= parameters.size() || substituteParamIndex >= parameters.size() || paramIndex == substituteParamIndex) {
            return PatchResult.PASS;
        }

        int localIndex = ParamTransformationUtil.getLVTIndex(parameters, isNonStatic, paramIndex);
        int substituteIndex = ParamTransformationUtil.getLVTIndex(parameters, isNonStatic, substituteParamIndex);

        LVTSnapshot.with(methodNode, () -> {
            LOGGER.info("Substituting parameter {} for {} in {}.{}", paramIndex, substituteParamIndex, classNode.name, methodNode.name);
            ParamTransformationUtil.updateParameterAnnotationArrays(methodNode, parameters.size(), annotations -> annotations.remove(paramIndex));
            parameters.remove(paramIndex);
            methodNode.localVariables.removeIf(lvn -> lvn.index == localIndex);

            AdapterUtil.replaceLVT(methodNode, idx -> idx == localIndex ? substituteIndex : idx);
        });

        return PatchResult.COMPUTE_FRAMES;
    }
}
