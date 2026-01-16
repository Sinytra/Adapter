package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.ParametersProcessor;
import org.sinytra.adapter.next.pipeline.processor.wrapop.WrapOpParamsProcessor;
import org.sinytra.adapter.next.pipeline.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.ComparingInjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class WrapOperationMixin implements MixinType<MixinData> {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .requireOne(Configuration.Keys.TARGET_AT, Configuration.Keys.TARGET_CONSTANT)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public TxResult preProcess(MixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        recipe.resolvers().getOrThrow(InjectionPointResolver.class)
            .addSubResolverFirst(new ComparingInjectionPointResolver.WrapOperation())
            .addSubResolver(new AtVariableAssignStoreSubResolver());
        recipe.processors()
            .addAfter(ParametersProcessor.class, new WrapOpParamsProcessor());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(METHOD_PARAMS, OPERATION, CAPTURED_PARAMS, LOCALS)));
        
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return TxResult.FAIL;
        if (dirty.getAtData() == null) return TxResult.PASS;

        MethodQualifier dirtyAtTarget = dirty.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
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
            .putType(OPERATION, MixinConstants.OPERATION_TYPE)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .put(LOCALS, clean.getParameters().get(LOCALS))
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(dirtyAtTarget.desc()));
        
        return TxResult.SUCCESS;
    }
}
