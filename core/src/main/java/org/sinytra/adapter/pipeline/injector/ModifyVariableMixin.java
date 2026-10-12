package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.injection.ModifyVarInjectionPointSubResolver;
import org.sinytra.adapter.pipeline.resolver.special.InjectorOrdinalResolver;
import org.sinytra.adapter.pipeline.resolver.special.ModifyVarAtReturnResolver;
import org.sinytra.adapter.pipeline.resolver.special.ModifyVarUpgradeResolver;

import java.util.List;

import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyVariableMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.ARGS_ONLY, MixinKeys.ORDINAL, MixinKeys.INDEX, MixinKeys.SLICE)
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    public ModifyVariableMixin() {
        super(TEMPLATE, MixinFlag.TARGETS_VARIABLE);
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers
            .addFirst(new ModifyVarUpgradeResolver())
            .addBefore(InjectionPointResolver.class, new InjectorOrdinalResolver())
            .addBefore(InjectionPointResolver.class, new ModifyVarAtReturnResolver());
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new ModifyVarInjectionPointSubResolver());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, LOCALS)));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getTargetMethod().desc() == null) {
            return TxResult.FAIL;
        }
        
        dirty.inheritProperyIfAbsent(MixinKeys.SLICE);

        boolean argsOnly = clean.getProperty(MixinKeys.ARGS_ONLY).orElse(false);
        String cleanDesc = clean.getTargetMethod().desc();
        if (argsOnly && cleanDesc != null && !dirty.getTargetMethod().desc().equals(cleanDesc)) {
            Type cleanVarType = clean.getParameters().getTypes(SINGLE_ANY).getFirst();
            List<Type> cleanTargetMethodParams = Parameters.getParameterTypes(cleanDesc);
            int cleanIndex = cleanTargetMethodParams.indexOf(cleanVarType);

            List<Type> dirtyTargetMethodParams = Parameters.getParameterTypes(dirty.getTargetMethod().desc());
            // The modified argument can't be located in the new target method
            if (cleanIndex == -1 || cleanIndex >= dirtyTargetMethodParams.size()) {
                return TxResult.FAIL;
            }
            Type dirtyVarType = dirtyTargetMethodParams.get(cleanIndex);

            TypeAdapter adapter = context.getTypeAdapter(cleanVarType, dirtyVarType);
            if (adapter != null) {
                MethodParameters newParams = MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, LOCALS));
                newParams.get(SINGLE_ANY).set(0, Parameter.simple(dirtyVarType));

                dirty.setParameters(newParams);
                dirty.setReturnType(dirtyVarType);
            }
            return TxResult.SUCCESS;
        }

        dirty.inheritSignature();

        return TxResult.SUCCESS;
    }

    @Override
    public boolean canInject(MixinContext context, Configuration config) {
        // Ensure variable at index has the correct type
        if (config.hasProperty(MixinKeys.INDEX)) {
            TargetPair targetPair = context.dirtyMethods().resolve(config.getTargetMethod());
            if (targetPair == null) return false;
            MethodNode target = targetPair.methodNode();

            Integer index = config.getProperty(MixinKeys.INDEX).orElseThrow();
            LocalVariableLookup lookup = LocalVariableLookup.of(target);
            if (lookup == null) return false;

            LocalVariableNode node = lookup.getByIndexOrNull(index);
            if (node == null) return false;

            Type expectedType = config.getReturnType();
            Type actualType = Type.getType(node.desc);
            return expectedType.equals(actualType);
        }
        return true;
    }
}
