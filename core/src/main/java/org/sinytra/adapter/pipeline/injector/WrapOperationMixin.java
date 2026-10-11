package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.sinytra.adapter.analysis.method.MethodHelper;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.ParametersProcessor;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.processor.wrapop.WrapOpParamsProcessor;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;

import java.util.List;

import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class WrapOperationMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .requireOne(MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT)
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT)
        .build();

    public WrapOperationMixin() {
        super(TEMPLATE, MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.getOrThrow(InjectionPointResolver.class)
            .addSubResolverFirst(new ComparingInjectionPointResolver.WrapOperation())
            .addSubResolver(new AtVariableAssignStoreSubResolver());
        processors
            .addAfter(ParametersProcessor.class, new WrapOpParamsProcessor());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(METHOD_PARAMS, OPERATION, CAPTURED_PARAMS, LOCALS)));
        
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;
        if (dirty.getAtData() == null) return TxResult.PASS;

        MethodQualifier dirtyAtTarget = dirty.getAtData().getTargetQualifier().orElse(null);
        if (dirtyAtTarget == null || dirtyAtTarget.desc() == null)
            return TxResult.FAIL;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        TargetPair targetMethodCall = dirtyAtTarget.isFull() ? context.methods().findInheritedMethodPair(context.dirtyLookup(), dirtyAtTarget) : null;
        Type receiver = targetMethodCall != null && !MethodHelper.isStatic(targetMethodCall.methodNode()) ? Type.getObjectType(dirtyAtTarget.internalOwnerName()) : null;
        List<Type> callTypes = Parameters.getCallOperandTypes(receiver, dirtyAtTarget.desc());

        MethodParameters params = clean.getParameters().mutableCopy()
            .putTypes(METHOD_PARAMS, callTypes)
            .putType(OPERATION, TypeConstants.OPERATION_TYPE)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(dirtyAtTarget.desc()));
        
        return TxResult.SUCCESS;
    }
}
