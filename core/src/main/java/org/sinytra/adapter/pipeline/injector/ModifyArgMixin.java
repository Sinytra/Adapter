package org.sinytra.adapter.pipeline.injector;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.ArbitraryInjectionPointSubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;

import java.util.List;

import static org.sinytra.adapter.model.config.key.MixinKeys.INDEX;
import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyArgMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.INDEX)
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.CALL_ARG,
        slot(SINGLE_ANY, VALUE)
    );

    public ModifyArgMixin() {
        super(TEMPLATE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new ArbitraryInjectionPointSubResolver());

        clean.setParameters(SIGNATURE.parse(context.methodNode()));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        dirty.inheritProperyIfAbsent(MixinKeys.ORDINAL);
        dirty.inheritProperyIfAbsent(MixinKeys.INDEX);

        if (dirty.getAtData() == null) {
            return TxResult.FAIL;
        }
        if (clean.getAtData().equals(dirty.getAtData())) {
            dirty.inheritSignature();
            return TxResult.SUCCESS;
        }

        Type type = findArgType(context, recipe.clean().getAtData(), dirty.getAtData(), clean, dirty);
        if (type != null) {
            return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, type);
        }

        return TxResult.SUCCESS;
    }

    @Nullable
    private static Type findArgType(MixinContext context, AtData cleanAtData, AtData atData, Configuration clean, Configuration dirty) {
        MethodQualifier cleanQualifier = cleanAtData.getTargetQualifier().orElse(null);
        MethodQualifier dirtyQualifier = atData.getTargetQualifier().orElse(null);
        if (cleanQualifier != null && dirtyQualifier != null && cleanQualifier.desc() != null && dirtyQualifier.desc() != null) {
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

            if (clean.getParameters() != null) {
                List<Parameter> list = clean.getParameters().get(SINGLE_ANY);
                if (!list.isEmpty()) {
                    Type type = list.getFirst().type();
                    if (dirtyArgs.contains(type)) {
                        return type;
                    }
                }
            }
        }
        return null;
    }
}
