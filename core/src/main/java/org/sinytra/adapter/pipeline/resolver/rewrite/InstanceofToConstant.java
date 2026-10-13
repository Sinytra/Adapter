package org.sinytra.adapter.pipeline.resolver.rewrite;

import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.ConstantData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.WrapOpSurgeon;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_ORDINAL;

public final class InstanceofToConstant implements RewriteRule {
    @Nullable
    @Override
    public Rewrite apply(MixinContext context, Recipe recipe) {
        TypeInsnNode instanceOfCall = InstanceofReplacement.find(context, recipe);
        if (instanceOfCall == null || !(recipe.getCleanInjectionInsn() instanceof MethodInsnNode cleanCall)) return null;

        MethodNode methodNode = context.methodNode();
        LocalVariableLookup lvt = LocalVariableLookup.of(methodNode);
        if (lvt == null) return null;

        LocalVariableNode instanceLocal = lvt.getByParameterOrdinalOrNull(0);
        if (instanceLocal == null) return null;

        Configuration clean = recipe.clean();
        List<Type> inheritedParams = clean.getParameters().getTypes(ParamGroup.METHOD_PARAMS);
        Multimap<Integer, VarInsnNode> usedVars = WrapOpSurgeon.getUsedVars(lvt, inheritedParams, context);

        MutableConfiguration config = recipe.dirty().copy()
            .removeProperty(MixinKeys.TARGET_AT)
            .setProperty(MixinKeys.TARGET_CONSTANT, ConstantData.classValue(Type.getObjectType(instanceOfCall.desc)))
            .inheritSignature();
        MethodParameters parameters = config.getParameters();
        Parameter newInstanceParam = Parameter.simple(TypeConstants.OBJECT_TYPE);
        parameters.set(ParamGroup.METHOD_PARAMS, List.of(newInstanceParam));

        if (usedVars.containsKey(instanceLocal.index)) {
            TargetPair cleanTarget = recipe.getCleanTarget();
            LocalVariableLookup cleanLocals = recipe.cleanLocalsTable();
            LocalVariableLookup dirtyLocals = recipe.dirtyLocalsTable();
            if (cleanTarget == null || cleanLocals == null || dirtyLocals == null) return null;

            List<AbstractInsnNode> originalCallArgs = context.dataFlow(cleanTarget).resolveCallOperandProducers(cleanCall);
            if (originalCallArgs == null || originalCallArgs.isEmpty() || !(originalCallArgs.getFirst() instanceof VarInsnNode originalInstanceInsn))
                return null;
            TargetPair dirtyTarget = recipe.getDirtyTarget();
            LocalVariableNode originalInstanceLocal = cleanLocals.getInScopeOrNull(originalInstanceInsn.var, cleanCall);
            if (dirtyTarget == null || originalInstanceLocal == null) return null;

            int cleanOrdinal = context.locals().resolveLocalOrdinal(cleanTarget, cleanCall, Type.getType(originalInstanceLocal.desc), originalInstanceInsn.var);
            if (cleanOrdinal == -1) return null;

            int dirtyIndex = context.locals().resolveLocalIndex(dirtyTarget, instanceOfCall, Type.getType(instanceLocal.desc), cleanOrdinal);
            LocalVariableNode dirtyLocal = dirtyLocals.getInScopeOrNull(dirtyIndex, instanceOfCall);
            if (dirtyLocal == null) return null;

            Parameter replacementInstanceParam = Parameter.builder(dirtyLocal.desc)
                .annotate(MixinAnnotations.LOCAL, b -> b
                    .put(PROPERTY_ORDINAL, cleanOrdinal))
                .build();
            parameters.add(ParamGroup.LOCALS, replacementInstanceParam);
            // TODO exclude original.call(...) insns from being mapped
            parameters.mapParameter(newInstanceParam, replacementInstanceParam);
        }

        // TODO Find a way to get rid of this ugly hack
        // Use "state.getBlock()" as the new Block parameter
        if (instanceOfCall.getPrevious() instanceof MethodInsnNode m) {
            String loadedType = Type.getReturnType(m.desc).getDescriptor();
            for (int paramVar : usedVars.keySet()) {
                if (paramVar == instanceLocal.index) {
                    continue;
                }

                LocalVariableNode node = lvt.getByIndexOrNull(paramVar);
                if (node == null || !loadedType.equals(node.desc)) return null;

                int paramOrdinal = lvt.getParameterOrdinal(node);
                List<Parameter> cleanParams = clean.getParameters().get(ParamGroup.METHOD_PARAMS);
                if (paramOrdinal < 0 || paramOrdinal >= cleanParams.size()) return null;

                Parameter oldParam = cleanParams.get(paramOrdinal);
                parameters.mapParameter(oldParam, newInstanceParam);
            }
        }

        return new Rewrite.Replace(config);
    }
}
