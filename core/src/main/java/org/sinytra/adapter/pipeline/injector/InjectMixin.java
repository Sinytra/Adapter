package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.special.InjectorOrdinalResolver;

import java.util.List;
import java.util.Objects;

import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class InjectMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.SLICES, MixinKeys.CANCELLABLE)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.VOID,
        slot(METHOD_PARAMS, TARGET_ARGS),
        slot(CI_CIR, CALLBACK_INFO),
        slot(LOCALS, KEEP)
    );

    public InjectMixin() {
        super(TEMPLATE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers
            .addBefore(InjectionPointResolver.class, new InjectorOrdinalResolver());
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new AtVariableAssignStoreSubResolver())
            .addSubResolver(new ComparingInjectionPointResolver.Inject());

        clean.setParameters(SIGNATURE.parse(context.methodNode()));

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

        if (sameTarget(recipe) || clean.getParameters().getTypes(METHOD_PARAMS).isEmpty()) {
            dirty.inheritParameters();
        } else if (HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, null) == TxResult.FAIL) {
            return TxResult.FAIL;
        }

        // Process static modifier
        if (!clean.getProperty(ControlKeys.STATIC).orElse(false) && dirty.getProperty(ControlKeys.STATIC).orElse(false)) {
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
