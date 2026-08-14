package org.sinytra.adapter.patch.processor;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;

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
public class ParametersPostProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        MethodParameters cleanParams = recipe.clean().getParameters();
        MethodParameters dirtyParams = recipe.clean().getParameters();
        if (!cleanParams.has(MethodParameters.ParamGroup.METHOD_PARAMS) || !dirtyParams.has(MethodParameters.ParamGroup.METHOD_PARAMS)) {
            return TxResult.PASS;
        }

        List<Type> cleanTypes = recipe.clean().getParameters().getTypes(MethodParameters.ParamGroup.METHOD_PARAMS);
        List<Type> dirtyTypes = recipe.dirty().getParameters().getTypes(MethodParameters.ParamGroup.METHOD_PARAMS);
        ParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(cleanTypes, dirtyTypes);
        MethodNode methodNode = context.methodNode();
        LocalVariableLookup lookup = context.methods().getLVT(context.methodNode());
        if (lookup == null) return TxResult.PASS;

        boolean matched = false;
        for (Pair<Integer, Type> replacement : diff.replacements()) {
            int ordinal = replacement.getFirst();
            LocalVariableNode node = lookup.getByParameterOrdinalOrNull(ordinal);
            if (node == null) continue;

            Type currentType = Type.getType(node.desc);

            for (AbstractInsnNode insn : methodNode.instructions) {
                if (!(insn instanceof MethodInsnNode minsn)) continue;

                // Add casts to usage in method calls
                List<Type> methodArgs = Parameters.getParameterTypes(minsn.desc);
                List<AbstractInsnNode> callArgs = MethodCallAnalyzer.getMethodCallSrcInsns(methodNode, minsn);
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
