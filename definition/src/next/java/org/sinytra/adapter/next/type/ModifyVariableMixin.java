package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.Parameter;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Keys;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.ModifyVarInjectionPointSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.InjectorOrdinalResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.ModifyVarAtReturnResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.ModifyVarUpgradeResolver;
import org.sinytra.adapter.patch.fixes.TypeAdapter;

import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyVariableMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(Keys.ARGS_ONLY, Keys.ORDINAL, Keys.INDEX, Keys.SLICE)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
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
        dirty.inheritProperyIfAbsent(Keys.SLICE);

        boolean argsOnly = clean.getProperty(Keys.ARGS_ONLY).orElse(false);
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
}
