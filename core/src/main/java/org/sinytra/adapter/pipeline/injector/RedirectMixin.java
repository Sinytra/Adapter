package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.sinytra.adapter.analysis.method.MethodHelper;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotationConstants;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.ParametersProcessor;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.processor.redirect.DivertRedirectProcessor;
import org.sinytra.adapter.pipeline.processor.redirect.ParameterUsageProcessor;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.special.ResolverSyntheticInstanceof;

import java.util.List;

import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class RedirectMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    public RedirectMixin() {
        super(TEMPLATE, MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
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

        MethodQualifier targetDesc = clean.getAtData().getTargetQualifier().orElse(null);
        if (targetDesc == null || targetDesc.desc() == null)
            return TxResult.FAIL;

        List<Type> methodParams = Parameters.getParameterTypes(context.methodNode().desc);
        boolean isStatic = isStaticRedirect(context, targetDesc, methodParams, Parameters.getParameterTypes(targetDesc.desc()));
        if (!isStatic && methodParams.isEmpty())
            return TxResult.FAIL;
        List<Type> callTypes = Parameters.getCallOperandTypes(isStatic ? null : methodParams.getFirst(), targetDesc.desc());

        MethodParameters capturedMethodParams = MethodParameters.create(
            Parameters.parse(context.methodNode(), callTypes.size(), -1),
            List.of(ParamGroup.CAPTURED_PARAMS, ParamGroup.LOCALS)
        );

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, capturedMethodParams.getTypes(CAPTURED_PARAMS))
            .putTypes(LOCALS, capturedMethodParams.getTypes(LOCALS))
            .putRemaining(capturedMethodParams)
            .build();

        clean.setParameters(params);

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null)
            return TxResult.FAIL;

        MethodQualifier targetDesc = dirty.getAtData().getTargetQualifier().orElse(null);
        if (targetDesc == null || targetDesc.desc() == null)
            return TxResult.FAIL;

        TargetPair dirtyTarget = context.dirtyMethods().resolve(targetDesc);
        if (dirtyTarget == null)
            return TxResult.FAIL;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        Type receiver = MethodHelper.isStatic(dirtyTarget.methodNode()) ? null : Type.getObjectType(dirtyTarget.classNode().name);
        List<Type> callTypes = Parameters.getCallOperandTypes(receiver, targetDesc.desc());

        dirty.inheritProperyIfAbsent(ControlKeys.PARAMETERS);
        MethodParameters params = (dirty.getParameters() != null ? dirty.getParameters().mutableCopy() : MethodParameters.builder())
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(targetDesc.desc()));

        return TxResult.SUCCESS;
    }

    private boolean isStaticRedirect(MixinContext context, MethodQualifier targetDesc, List<Type> methodParams, List<Type> callTypes) {
        TargetPair cleanTarget = context.cleanMethods().resolve(targetDesc);
        if (cleanTarget != null) {
            return MethodHelper.isStatic(cleanTarget.methodNode());
        }
        return methodParams.size() >= callTypes.size() && methodParams.subList(0, callTypes.size()).equals(callTypes);
    }
}
