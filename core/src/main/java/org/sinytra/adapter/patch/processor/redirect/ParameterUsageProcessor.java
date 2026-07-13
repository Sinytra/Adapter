package org.sinytra.adapter.patch.processor.redirect;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;

public class ParameterUsageProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        String cleanTarget = recipe.clean().getAtData().getTarget().orElse(null);
        if (cleanTarget == null) return TxResult.PASS;
        String dirtyTarget = dirty.getAtData().getTarget().orElse(null);
        if (dirtyTarget == null) return TxResult.PASS;

        MethodQualifier cleanTargetQual = MethodQualifier.parse(cleanTarget).orElseThrow();
        MethodQualifier dirtyTargetQual = MethodQualifier.parse(dirtyTarget).orElseThrow();

        List<Type> cleanParams = recipe.clean().getParameters().getTypes(ParamGroup.METHOD_PARAMS);
        List<Type> dirtyParams = recipe.dirty().getParameters().getTypes(ParamGroup.METHOD_PARAMS);
        ParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(cleanParams, dirtyParams);

        List<List<AbstractInsnNode>> callInsns = MethodCallAnalyzer.getAllMethodCallSrcInsnsInclusive(context.methodNode(), cleanTargetQual);
        if (callInsns.isEmpty()) {
            return TxResult.SUCCESS;
        }

        LocalVariableLookup lookup = context.methods().getLVT(context.methodNode());
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
            for (Pair<Integer, Type> insertion : diff.insertions()) {
                int ordinal = insertion.getFirst();
                LocalVariableNode node = lookup.getByParameterOrdinal(ordinal);

                AbstractInsnNode nextInsn = call.get(ordinal);
                AbstractInsnNode insn = AdapterUtil.loadType(insertion.getSecond(), node.index);
                context.methodNode().instructions.insertBefore(nextInsn, insn);
                call.add(ordinal, insn);
            }
        }

        return TxResult.SUCCESS;
    }
}
