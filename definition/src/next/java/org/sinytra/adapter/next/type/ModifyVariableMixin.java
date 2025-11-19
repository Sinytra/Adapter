package org.sinytra.adapter.next.type;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.ModifyVariableMixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.ModifyVarInjectionPointSubResolver;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyVariableMixin implements MixinType<ModifyVariableMixinData> {

    @Override
    public ModifyVariableMixinData parse(MixinContext context, ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        boolean argsOnly = handle.<Boolean>getValue("argsOnly").map(AnnotationValueHandle::get).orElse(false);
        Integer ordinal = handle.<Integer>getValue("ordinal").map(AnnotationValueHandle::get).orElse(null);
        return new ModifyVariableMixinData(targetClass, targetMethod, atData, argsOnly, ordinal);
    }

    @Override
    public void preProcess(ModifyVariableMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, LOCALS)));

        recipe.resolvers().getOrThrow(InjectionPointResolver.class).addSubResolver(new ModifyVarInjectionPointSubResolver());
    }

    @Override
    public void postProcess(ModifyVariableMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (mixin.argsOnly() && dirty.getTargetMethod() != null && !dirty.getTargetMethod().desc().equals(clean.getTargetMethod().desc())) {
            Type cleanVarType = clean.getParameters().get(SINGLE_ANY).getFirst();
            List<Type> cleanTargetMethodParams = MethodParameters.getParameterTypes(clean.getTargetMethod().desc());
            int cleanIndex = cleanTargetMethodParams.indexOf(cleanVarType);

            List<Type> dirtyTargetMethodParams = MethodParameters.getParameterTypes(dirty.getTargetMethod().desc());
            Type dirtyVarType = dirtyTargetMethodParams.get(cleanIndex);

            TypeAdapter adapter = context.getTypeAdapter(cleanVarType, dirtyVarType);
            if (adapter != null) {
                MethodParameters newParams = MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, LOCALS));
                newParams.get(SINGLE_ANY).set(0, dirtyVarType);

                dirty.setParameters(newParams);
                dirty.setReturnType(dirtyVarType);
            }
            return;
        }

        dirty.inheritParameters();
        dirty.inheritReturnType();
    }
}
