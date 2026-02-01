package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotationConstants;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.ConfigurationTemplates;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.ParametersProcessor;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.processor.redirect.DivertRedirectProcessor;
import org.sinytra.adapter.patch.processor.redirect.ParameterUsageProcessor;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.METHOD_PARAMS;

public class RedirectMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public Set<MixinFlag> getFlags() {
        return EnumSet.of(MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.
            addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(false));
        processors
            .addAfter(ParametersProcessor.class, new DivertRedirectProcessor())
            .addAfter(ParametersProcessor.class, new ParameterUsageProcessor());

        if (clean.getAtData() == null || !MixinAnnotationConstants.AT_VAL_INVOKE.equals(clean.getAtData().getValue()))
            return TxResult.FAIL;

        MethodQualifier targetDesc = clean.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);
        if (targetDesc == null)
            return TxResult.FAIL;

        List<Type> methodParams = Parameters.getParameterTypes(context.methodNode().desc);
        List<Type> callTypes = Parameters.getParameterTypes(targetDesc.desc());
        boolean isStatic = methodParams.size() >= callTypes.size() && methodParams.subList(0, callTypes.size()).equals(callTypes);
        if (!isStatic) {
            callTypes.addFirst(methodParams.getFirst());
        }

        List<Type> methodTypes = Parameters.getParameterTypes(context.methodNode().desc);
        List<Type> capturedMethodParams = new ArrayList<>(methodTypes.subList(callTypes.size(), methodTypes.size()));

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, capturedMethodParams)
            .build();

        clean.setParameters(params);

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null)
            return TxResult.FAIL;

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);
        if (targetDesc == null)
            return TxResult.FAIL;

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), targetDesc);
        if (dirtyTarget == null)
            return TxResult.FAIL;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        List<Type> callTypes = Parameters.getParameterTypes(targetDesc.desc());
        if (!MethodHelper.isStatic(dirtyTarget.methodNode())) {
            Type owner = Type.getObjectType(dirtyTarget.classNode().name);
            callTypes.addFirst(owner);
        }

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(targetDesc.desc()));

        return TxResult.SUCCESS;
    }
}
