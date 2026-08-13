package org.sinytra.adapter.patch.processor;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParametersProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        MethodParameters cleanParams = recipe.clean().getParameters();
        MethodParameters dirtyParams = dirty.getParameters();
        if (dirtyParams == null) return TxResult.FAIL;
        if (cleanParams.merge().equals(dirtyParams.merge()))
            return TxResult.PASS;

        // Apply mappings of params that will be removed in dirty
        Map<VarInsnNode, Pair<Integer, Type>> oldVarMap = Parameters.gatherVarMappings(context.methodNode(), cleanParams.merge(), dirtyParams.merge(), dirtyParams.getMapping());

        if (cleanParams.getOrder().equals(dirtyParams.getOrder())) {
            List<MethodParameters.ParamGroup> order = dirtyParams.getOrder();
            int offset = 0;
            for (MethodParameters.ParamGroup group : order) {
                List<Type> cleanList = cleanParams.getTypes(group);
                List<Type> dirtyList = dirtyParams.getTypes(group);

                if (!applyDiff(cleanList, dirtyList, context, offset)) {
                    return TxResult.FAIL;
                }
                offset += dirtyList.size();
            }
        } else {
            if (!applyDiff(cleanParams.mergeTypes(), dirtyParams.mergeTypes(), context, 0)) {
                return TxResult.FAIL;
            }
        }

        // Apply mappings of params that only exist in dirty
        Parameters.applyVarMappings(context.methodNode(), Parameters.gatherVarMappings(context.methodNode(), dirtyParams.merge(), dirtyParams.merge(), dirtyParams.getMapping()));
        Parameters.applyVarMappings(context.methodNode(), oldVarMap);
        Parameters.applyAnnotations(context.methodNode(), dirtyParams.merge());

        return TxResult.SUCCESS;
    }

    private boolean applyDiff(List<Type> clean, List<Type> dirty, MixinContext context, int offset) {
        ParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(clean, dirty);
        if (!diff.isEmpty()) {
            PatchResult result = diff.offset(offset).asParameterTransformer(false, Set.of())
                .apply(context);
            return result != PatchResult.PASS;
        }
        return true;
    }
}
