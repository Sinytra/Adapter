package org.sinytra.adapter.pipeline.processor.signature;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.analysis.params.ParamsEditDistance;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.edit.param.ParamTransformationUtil;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;
import java.util.Map;

public class ParametersStep implements SignatureStep {
    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        MethodParameters cleanParams = recipe.clean().getParameters();
        MethodParameters dirtyParams = dirty.getParameters();
        if (dirtyParams == null) return TxResult.FAIL;
        if (cleanParams.merge().equals(dirtyParams.merge())) return TxResult.PASS;

        // Apply mappings of params that will be removed in dirty
        MethodNode method = context.methodNode();
        Map<VarInsnNode, Pair<Integer, Type>> oldVarMap = ParamTransformationUtil.gatherVarMappings(method, cleanParams.merge(), dirtyParams.merge(), dirtyParams.getMapping());

        if (cleanParams.getOrder().equals(dirtyParams.getOrder())) {
            int offset = 0;
            for (MethodParameters.ParamGroup group : dirtyParams.getOrder()) {
                if (!applyDiff(cleanParams.getTypes(group), dirtyParams.getTypes(group), context, offset)) {
                    return TxResult.FAIL;
                }
                offset += dirtyParams.getTypes(group).size();
            }
        } else if (!applyDiff(cleanParams.mergeTypes(), dirtyParams.mergeTypes(), context, 0)) {
            return TxResult.FAIL;
        }

        // Apply mappings of params that only exist in dirty
        ParamTransformationUtil.applyVarMappings(method, ParamTransformationUtil.gatherVarMappings(method, dirtyParams.merge(), dirtyParams.merge(), dirtyParams.getMapping()));
        ParamTransformationUtil.applyVarMappings(method, oldVarMap);
        ParamTransformationUtil.applyAnnotations(method, dirtyParams.merge());
        return TxResult.SUCCESS;
    }

    private static boolean applyDiff(List<Type> clean, List<Type> dirty, MixinContext context, int offset) {
        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(clean, dirty, context.typeRelation());
        return diff.isEmpty() || diff.offset(offset).asParameterTransformer(false).apply(context) != PatchResult.PASS;
    }
}
