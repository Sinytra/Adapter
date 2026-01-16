package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Configuration.Keys;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.config.PropertyKey;
import org.sinytra.adapter.next.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;

import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class InjectMixin implements MixinType<MixinData> {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public Set<PropertyKey<?>> requestProperties() {
        return Set.of(Keys.SLICES, Keys.CANCELLABLE);
    }

    @Override
    public TxResult preProcess(MixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        recipe.resolvers().getOrThrow(InjectionPointResolver.class)
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
    public TxResult postProcess(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        dirty.setReturnType(Type.VOID_TYPE);
        if (dirty.getTargetMethod() == null) {
            return TxResult.SUCCESS;
        }

        if (dirty.getTargetMethod().desc().equals(clean.getTargetMethod().desc())) {
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

        return TxResult.SUCCESS;
    }
}
