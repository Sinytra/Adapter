package org.sinytra.adapter.pipeline.processor.signature;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.edit.param.ReplaceParametersTransformer;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

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
    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        MethodParameters cleanParams = recipe.clean().getParameters();
        MethodParameters dirtyParams = dirty.getParameters();
        if (dirtyParams == null || !cleanParams.has(MethodParameters.ParamGroup.METHOD_PARAMS) || !dirtyParams.has(MethodParameters.ParamGroup.METHOD_PARAMS)) {
            return TxResult.PASS;
        }

        List<Type> cleanTypes = cleanParams.getTypes(MethodParameters.ParamGroup.METHOD_PARAMS);
        List<Type> dirtyTypes = dirtyParams.getTypes(MethodParameters.ParamGroup.METHOD_PARAMS);
        LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(cleanTypes, dirtyTypes);
        if (diff == null) return TxResult.PASS;

        MethodNode methodNode = context.methodNode();
        LocalVariableLookup lookup = LocalVariableLookup.of(context.methodNode());
        if (lookup == null) return TxResult.PASS;

        boolean matched = false;
        for (ReplaceParametersTransformer replacement : diff.replacements()) {
            int ordinal = replacement.index();
            LocalVariableNode node = lookup.getByParameterOrdinalOrNull(ordinal);
            if (node == null) continue;

            Type currentType = Type.getType(node.desc);

            for (AbstractInsnNode insn : methodNode.instructions) {
                if (!(insn instanceof MethodInsnNode minsn)) continue;

                // Add casts to usage in method calls
                List<Type> methodArgs = Parameters.getParameterTypes(minsn.desc);
                List<AbstractInsnNode> callArgs = DataFlow.of(methodNode).resolveCallOperandProducers(minsn);
                if (callArgs == null) continue;

                int instanceOffset = minsn.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
                for (int i = instanceOffset; i < callArgs.size(); i++) {
                    AbstractInsnNode arg = callArgs.get(i);
                    if (arg instanceof VarInsnNode varInsn && varInsn.var == node.index) {
                        Type type = methodArgs.get(i - instanceOffset);
                        if (!currentType.equals(type)) {
                            methodNode.instructions.insert(varInsn, new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
                            matched = true;
                        }
                    }
                }
            }
        }

        return matched ? TxResult.SUCCESS : TxResult.PASS;
    }
}
