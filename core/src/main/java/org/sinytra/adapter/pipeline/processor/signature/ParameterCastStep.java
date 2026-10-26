package org.sinytra.adapter.pipeline.processor.signature;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.params.ParamsEditDistance;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.edit.param.ReplaceParametersTransformer;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * If a mixin has its parameter changed to a higher class e.g. ServerPlayer -> LivingEntity but
 * uses it to call a method that only accepts a ServerPlayer, we have to cast it back.
 * <p>
 * Before:
 * <pre>{@code
 *     private void modify(ServerLevel world, ServerPlayer player, CallbackInfo ci) {
 *         trigger(player, stack, getDamageValue());
 *     }
 * }</pre>
 * <p>
 * After:
 * <pre>{@code
 *     private void modify(ServerLevel world, LivingEntity player, CallbackInfo ci) {
 *         trigger((ServerPlayer)player, stack, getDamageValue());
 * //              ^^^^^^^^^^^^^^ Added cast after param change 
 *     }
 * }</pre>
 */
// TODO Add type safety check
public class ParameterCastStep implements SignatureStep {
    private record Cast(VarInsnNode load, Type type) {
    }

    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        MethodParameters cleanParams = recipe.clean().getParameters();
        MethodParameters dirtyParams = dirty.getParameters();
        MethodNode methodNode = context.methodNode();
        LocalVariableLookup lookup = LocalVariableLookup.of(methodNode);
        if (dirtyParams == null || lookup == null || !cleanParams.has(ParamGroup.METHOD_PARAMS) || !dirtyParams.has(ParamGroup.METHOD_PARAMS)) {
            return TxResult.PASS;
        }

        Map<Integer, Type> replaced = new HashMap<>();
        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(cleanParams.getTypes(ParamGroup.METHOD_PARAMS), dirtyParams.getTypes(ParamGroup.METHOD_PARAMS),
            context.typeRelation());
        for (ReplaceParametersTransformer replacement : diff.replacements()) {
            LocalVariableNode node = lookup.getByParameterOrdinalOrNull(replacement.index());
            if (node != null) {
                replaced.put(node.index, Type.getType(node.desc));
            }
        }

        List<Cast> casts = new ArrayList<>();
        DataFlow flow = DataFlow.of(methodNode);
        for (AbstractInsnNode insn : methodNode.instructions) {
            List<AbstractInsnNode> operands = insn instanceof MethodInsnNode call ? flow.resolveCallOperandProducers(call) : null;
            if (operands == null) continue;

            MethodInsnNode call = (MethodInsnNode) insn;
            List<Type> args = Parameters.getParameterTypes(call.desc);
            int instanceOffset = call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
            for (int i = instanceOffset; i < operands.size(); i++) {
                Type expected = args.get(i - instanceOffset);
                if (operands.get(i) instanceof VarInsnNode load && replaced.containsKey(load.var) && !replaced.get(load.var).equals(expected)) {
                    casts.add(new Cast(load, expected));
                }
            }
        }
        casts.forEach(cast -> methodNode.instructions.insert(cast.load(), new TypeInsnNode(Opcodes.CHECKCAST, cast.type().getInternalName())));
        return casts.isEmpty() ? TxResult.PASS : TxResult.SUCCESS;
    }
}
