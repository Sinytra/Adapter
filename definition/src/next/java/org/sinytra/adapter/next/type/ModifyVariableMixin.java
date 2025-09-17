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
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyVariableMixin implements MixinType<ModifyVariableMixinData> {

    @Override
    public ModifyVariableMixinData parse(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        boolean argsOnly = handle.<Boolean>getValue("argsOnly").map(AnnotationValueHandle::get).orElse(false);
        return new ModifyVariableMixinData(targetClass, targetMethod, atData, argsOnly);
    }

    @Override
    public void preProcess(ModifyVariableMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        clean.setParameters(MethodParameters.create(context.getMethodNode().desc, List.of(SINGLE_ANY, LOCALS)));
        clean.setReturnType(Type.getReturnType(context.getMethodNode().desc));
    }

    @Override
    public void postProcess(ModifyVariableMixinData mixin, MixinContext context, MutableConfiguration dirty, Recipe recipe) {
        Configuration clean = recipe.clean();

        if (mixin.argsOnly() && dirty.getTargetMethod() != null && !dirty.getTargetMethod().desc().equals(clean.getTargetMethod().desc())) {
            Type cleanVarType = clean.getParameters().get(SINGLE_ANY).getFirst();
            List<Type> cleanTargetMethodParams = MethodParameters.getParameterTypes(clean.getTargetMethod().desc());
            int cleanIndex = cleanTargetMethodParams.indexOf(cleanVarType);

            List<Type> dirtyTargetMethodParams = MethodParameters.getParameterTypes(dirty.getTargetMethod().desc());
            Type dirtyVarType = dirtyTargetMethodParams.get(cleanIndex);

            TypeAdapter adapter = context.getMethodContext().patchContext().environment().bytecodeFixerUpper().getTypeAdapter(cleanVarType, dirtyVarType);
            if (adapter != null) {
                MethodParameters newParams = MethodParameters.create(context.getMethodNode().desc, List.of(SINGLE_ANY, LOCALS));
                newParams.get(SINGLE_ANY).set(0, dirtyVarType);

                dirty.setParameters(newParams);
                dirty.setReturnType(dirtyVarType);
            }
        }
    }
}
