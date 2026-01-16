package org.sinytra.adapter.next.type;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.Parameter;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.config.PropertyKey;
import org.sinytra.adapter.next.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.SINGLE_ANY;
import static org.sinytra.adapter.next.pipeline.config.Configuration.Keys.INDEX;

public class ModifyArgMixin implements MixinType<MixinData> {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public Set<PropertyKey<?>> requestProperties() {
        return Set.of(Configuration.Keys.INDEX);
    }

    @Override
    public TxResult preProcess(MixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        recipe.resolvers().getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new ArbitraryInjectionPointSubResolver());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY)));

        mixin.getProperty(INDEX)
            .ifPresent(p -> clean.setProperty(INDEX, p));
        
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (clean.getAtData().equals(dirty.getAtData())) {
            dirty.inheritParameters();
            dirty.inheritReturnType();
            return TxResult.SUCCESS;
        }

        if (clean.hasProperty(INDEX) && !dirty.hasProperty(INDEX)) {
            dirty.setProperty(INDEX, clean.getProperty(INDEX).orElseThrow());
        }

        Type type = findArgType(context, recipe.clean().getAtData(), dirty.getAtData(), dirty);
        if (type != null) {
            MethodParameters parameters = MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY));
            parameters.set(SINGLE_ANY, Parameter.simple(type));
            dirty.setParameters(parameters);
            dirty.setReturnType(type);
        }
        
        return TxResult.SUCCESS;
    }

    @Nullable
    private static Type findArgType(MixinContext context, AtData cleanAtData, AtData atData, Configuration dirty) {
        MethodQualifier cleanQualifier = cleanAtData.getTarget().flatMap(MethodQualifier::create).orElse(null);
        MethodQualifier dirtyQualifier = atData.getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (cleanQualifier != null && dirtyQualifier != null) {
            List<Type> cleanArgs = Parameters.getParameterTypes(cleanQualifier.desc());
            List<Type> dirtyArgs = Parameters.getParameterTypes(dirtyQualifier.desc());

            if (dirty.hasProperty(INDEX)) {
                int dirtyIndex = dirty.getProperty(INDEX).orElseThrow();
                return dirtyIndex < dirtyArgs.size() ? dirtyArgs.get(dirtyIndex) : null;
            }

            if (cleanArgs.size() == 1 && dirtyArgs.size() == 1) {
                if (cleanArgs.getFirst().equals(dirtyArgs.getFirst())) {
                    return dirtyArgs.getFirst();
                }

                Type dirtyType = dirtyArgs.getFirst();
                TypeAdapter adapter = context.getTypeAdapter(cleanArgs.getFirst(), dirtyType);
                if (adapter != null) {
                    return dirtyType;
                }
            }
        }
        return null;
    }
}
