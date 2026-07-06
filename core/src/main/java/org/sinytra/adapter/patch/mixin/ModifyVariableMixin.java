package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameter;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.ConfigurationTemplates;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.injection.ModifyVarInjectionPointSubResolver;
import org.sinytra.adapter.patch.resolver.special.InjectorOrdinalResolver;
import org.sinytra.adapter.patch.resolver.special.ModifyVarAtReturnResolver;
import org.sinytra.adapter.patch.resolver.special.ModifyVarUpgradeResolver;
import org.sinytra.adapter.types.TypeAdapter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyVariableMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.ARGS_ONLY, MixinKeys.ORDINAL, MixinKeys.INDEX, MixinKeys.SLICE)
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public Set<MixinFlag> getFlags() {
        return EnumSet.of(MixinFlag.TARGETS_VARIABLE);
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
        if (argsOnly && dirty.getTargetMethod() != null && !dirty.getTargetMethod().desc().equals(clean.getTargetMethod().desc())) {
            Type cleanVarType = clean.getParameters().getTypes(SINGLE_ANY).getFirst();
            List<Type> cleanTargetMethodParams = Parameters.getParameterTypes(clean.getTargetMethod().desc());
            int cleanIndex = cleanTargetMethodParams.indexOf(cleanVarType);

            List<Type> dirtyTargetMethodParams = Parameters.getParameterTypes(dirty.getTargetMethod().desc());
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

        dirty.inheritParameters();
        dirty.inheritReturnType();

        return TxResult.SUCCESS;
    }

    @Override
    public boolean canInject(MixinContext context, Configuration config) {
        // Ensure variable at index has the correct type
        if (config.hasProperty(MixinKeys.INDEX)) {
            MethodNode target = context.methods().findOwnMethod(context.dirtyLookup(), config.getTargetMethod());
            if (target == null) return false;

            Integer index = config.getProperty(MixinKeys.INDEX).orElseThrow();
            LocalVariableLookup lookup = new LocalVariableLookup(target);

            LocalVariableNode node = lookup.getByIndexOrNull(index);
            if (node == null) return false;

            Type expectedType = config.getReturnType();
            Type actualType = Type.getType(node.desc);
            return expectedType.equals(actualType);
        }
        return true;
    }
}
