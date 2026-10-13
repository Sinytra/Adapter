package org.sinytra.adapter.pipeline.processor.signature;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.params.ParamsEditDistance;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.edit.param.InjectParameterTransform;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.List;

public class RedirectedCallStep implements SignatureStep {
    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        String cleanTarget = recipe.clean().getAtData().getTarget().orElse(null);
        if (cleanTarget == null) return TxResult.PASS;
        String dirtyTarget = dirty.getAtData().getTarget().orElse(null);
        if (dirtyTarget == null) return TxResult.PASS;

        MethodQualifier cleanTargetQual = MethodQualifier.parse(cleanTarget).orElse(null);
        MethodQualifier dirtyTargetQual = MethodQualifier.parse(dirtyTarget).orElse(null);
        if (cleanTargetQual == null || dirtyTargetQual == null) return TxResult.PASS;

        MethodParameters dirtyMethodParams = dirty.getParameters();
        if (dirtyMethodParams == null || !dirtyMethodParams.has(ParamGroup.METHOD_PARAMS)) return TxResult.PASS;

        List<Type> cleanParams = recipe.clean().getParameters().getTypes(ParamGroup.METHOD_PARAMS);
        List<Type> dirtyParams = dirtyMethodParams.getTypes(ParamGroup.METHOD_PARAMS);
        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(cleanParams, dirtyParams, context.typeRelation()).snapshot();

        List<List<AbstractInsnNode>> callInsns = DataFlow.of(context.methodNode(), DataFlow.Producers.COMPUTER).findCallsWithProducers(cleanTargetQual);
        if (callInsns.isEmpty()) {
            return TxResult.SUCCESS;
        }

        LocalVariableLookup lookup = LocalVariableLookup.of(context.methodNode());
        if (lookup == null || diff.insertions().stream().anyMatch(i -> lookup.getByParameterOrdinalOrNull(i.index()) == null)) {
            return TxResult.PASS;
        }

        for (List<AbstractInsnNode> call : callInsns) {
            MethodInsnNode minsn = (MethodInsnNode) call.getLast();

            // Update call target
            if (dirtyTargetQual.owner() != null) {
                minsn.owner = dirtyTargetQual.internalOwnerName();
            }
            minsn.name = dirtyTargetQual.name();
            minsn.desc = dirtyTargetQual.desc();

            // TODO Helper class like for WrapOp
            // Insert new params
            for (InjectParameterTransform insertion : diff.insertions()) {
                int ordinal = insertion.index();
                LocalVariableNode node = lookup.getByParameterOrdinalOrNull(ordinal);

                int position = Math.min(ordinal, call.size() - 1);
                AbstractInsnNode nextInsn = call.get(position);
                AbstractInsnNode insn = AdapterUtil.loadType(insertion.type(), node.index);
                context.methodNode().instructions.insertBefore(nextInsn, insn);
                call.add(position, insn);
            }
        }

        return TxResult.SUCCESS;
    }
}
