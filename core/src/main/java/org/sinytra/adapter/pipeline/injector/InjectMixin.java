package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.config.key.SpecialKeys;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.special.InjectorOrdinalResolver;

import java.util.List;
import java.util.Objects;

import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class InjectMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.SLICES, MixinKeys.CANCELLABLE)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    public InjectMixin() {
        super(TEMPLATE);
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
            if (types.size() != 1) {
                return TxResult.FAIL;
            }
            MethodParameters params = dirty.getParameters();
            List<Parameter> methodParams = params.get(METHOD_PARAMS);
            Type targetType = types.getFirst();
            methodParams.addFirst(Parameter.simple(targetType));
        }

        return TxResult.SUCCESS;
    }

    private boolean sameTarget(Recipe recipe) {
        MethodQualifier cleanQ = recipe.clean().getTargetMethod();
        MethodQualifier dirtyQ = recipe.dirty().getTargetMethod();
        if (cleanQ != null && dirtyQ != null && Objects.equals(cleanQ.name(), dirtyQ.name()) && Objects.equals(cleanQ.desc(), dirtyQ.desc())) {
            return true;
        }

        Targets targets = recipe.targets();
        return targets != null && MethodQualifier.create(targets.clean().methodNode()).matches(MethodQualifier.create(targets.dirty().methodNode()));
    }
}
