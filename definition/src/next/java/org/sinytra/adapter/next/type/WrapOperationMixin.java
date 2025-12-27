package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ConfigurationTemplates;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.WrapOperationMixinData;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.resolver.injection.AtVariableAssignStoreSubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.*;

public class WrapOperationMixin implements MixinType<WrapOperationMixinData> {
    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return ConfigurationTemplates.MIXIN_AT;
    }

    @Override
    public WrapOperationMixinData parse(MixinContext context, ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        return new WrapOperationMixinData(targetClass, targetMethod, atData);
    }

    @Override
    public void preProcess(WrapOperationMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        recipe.resolvers().getOrThrow(InjectionPointResolver.class)
            .addSubResolver(new AtVariableAssignStoreSubResolver());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(METHOD_PARAMS, OPERATION, CAPTURED_PARAMS, LOCALS)));
    }

    @Override
    public void postProcess(WrapOperationMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null) return;

        MethodQualifier dirtyAtTarget = dirty.getAtData().getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (dirtyAtTarget == null) return;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        List<Type> callTypes = new ArrayList<>(MethodParameters.getParameterTypes(dirtyAtTarget.desc()));

        if (dirtyAtTarget.isFull()) {
            MethodNode targetMethodCall = context.methods().findMethod(context.dirtyLookup(), dirtyAtTarget);
            if (targetMethodCall != null && !MethodHelper.isStatic(targetMethodCall)) {
                callTypes.addFirst(Type.getObjectType(dirtyAtTarget.internalOwnerName()));
            }
        }

        MethodParameters params = MethodParameters.builder()
            .put(METHOD_PARAMS, callTypes)
            .put(OPERATION, AdapterUtil.OPERATION_TYPE)
            .put(CAPTURED_PARAMS, dirtyCaptured)
            .put(LOCALS, clean.getParameters().get(LOCALS))
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(dirtyAtTarget.desc()));
    }
}
