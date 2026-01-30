package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.config.*;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.ParametersProcessor;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.processor.wrapop.WrapOpParamsProcessor;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.patch.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.*;

public class WrapOperationMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .requireOne(MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT)
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

        MethodQualifier dirtyAtTarget = dirty.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);
        if (dirtyAtTarget == null)
            return TxResult.FAIL;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        List<Type> callTypes = new ArrayList<>(Parameters.getParameterTypes(dirtyAtTarget.desc()));

        if (dirtyAtTarget.isFull()) {
            MethodNode targetMethodCall = context.methods().findInheritedMethod(context.dirtyLookup(), dirtyAtTarget);
            if (targetMethodCall != null && !MethodHelper.isStatic(targetMethodCall)) {
                callTypes.addFirst(Type.getObjectType(dirtyAtTarget.internalOwnerName()));
            }
        }

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putType(OPERATION, TypeConstants.OPERATION_TYPE)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .put(LOCALS, clean.getParameters().get(LOCALS))
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(dirtyAtTarget.desc()));
        
        return TxResult.SUCCESS;
    }
}
