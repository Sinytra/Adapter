package org.sinytra.adapter.next.type;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.ModifyArgMixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyArgMixin implements MixinType<ModifyArgMixinData> {
    @Override
    public ModifyArgMixinData parse(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, AnnotationHandle handle) {
        return new ModifyArgMixinData(targetClass, targetMethod, atData);
    }

    @Override
    public void preProcess(ModifyArgMixinData mixin, MixinContext context, MutableConfiguration clean, Recipe recipe) {
        clean.setParameters(MethodParameters.create(context.methodNode().desc, List.of(SINGLE_ANY)));
        clean.setReturnType(Type.getReturnType(context.methodNode().desc));
    }

    @Override
    public void postProcess(ModifyArgMixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        Type type = findArgType(context, recipe.clean().getAtData(), dirty.getAtData());
        if (type != null) {
            MethodParameters parameters = MethodParameters.create(context.methodNode().desc, List.of(SINGLE_ANY));
            parameters.set(SINGLE_ANY, List.of(type));
            dirty.setParameters(parameters);
            dirty.setReturnType(type);   
        }
    }

    @Nullable
    private static Type findArgType(MixinContext context, AtData cleanAtData, AtData atData) {
        MethodQualifier cleanQualifier = cleanAtData.getTarget().flatMap(MethodQualifier::create).orElse(null);
        MethodQualifier dirtyQualifier = atData.getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (cleanQualifier != null && dirtyQualifier != null) {
            List<Type> cleanArgs = MethodParameters.getParameterTypes(cleanQualifier.desc());
            List<Type> dirtyArgs = MethodParameters.getParameterTypes(dirtyQualifier.desc());
            if (cleanArgs.size() == 1 && dirtyArgs.size() == 1) {
                Type dirtyType = dirtyArgs.getFirst();
                TypeAdapter adapter = context.getTypeAdapter(cleanArgs.getFirst(), dirtyType);
                if (adapter != null) {
                    return dirtyType;
                }
            }
        }
        return null;
    }
}
