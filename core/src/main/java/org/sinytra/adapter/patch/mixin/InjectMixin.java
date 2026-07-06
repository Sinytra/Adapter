package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.config.ConfigurationTemplates;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameter;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.patch.config.*;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.patch.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.patch.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.special.InjectorOrdinalResolver;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;
import java.util.Objects;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.*;

public class InjectMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.SLICES, MixinKeys.CANCELLABLE)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers
            .addBefore(InjectionPointResolver.class, new InjectorOrdinalResolver());
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new AtVariableAssignStoreSubResolver())
            .addSubResolver(new ComparingInjectionPointResolver.Inject())
            .addSubResolver(new ArbitraryInjectionPointSubResolver());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(METHOD_PARAMS, CI_CIR, LOCALS)));

        // TODO Handle slices
//        mixin.getProperty(Configuration.Keys.SLICES)
//            .ifPresent(p -> clean.setProperty(Configuration.Keys.SLICES, p));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        dirty.setReturnType(Type.VOID_TYPE);
        if (dirty.getTargetMethod() == null || dirty.getTargetMethod().desc() == null) {
            return TxResult.SUCCESS;
        }

        if (sameTarget(recipe)) {
            dirty.inheritParameters();
        } else {
            List<Type> cleanParams = clean.getParameters().getTypes(METHOD_PARAMS);
            if (!cleanParams.isEmpty()) {
                MethodParameters newParams = clean.getParameters().copy();
                List<Type> dirtyTargetMethodParams = Parameters.getParameterTypes(dirty.getTargetMethod().desc());
                newParams.setTypes(METHOD_PARAMS, dirtyTargetMethodParams);
                dirty.setParameters(newParams);
            } else {
                dirty.inheritParameters();
            }
        }

        // Process static modifier
        if (!clean.getProperty(SpecialKeys.STATIC).orElse(false) && dirty.getProperty(SpecialKeys.STATIC).orElse(false)) {
            List<Type> types = context.targetTypes();
            if (types.size() == 1) {
                MethodParameters params = dirty.getParameters();
                List<Parameter> methodParams = params.get(METHOD_PARAMS);
                Type targetType = types.getFirst();
                methodParams.addFirst(Parameter.simple(targetType));
            } else {
                throw new IllegalStateException("Cannot automatically determine target instance type for mixin " + context.classNode().name);
            }
        }

        return TxResult.SUCCESS;
    }

    private boolean sameTarget(Recipe recipe) {
        MethodQualifier cleanQ = recipe.clean().getTargetMethod();
        MethodQualifier dirtyQ = recipe.dirty().getTargetMethod();
        if (cleanQ != null && dirtyQ != null && Objects.equals(cleanQ.name(), dirtyQ.name()) && Objects.equals(cleanQ.desc(), dirtyQ.desc())) {
            return true;
        }

        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        return cleanTarget != null && dirtyTarget != null
            && MethodQualifier.create(cleanTarget.methodNode()).matches(MethodQualifier.create(dirtyTarget.methodNode()));
    }
}
